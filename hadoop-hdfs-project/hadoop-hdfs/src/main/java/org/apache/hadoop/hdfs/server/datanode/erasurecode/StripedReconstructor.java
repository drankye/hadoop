/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.datanode.erasurecode;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.server.datanode.CachingStrategy;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.protocol.BlockECRecoveryCommand.BlockECRecoveryInfo;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.io.erasurecode.CodecUtil;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;

/**
 * ReconstructAndTransferBlock recover one or more missed striped block in the
 * striped block group, the minimum number of live striped blocks should be
 * no less than data block number.
 *
 * | <- Striped Block Group -> |
 *  blk_0      blk_1       blk_2(*)   blk_3   ...   <- A striped block group
 *    |          |           |          |
 *    v          v           v          v
 * +------+   +------+   +------+   +------+
 * |cell_0|   |cell_1|   |cell_2|   |cell_3|  ...
 * +------+   +------+   +------+   +------+
 * |cell_4|   |cell_5|   |cell_6|   |cell_7|  ...
 * +------+   +------+   +------+   +------+
 * |cell_8|   |cell_9|   |cell10|   |cell11|  ...
 * +------+   +------+   +------+   +------+
 *  ...         ...       ...         ...
 *
 *
 * We use following steps to recover striped block group, in each round, we
 * recover <code>bufferSize</code> data until finish, the
 * <code>bufferSize</code> is configurable and may be less or larger than
 * cell size:
 * step1: read <code>bufferSize</code> data from minimum number of sources
 *        required by recovery.
 * step2: decode data for targets.
 * step3: transfer data to targets.
 *
 * In step1, try to read <code>bufferSize</code> data from minimum number
 * of sources , if there is corrupt or stale sources, read from new source
 * will be scheduled. The best sources are remembered for next round and
 * may be updated in each round.
 *
 * In step2, typically if source blocks we read are all data blocks, we
 * need to call encode, and if there is one parity block, we need to call
 * decode. Notice we only read once and recover all missed striped block
 * if they are more than one.
 *
 * In step3, send the recovered data to targets by constructing packet
 * and send them directly. Same as continuous block replication, we
 * don't check the packet ack. Since the datanode doing the recovery work
 * are one of the source datanodes, so the recovered data are sent
 * remotely.
 *
 * There are some points we can do further improvements in next phase:
 * 1. we can read the block file directly on the local datanode,
 *    currently we use remote block reader. (Notice short-circuit is not
 *    a good choice, see inline comments).
 * 2. We need to check the packet ack for EC recovery? Since EC recovery
 *    is more expensive than continuous block replication, it needs to
 *    read from several other datanodes, should we make sure the
 *    recovered result received by targets?
 */
@InterfaceAudience.Private
class StripedReconstructor {
  private static final Logger LOG = DataNode.LOG;

  private final ErasureCodingWorker worker;
  private final DataNode datanode;
  private final Configuration conf;

  private final ErasureCodingPolicy ecPolicy;


  private RawErasureDecoder decoder;

  private final ExtendedBlock blockGroup;
  private final BitSet liveBitSet;

  // position in striped internal block
  private long positionInBlock;

  private StripedReaders stripedReaders;

  private StripedWriters stripedWriters;

  private final CachingStrategy cachingStrategy;

  StripedReconstructor(ErasureCodingWorker worker,
                       BlockECRecoveryInfo recoveryInfo) {
    this.worker = worker;
    this.datanode = worker.datanode;
    this.conf = worker.conf;

    ecPolicy = recoveryInfo.getErasureCodingPolicy();

    blockGroup = recoveryInfo.getExtendedBlock();
    byte[] liveIndices = recoveryInfo.getLiveBlockIndices();
    liveBitSet = new BitSet(ecPolicy.getNumDataUnits() +
        ecPolicy.getNumParityUnits());
    for (int i = 0; i < liveIndices.length; i++) {
      liveBitSet.set(liveIndices[i]);
    }

    stripedReaders = new StripedReaders(this, datanode, conf, recoveryInfo);
    stripedWriters = new StripedWriters(this, datanode, conf, recoveryInfo);

    cachingStrategy = CachingStrategy.newDefaultStrategy();

    positionInBlock = 0L;
  }

  protected BitSet getLiveBitSet() {
    return liveBitSet;
  }

  protected ByteBuffer allocateBuffer(int length) {
    return ByteBuffer.allocate(length);
  }

  protected ExtendedBlock getBlock(int i) {
    return StripedBlockUtil.constructInternalBlock(blockGroup, ecPolicy, i);
  }

  protected long getBlockLen(int i) {
    return StripedBlockUtil.getInternalBlockLength(blockGroup.getNumBytes(),
        ecPolicy, i);
  }

  public void run() {
    datanode.incrementXmitsInProgress();
    try {
      stripedReaders.init();

      stripedWriters.init();

      reconstructAndTransfer();

      stripedWriters.endTargetBlocks();

      // Currently we don't check the acks for packets, this is similar as
      // block replication.
    } catch (Throwable e) {
      LOG.warn("Failed to recover striped block: " + blockGroup, e);
    } finally {
      datanode.decrementXmitsInProgress();

      stripedReaders.close();

      stripedWriters.close();
    }
  }

  void reconstructAndTransfer() throws IOException {
    while (positionInBlock < stripedWriters.getMaxTargetLength()) {
      long remaining = stripedWriters.getMaxTargetLength() - positionInBlock;
      final int toRecoverLen = (int) Math.min(stripedReaders.getBufferSize(), remaining);
      // step1: read from minimum source DNs required for reconstruction.
      // The returned success list is the source DNs we do real read from
      stripedReaders.readMinimum(toRecoverLen);

      // step2: decode to reconstruct targets
      recoverTargets(toRecoverLen);

      // step3: transfer data
      if (stripedWriters.transferData2Targets() == 0) {
        String error = "Transfer failed for all targets.";
        throw new IOException(error);
      }

      positionInBlock += toRecoverLen;

      clearBuffers();
    }
  }

  // Initialize decoder
  private void initDecoderIfNecessary() {
    if (decoder == null) {
      decoder = CodecUtil.createRSRawDecoder(conf, ecPolicy.getNumDataUnits(),
          ecPolicy.getNumParityUnits());
    }
  }

  private void recoverTargets(int toRecoverLen) {
    initDecoderIfNecessary();

    ByteBuffer[] inputs = stripedReaders.getInputBuffers(toRecoverLen);

    int[] erasedIndices = stripedWriters.getRealTargetIndices();
    ByteBuffer[] outputs = stripedWriters.getRealTargetBuffers(toRecoverLen);

    decoder.decode(inputs, erasedIndices, outputs);

    stripedWriters.updateRealTargetBuffers(toRecoverLen);
  }

  long getPositionInBlock() {
    return positionInBlock;
  }

  /**
   * clear all buffers
   */
  private void clearBuffers() {
    stripedReaders.clearBuffers();

    stripedWriters.clearBuffers();
  }

  InetSocketAddress getSocketAddress4Transfer(DatanodeInfo dnInfo) {
    return NetUtils.createSocketAddr(dnInfo.getXferAddr(
        datanode.getDnConf().getConnectToDnViaHostname()));
  }

  int getBufferSize() {
    return stripedReaders.getBufferSize();
  }

  DataChecksum getChecksum() {
    return stripedReaders.getChecksum();
  }

  CachingStrategy getCachingStrategy() {
    return cachingStrategy;
  }

  CompletionService<Void> createReadService() {
    return new ExecutorCompletionService<>(worker.STRIPED_READ_THREAD_POOL);
  }

  ExtendedBlock getBlockGroup() {
    return blockGroup;
  }
}

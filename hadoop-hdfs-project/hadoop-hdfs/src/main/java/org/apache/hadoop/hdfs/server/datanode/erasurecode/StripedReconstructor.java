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

import com.google.common.base.Preconditions;
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
import java.util.Arrays;
import java.util.BitSet;

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

  protected final ErasureCodingWorker worker;
  protected final DataNode datanode;
  private final Configuration conf;

  private final ErasureCodingPolicy ecPolicy;
  private final int dataBlkNum;
  private final int parityBlkNum;
  private final int cellSize;

  private RawErasureDecoder decoder;

  // Striped read buffer size
  private int bufferSize;

  protected final ExtendedBlock blockGroup;
  private final int minRequiredSources;
  // position in striped internal block
  protected long positionInBlock;

  private long maxTargetLength;

  protected StripedReaders stripedReaders;

  // The buffers and indices for striped blocks whose length is 0
  private ByteBuffer[] zeroStripeBuffers;
  private short[] zeroStripeIndices;

  protected final short[] targetIndices;
  private StripedWriters stripedWriters;

  private final static int WRITE_PACKET_SIZE = 64 * 1024;
  protected DataChecksum checksum;
  protected int maxChunksPerPacket;
  private byte[] packetBuf;
  protected byte[] checksumBuf;
  protected int bytesPerChecksum;
  protected int checksumSize;

  private BlockECRecoveryInfo recoveryInfo;

  protected final CachingStrategy cachingStrategy;

  StripedReconstructor(ErasureCodingWorker worker,
                       BlockECRecoveryInfo recoveryInfo) {
    this.worker = worker;
    this.recoveryInfo = recoveryInfo;
    this.datanode = worker.datanode;
    this.conf = worker.conf;

    ecPolicy = recoveryInfo.getErasureCodingPolicy();
    dataBlkNum = ecPolicy.getNumDataUnits();
    parityBlkNum = ecPolicy.getNumParityUnits();
    cellSize = ecPolicy.getCellSize();

    blockGroup = recoveryInfo.getExtendedBlock();
    final int cellsNum = (int)((blockGroup.getNumBytes() - 1) / cellSize + 1);
    minRequiredSources = Math.min(cellsNum, dataBlkNum);

    if (minRequiredSources < dataBlkNum) {
      zeroStripeBuffers =
          new ByteBuffer[dataBlkNum - minRequiredSources];
      zeroStripeIndices = new short[dataBlkNum - minRequiredSources];
    }

    stripedReaders = new StripedReaders(this, recoveryInfo, datanode, conf);
    stripedWriters = new StripedWriters(this, recoveryInfo);

    targetIndices = new short[stripedWriters.targets.length];

    Preconditions.checkArgument(targetIndices.length <= parityBlkNum,
        "Too much missed striped blocks.");

    getTargetIndices();
    cachingStrategy = CachingStrategy.newDefaultStrategy();

    positionInBlock = 0L;

    maxTargetLength = 0L;
    for (short targetIndex : targetIndices) {
      maxTargetLength = Math.max(maxTargetLength,
          getBlockLen(blockGroup, targetIndex));
    }
  }

  protected ByteBuffer allocateBuffer(int length) {
    return ByteBuffer.allocate(length);
  }

  protected ByteBuffer allocateReadBuffer() {
    return ByteBuffer.allocate(getBufferSize());
  }

  protected ExtendedBlock getBlock(ExtendedBlock blockGroup, int i) {
    return StripedBlockUtil.constructInternalBlock(blockGroup, cellSize,
        dataBlkNum, i);
  }

  protected long getBlockLen(ExtendedBlock blockGroup, int i) {
    return StripedBlockUtil.getInternalBlockLength(blockGroup.getNumBytes(),
        cellSize, dataBlkNum, i);
  }

  public void run() {
    datanode.incrementXmitsInProgress();
    try {
      stripedReaders.init();
      checksum = stripedReaders.checksum;
      initBufferSize();

      stripedWriters.init();

      if (zeroStripeBuffers != null) {
        for (int i = 0; i < zeroStripeBuffers.length; i++) {
          zeroStripeBuffers[i] = allocateBuffer(bufferSize);
        }
      }

      if (stripedWriters.initTargetStreams() == 0) {
        String error = "All targets are failed.";
        throw new IOException(error);
      }

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
    while (positionInBlock < maxTargetLength) {
      final int toRecover = (int) Math.min(
          bufferSize, maxTargetLength - positionInBlock);
      // step1: read from minimum source DNs required for reconstruction.
      // The returned success list is the source DNs we do real read from
      int[] successList = stripedReaders.readMinimum(toRecover);

      // step2: decode to reconstruct targets
      recoverTargets(successList, toRecover);

      // step3: transfer data
      if (transferData2Targets() == 0) {
        String error = "Transfer failed for all targets.";
        throw new IOException(error);
      }

      clearBuffers();
      positionInBlock += toRecover;
    }
  }

  private void initBufferSize() {
    bytesPerChecksum = checksum.getBytesPerChecksum();
    // The bufferSize is flat to divide bytesPerChecksum
    int readBufferSize = worker.STRIPED_READ_BUFFER_SIZE;
    bufferSize = readBufferSize < bytesPerChecksum ? bytesPerChecksum :
        readBufferSize - readBufferSize % bytesPerChecksum;
  }

  private void getTargetIndices() {
    BitSet bitset = new BitSet(dataBlkNum + parityBlkNum);
    for (int i = 0; i < stripedReaders.sources.length; i++) {
      bitset.set(stripedReaders.liveIndices[i]);
    }
    int m = 0;
    int k = 0;
    for (int i = 0; i < dataBlkNum + parityBlkNum; i++) {
      if (!bitset.get(i)) {
        if (getBlockLen(blockGroup, i) > 0) {
          if (m < stripedWriters.targets.length) {
            targetIndices[m++] = (short)i;
          }
        } else {
          zeroStripeIndices[k++] = (short)i;
        }
      }
    }
  }

  private void paddingBufferToLen(ByteBuffer buffer, int len) {
    if (len > buffer.limit()) {
      buffer.limit(len);
    }
    int toPadding = len - buffer.position();
    for (int i = 0; i < toPadding; i++) {
      buffer.put((byte) 0);
    }
  }

  // Initialize decoder
  private void initDecoderIfNecessary() {
    if (decoder == null) {
      decoder = newDecoder(dataBlkNum, parityBlkNum);
    }
  }

  private RawErasureDecoder newDecoder(int numDataUnits, int numParityUnits) {
    return CodecUtil.createRSRawDecoder(conf, numDataUnits, numParityUnits);
  }

  private int[] getErasedIndices(boolean[] targetsStatus) {
    int[] result = new int[stripedWriters.targets.length];
    int m = 0;
    for (int i = 0; i < stripedWriters.targets.length; i++) {
      if (targetsStatus[i]) {
        result[m++] = targetIndices[i];
      }
    }
    return Arrays.copyOf(result, m);
  }

  private void recoverTargets(int[] successList, int toRecoverLen) {
    initDecoderIfNecessary();
    ByteBuffer[] inputs = new ByteBuffer[dataBlkNum + parityBlkNum];
    for (int i = 0; i < successList.length; i++) {
      StripedReader reader = stripedReaders.get(successList[i]);
      ByteBuffer buffer = reader.getReadBuffer();
      paddingBufferToLen(buffer, toRecoverLen);
      inputs[reader.index] = (ByteBuffer)buffer.flip();
    }
    if (successList.length < dataBlkNum) {
      for (int i = 0; i < zeroStripeBuffers.length; i++) {
        ByteBuffer buffer = zeroStripeBuffers[i];
        paddingBufferToLen(buffer, toRecoverLen);
        int index = zeroStripeIndices[i];
        inputs[index] = (ByteBuffer)buffer.flip();
      }
    }

    boolean[] targetsStatus = stripedWriters.targetsStatus;

    int[] erasedIndices = getErasedIndices(targetsStatus);
    ByteBuffer[] outputs = new ByteBuffer[erasedIndices.length];
    int m = 0;
    for (int i = 0; i < stripedWriters.getNum(); i++) {
      if (targetsStatus[i]) {
        stripedWriters.get(i).getTargetBuffer().limit(toRecoverLen);
        outputs[m++] = stripedWriters.get(i).getTargetBuffer();
      }
    }
    decoder.decode(inputs, erasedIndices, outputs);

    for (int i = 0; i < stripedWriters.targets.length; i++) {
      if (targetsStatus[i]) {
        long blockLen = getBlockLen(blockGroup, targetIndices[i]);
        long remaining = blockLen - positionInBlock;
        if (remaining <= 0) {
          stripedWriters.get(i).getTargetBuffer().limit(0);
        } else if (remaining < toRecoverLen) {
          stripedWriters.get(i).getTargetBuffer().limit((int)remaining);
        }
      }
    }
  }

  /**
   * Send data to targets
   */
  private int transferData2Targets() {
    boolean[] targetsStatus = stripedWriters.targetsStatus;
    int nSuccess = 0;
    for (int i = 0; i < stripedWriters.targets.length; i++) {
      if (targetsStatus[i]) {
        boolean success = false;
        try {
          stripedWriters.get(i).transferData2Target(packetBuf);
          nSuccess++;
          success = true;
        } catch (IOException e) {
          LOG.warn(e.getMessage());
        }
        targetsStatus[i] = success;
      }
    }
    return nSuccess;
  }

  /**
   * clear all buffers
   */
  private void clearBuffers() {
    stripedReaders.clearBuffers();

    if (zeroStripeBuffers != null) {
      for (ByteBuffer zeroStripeBuffer : zeroStripeBuffers) {
        zeroStripeBuffer.clear();
      }
    }

    stripedWriters.clearBuffers();
  }

  protected InetSocketAddress getSocketAddress4Transfer(DatanodeInfo dnInfo) {
    return NetUtils.createSocketAddr(dnInfo.getXferAddr(
        datanode.getDnConf().getConnectToDnViaHostname()));
  }

  protected int getBufferSize() {
    return bufferSize;
  }
}

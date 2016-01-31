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
import org.apache.hadoop.fs.StorageType;
import org.apache.hadoop.hdfs.DFSUtilClient.CorruptedBlocks;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.datatransfer.PacketHeader;
import org.apache.hadoop.hdfs.server.datanode.CachingStrategy;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.protocol.BlockECRecoveryCommand.BlockECRecoveryInfo;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.hdfs.util.StripedBlockUtil.StripingChunkReadResult;
import org.apache.hadoop.io.erasurecode.CodecUtil;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

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
  private final int dataBlkNum;
  private final int parityBlkNum;
  private final int cellSize;

  private RawErasureDecoder decoder;

  // Striped read buffer size
  private int bufferSize;

  protected final ExtendedBlock blockGroup;
  private final int minRequiredSources;
  // position in striped internal block
  private long positionInBlock;

  private int[] successList;

  private boolean[] targetsStatus;

  private long maxTargetLength;

  // sources
  protected final byte[] liveIndices;
  protected final DatanodeInfo[] sources;

  private final List<StripedReader> stripedReaders;

  // The buffers and indices for striped blocks whose length is 0
  private ByteBuffer[] zeroStripeBuffers;
  private short[] zeroStripeIndices;

  // targets
  protected final DatanodeInfo[] targets;
  protected final StorageType[] targetStorageTypes;

  protected final short[] targetIndices;
  private StripedWriter[] stripedWriters;


  protected final long[] blockOffset4Targets;
  protected final long[] seqNo4Targets;

  private final static int WRITE_PACKET_SIZE = 64 * 1024;
  protected DataChecksum checksum;
  protected int maxChunksPerPacket;
  private byte[] packetBuf;
  protected byte[] checksumBuf;
  protected int bytesPerChecksum;
  protected int checksumSize;

  protected final CachingStrategy cachingStrategy;

  private final Map<Future<Void>, Integer> futures = new HashMap<>();
  private final CompletionService<Void> readService;

  StripedReconstructor(ErasureCodingWorker worker,
                       BlockECRecoveryInfo recoveryInfo) {
    this.worker = worker;
    this.datanode = worker.datanode;
    this.conf = worker.conf;

    ecPolicy = recoveryInfo.getErasureCodingPolicy();
    dataBlkNum = ecPolicy.getNumDataUnits();
    parityBlkNum = ecPolicy.getNumParityUnits();
    cellSize = ecPolicy.getCellSize();

    blockGroup = recoveryInfo.getExtendedBlock();
    final int cellsNum = (int)((blockGroup.getNumBytes() - 1) / cellSize + 1);
    minRequiredSources = Math.min(cellsNum, dataBlkNum);

    liveIndices = recoveryInfo.getLiveBlockIndices();
    sources = recoveryInfo.getSourceDnInfos();
    stripedReaders = new ArrayList<>(sources.length);
    readService =
        new ExecutorCompletionService<>(worker.STRIPED_READ_THREAD_POOL);

    Preconditions.checkArgument(liveIndices.length >= minRequiredSources,
        "No enough live striped blocks.");
    Preconditions.checkArgument(liveIndices.length == sources.length,
        "liveBlockIndices and source dns should match");

    if (minRequiredSources < dataBlkNum) {
      zeroStripeBuffers =
          new ByteBuffer[dataBlkNum - minRequiredSources];
      zeroStripeIndices = new short[dataBlkNum - minRequiredSources];
    }

    targets = recoveryInfo.getTargetDnInfos();
    targetStorageTypes = recoveryInfo.getTargetStorageTypes();
    targetIndices = new short[targets.length];

    Preconditions.checkArgument(targetIndices.length <= parityBlkNum,
        "Too much missed striped blocks.");

    stripedWriters = new StripedWriter[targets.length];

    blockOffset4Targets = new long[targets.length];
    seqNo4Targets = new long[targets.length];

    for (int i = 0; i < targets.length; i++) {
      blockOffset4Targets[i] = 0;
      seqNo4Targets[i] = 0;
    }

    getTargetIndices();
    cachingStrategy = CachingStrategy.newDefaultStrategy();

    // Store the array indices of source DNs we have read successfully.
    // In each iteration of read, the successList list may be updated if
    // some source DN is corrupted or slow. And use the updated successList
    // list of DNs for next iteration read.
    successList = new int[minRequiredSources];

    // targetsStatus store whether some target is success, it will record
    // any failed target once, if some target failed (invalid DN or transfer
    // failed), will not transfer data to it any more.
    targetsStatus = new boolean[targets.length];

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

  private long getBlockLen(ExtendedBlock blockGroup, int i) {
    return StripedBlockUtil.getInternalBlockLength(blockGroup.getNumBytes(),
        cellSize, dataBlkNum, i);
  }

  public void run() {
    datanode.incrementXmitsInProgress();
    try {
      int nSuccess = 0;
      for (int i = 0;
           i < sources.length && nSuccess < minRequiredSources; i++) {
        StripedReader reader = new StripedReader(this, datanode, conf, i, 0);
        stripedReaders.add(reader);
        if (reader.blockReader != null) {
          initChecksumAndBufferSizeIfNeeded(reader);
          successList[nSuccess++] = i;
        }
      }

      if (nSuccess < minRequiredSources) {
        String error = "Can't find minimum sources required by "
            + "recovery, block id: " + blockGroup.getBlockId();
        throw new IOException(error);
      }

      if (zeroStripeBuffers != null) {
        for (int i = 0; i < zeroStripeBuffers.length; i++) {
          zeroStripeBuffers[i] = allocateBuffer(bufferSize);
        }
      }

      checksumSize = checksum.getChecksumSize();
      int chunkSize = bytesPerChecksum + checksumSize;
      maxChunksPerPacket = Math.max(
          (WRITE_PACKET_SIZE - PacketHeader.PKT_MAX_HEADER_LEN)/chunkSize, 1);
      int maxPacketSize = chunkSize * maxChunksPerPacket
          + PacketHeader.PKT_MAX_HEADER_LEN;

      packetBuf = new byte[maxPacketSize];
      checksumBuf = new byte[checksumSize * (bufferSize / bytesPerChecksum)];

      if (initTargetStreams(targetsStatus) == 0) {
        String error = "All targets are failed.";
        throw new IOException(error);
      }

      reconstructAndTransfer();

      endTargetBlocks(targetsStatus);

      // Currently we don't check the acks for packets, this is similar as
      // block replication.
    } catch (Throwable e) {
      LOG.warn("Failed to recover striped block: " + blockGroup, e);
    } finally {
      datanode.decrementXmitsInProgress();
      // close block readers
      for (StripedReader StripedReader : stripedReaders) {
        StripedReader.closeBlockReader();
      }

      for (int i = 0; i < targets.length; i++) {
        stripedWriters[i].close();
      }
    }
  }

  void reconstructAndTransfer() throws IOException {
    while (positionInBlock < maxTargetLength) {
      final int toRecover = (int) Math.min(
          bufferSize, maxTargetLength - positionInBlock);
      // step1: read from minimum source DNs required for reconstruction.
      // The returned success list is the source DNs we do real read from
      CorruptedBlocks corruptedBlocks = new CorruptedBlocks();
      try {
        successList = readMinimumStripedData4Recovery(toRecover,
            corruptedBlocks);
      } finally {
        // report corrupted blocks to NN
        datanode.reportCorruptedBlocks(corruptedBlocks);
      }

      // step2: decode to reconstruct targets
      recoverTargets(successList, targetsStatus, toRecover);

      // step3: transfer data
      if (transferData2Targets(targetsStatus) == 0) {
        String error = "Transfer failed for all targets.";
        throw new IOException(error);
      }

      clearBuffers();
      positionInBlock += toRecover;
    }
  }

  // init checksum from block reader
  private void initChecksumAndBufferSizeIfNeeded(StripedReader stripedReader) {
    if (checksum == null) {
      checksum = stripedReader.blockReader.getDataChecksum();;
      bytesPerChecksum = checksum.getBytesPerChecksum();
      // The bufferSize is flat to divide bytesPerChecksum
      int readBufferSize = worker.STRIPED_READ_BUFFER_SIZE;
      bufferSize = readBufferSize < bytesPerChecksum ? bytesPerChecksum :
          readBufferSize - readBufferSize % bytesPerChecksum;
    } else {
      assert stripedReader.blockReader.getDataChecksum().equals(checksum);
    }
  }

  private void getTargetIndices() {
    BitSet bitset = new BitSet(dataBlkNum + parityBlkNum);
    for (int i = 0; i < sources.length; i++) {
      bitset.set(liveIndices[i]);
    }
    int m = 0;
    int k = 0;
    for (int i = 0; i < dataBlkNum + parityBlkNum; i++) {
      if (!bitset.get(i)) {
        if (getBlockLen(blockGroup, i) > 0) {
          if (m < targets.length) {
            targetIndices[m++] = (short)i;
          }
        } else {
          zeroStripeIndices[k++] = (short)i;
        }
      }
    }
  }

  /** the reading length should not exceed the length for recovery */
  private int getReadLength(int index, int recoverLength) {
    long blockLen = getBlockLen(blockGroup, index);
    long remaining = blockLen - positionInBlock;
    return (int) Math.min(remaining, recoverLength);
  }

  /**
   * Read from minimum source DNs required for reconstruction in the iteration.
   * First try the success list which we think they are the best DNs
   * If source DN is corrupt or slow, try to read some other source DN,
   * and will update the success list.
   *
   * Remember the updated success list and return it for following
   * operations and next iteration read.
   *
   * @param recoverLength the length to recover.
   * @return updated success list of source DNs we do real read
   * @throws IOException
   */
  private int[] readMinimumStripedData4Recovery(int recoverLength,
                                                CorruptedBlocks corruptedBlocks)
      throws IOException {
    Preconditions.checkArgument(recoverLength >= 0 &&
        recoverLength <= bufferSize);
    int nSuccess = 0;
    int[] newSuccess = new int[minRequiredSources];
    BitSet usedFlag = new BitSet(sources.length);
    /*
     * Read from minimum source DNs required, the success list contains
     * source DNs which we think best.
     */
    for (int i = 0; i < minRequiredSources; i++) {
      StripedReader reader = stripedReaders.get(successList[i]);
      int toRead = getReadLength(liveIndices[successList[i]], recoverLength);
      if (toRead > 0) {
        Callable<Void> readCallable =
            reader.readFromBlock(toRead, corruptedBlocks);
        Future<Void> f = readService.submit(readCallable);
        futures.put(f, successList[i]);
      } else {
        // If the read length is 0, we don't need to do real read
        reader.getReadBuffer().position(0);
        newSuccess[nSuccess++] = successList[i];
      }
      usedFlag.set(successList[i]);
    }

    while (!futures.isEmpty()) {
      try {
        StripingChunkReadResult result =
            StripedBlockUtil.getNextCompletedStripedRead(
                readService, futures, worker.STRIPED_READ_TIMEOUT_MILLIS);
        int resultIndex = -1;
        if (result.state == StripingChunkReadResult.SUCCESSFUL) {
          resultIndex = result.index;
        } else if (result.state == StripingChunkReadResult.FAILED) {
          // If read failed for some source DN, we should not use it anymore
          // and schedule read from another source DN.
          StripedReader failedReader = stripedReaders.get(result.index);
          failedReader.closeBlockReader();
          resultIndex = scheduleNewRead(usedFlag, recoverLength, corruptedBlocks);
        } else if (result.state == StripingChunkReadResult.TIMEOUT) {
          // If timeout, we also schedule a new read.
          resultIndex = scheduleNewRead(usedFlag, recoverLength, corruptedBlocks);
        }
        if (resultIndex >= 0) {
          newSuccess[nSuccess++] = resultIndex;
          if (nSuccess >= minRequiredSources) {
            // cancel remaining reads if we read successfully from minimum
            // number of source DNs required by reconstruction.
            cancelReads(futures.keySet());
            futures.clear();
            break;
          }
        }
      } catch (InterruptedException e) {
        LOG.info("Read data interrupted.", e);
        break;
      }
    }

    if (nSuccess < minRequiredSources) {
      String error = "Can't read data from minimum number of sources "
          + "required by reconstruction, block id: " + blockGroup.getBlockId();
      throw new IOException(error);
    }

    return newSuccess;
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
    int[] result = new int[targets.length];
    int m = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        result[m++] = targetIndices[i];
      }
    }
    return Arrays.copyOf(result, m);
  }

  private void recoverTargets(int[] success, boolean[] targetsStatus,
                              int toRecoverLen) {
    initDecoderIfNecessary();
    ByteBuffer[] inputs = new ByteBuffer[dataBlkNum + parityBlkNum];
    for (int i = 0; i < success.length; i++) {
      StripedReader reader = stripedReaders.get(success[i]);
      ByteBuffer buffer = reader.getReadBuffer();
      paddingBufferToLen(buffer, toRecoverLen);
      inputs[reader.index] = (ByteBuffer)buffer.flip();
    }
    if (success.length < dataBlkNum) {
      for (int i = 0; i < zeroStripeBuffers.length; i++) {
        ByteBuffer buffer = zeroStripeBuffers[i];
        paddingBufferToLen(buffer, toRecoverLen);
        int index = zeroStripeIndices[i];
        inputs[index] = (ByteBuffer)buffer.flip();
      }
    }
    int[] erasedIndices = getErasedIndices(targetsStatus);
    ByteBuffer[] outputs = new ByteBuffer[erasedIndices.length];
    int m = 0;
    for (int i = 0; i < stripedWriters.length; i++) {
      if (targetsStatus[i]) {
        stripedWriters[i].getTargetBuffer().limit(toRecoverLen);
        outputs[m++] = stripedWriters[i].getTargetBuffer();
      }
    }
    decoder.decode(inputs, erasedIndices, outputs);

    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        long blockLen = getBlockLen(blockGroup, targetIndices[i]);
        long remaining = blockLen - positionInBlock;
        if (remaining <= 0) {
          stripedWriters[i].getTargetBuffer().limit(0);
        } else if (remaining < toRecoverLen) {
          stripedWriters[i].getTargetBuffer().limit((int)remaining);
        }
      }
    }
  }

  /**
   * Schedule a read from some new source DN if some DN is corrupted
   * or slow, this is called from the read iteration.
   * Initially we may only have <code>minRequiredSources</code> number of
   * StripedBlockReader.
   * If the position is at the end of target block, don't need to do
   * real read, and return the array index of source DN, otherwise -1.
   *
   * @param used the used source DNs in this iteration.
   * @return the array index of source DN if don't need to do real read.
   */
  private int scheduleNewRead(BitSet used, int recoverLength,
                              CorruptedBlocks corruptedBlocks) {
    StripedReader reader = null;
    // step1: initially we may only have <code>minRequiredSources</code>
    // number of StripedBlockReader, and there may be some source DNs we never
    // read before, so will try to create StripedBlockReader for one new source DN
    // and try to read from it. If found, go to step 3.
    int m = stripedReaders.size();
    int toRead = 0;
    while (reader == null && m < sources.length) {
      reader = new StripedReader(this, datanode, conf, m, positionInBlock);
      stripedReaders.add(reader);
      toRead = getReadLength(liveIndices[m], recoverLength);
      if (toRead > 0) {
        if (reader.blockReader == null) {
          reader = null;
          m++;
        }
      } else {
        used.set(m);
        return m;
      }
    }

    // step2: if there is no new source DN we can use, try to find a source
    // DN we ever read from but because some reason, e.g., slow, it
    // is not in the success DN list at the begin of this iteration, so
    // we have not tried it in this iteration. Now we have a chance to
    // revisit it again.
    for (int i = 0; reader == null && i < stripedReaders.size(); i++) {
      if (!used.get(i)) {
        StripedReader StripedReader = stripedReaders.get(i);
        toRead = getReadLength(liveIndices[i], recoverLength);
        if (toRead > 0) {
          StripedReader.closeBlockReader();
          StripedReader.resetBlockReader(positionInBlock);
          if (StripedReader.blockReader != null) {
            StripedReader.getReadBuffer().position(0);
            m = i;
            reader = StripedReader;
          }
        } else {
          used.set(i);
          StripedReader.getReadBuffer().position(0);
          return i;
        }
      }
    }

    // step3: schedule if find a correct source DN and need to do real read.
    if (reader != null) {
      Callable<Void> readCallable = reader.readFromBlock(toRead, corruptedBlocks);
      Future<Void> f = readService.submit(readCallable);
      futures.put(f, m);
      used.set(m);
    }

    return -1;
  }

  // cancel all reads.
  private void cancelReads(Collection<Future<Void>> futures) {
    for (Future<Void> future : futures) {
      future.cancel(true);
    }
  }

  /**
   * Send data to targets
   */
  private int transferData2Targets(boolean[] targetsStatus) {
    int nSuccess = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        boolean success = false;
        try {
          stripedWriters[i].transferData2Target(packetBuf);
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
    for (StripedReader StripedReader : stripedReaders) {
      if (StripedReader.getReadBuffer() != null) {
        StripedReader.getReadBuffer().clear();
      }
    }

    if (zeroStripeBuffers != null) {
      for (ByteBuffer zeroStripeBuffer : zeroStripeBuffers) {
        zeroStripeBuffer.clear();
      }
    }

    for (StripedWriter writer : stripedWriters) {
      ByteBuffer targetBuffer = writer.getTargetBuffer();
      if (targetBuffer != null) {
        targetBuffer.clear();
      }
    }
  }

  // send an empty packet to mark the end of the block
  private void endTargetBlocks(boolean[] targetsStatus) {
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        try {
          stripedWriters[i].endTargetBlock(packetBuf);
        } catch (IOException e) {
          LOG.warn(e.getMessage());
        }
      }
    }
  }

  /**
   * Initialize  output/input streams for transferring data to target
   * and send create block request.
   */
  private int initTargetStreams(boolean[] targetsStatus) {
    int nSuccess = 0;
    for (short i = 0; i < targets.length; i++) {
      try {
        stripedWriters[i] = new StripedWriter(this, datanode, conf, i);
        nSuccess++;
        targetsStatus[i] = true;
      } catch (Throwable e) {
        LOG.warn(e.getMessage());
      }
    }
    return nSuccess;
  }

  protected InetSocketAddress getSocketAddress4Transfer(DatanodeInfo dnInfo) {
    return NetUtils.createSocketAddr(dnInfo.getXferAddr(
        datanode.getDnConf().getConnectToDnViaHostname()));
  }

  protected int getBufferSize() {
    return bufferSize;
  }
}

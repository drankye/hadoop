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
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.datatransfer.PacketHeader;
import org.apache.hadoop.hdfs.server.datanode.CachingStrategy;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.protocol.BlockECRecoveryCommand.BlockECRecoveryInfo;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;

@InterfaceAudience.Private
class StripedWriters {
  private static final Logger LOG = DataNode.LOG;

  protected final StripedReconstructor reconstructor;
  private final DataNode datanode;
  private final Configuration conf;

  private final ErasureCodingPolicy ecPolicy;
  private final int dataBlkNum;
  private final int parityBlkNum;
  private final int cellSize;

  protected final ExtendedBlock blockGroup;

  protected boolean[] targetsStatus;

  // targets
  protected final DatanodeInfo[] targets;
  private final short[] targetIndices;
  protected final StorageType[] targetStorageTypes;
  private long maxTargetLength;

  private StripedWriter[] writers;

  private final static int WRITE_PACKET_SIZE = 64 * 1024;
  protected int maxChunksPerPacket;
  private byte[] packetBuf;
  protected byte[] checksumBuf;
  protected int bytesPerChecksum;
  protected int checksumSize;

  protected final CachingStrategy cachingStrategy;

  StripedWriters(StripedReconstructor reconstructor, BlockECRecoveryInfo recoveryInfo) {
    this.reconstructor = reconstructor;
    this.datanode = reconstructor.datanode;
    this.conf = reconstructor.worker.conf;

    ecPolicy = recoveryInfo.getErasureCodingPolicy();
    dataBlkNum = ecPolicy.getNumDataUnits();
    parityBlkNum = ecPolicy.getNumParityUnits();
    cellSize = ecPolicy.getCellSize();

    blockGroup = recoveryInfo.getExtendedBlock();
    final int cellsNum = (int)((blockGroup.getNumBytes() - 1) / cellSize + 1);

    targets = recoveryInfo.getTargetDnInfos();
    targetStorageTypes = recoveryInfo.getTargetStorageTypes();

    writers = new StripedWriter[targets.length];

    targetIndices = new short[targets.length];
    Preconditions.checkArgument(targetIndices.length <= parityBlkNum,
        "Too much missed striped blocks.");
    initTargetIndices();

    maxTargetLength = 0L;
    for (short targetIndex : targetIndices) {
      maxTargetLength = Math.max(maxTargetLength,
          reconstructor.getBlockLen(blockGroup, targetIndex));
    }

    cachingStrategy = CachingStrategy.newDefaultStrategy();

    // targetsStatus store whether some target is success, it will record
    // any failed target once, if some target failed (invalid DN or transfer
    // failed), will not transfer data to it any more.
    targetsStatus = new boolean[targets.length];
  }

  void init() throws IOException {
    DataChecksum checksum = reconstructor.getChecksum();
    checksumSize = checksum.getChecksumSize();
    bytesPerChecksum = checksum.getBytesPerChecksum();
    int chunkSize = bytesPerChecksum + checksumSize;
    maxChunksPerPacket = Math.max(
        (WRITE_PACKET_SIZE - PacketHeader.PKT_MAX_HEADER_LEN) / chunkSize, 1);
    int maxPacketSize = chunkSize * maxChunksPerPacket
        + PacketHeader.PKT_MAX_HEADER_LEN;

    packetBuf = new byte[maxPacketSize];
    checksumBuf = new byte[checksumSize * (reconstructor.getBufferSize() / bytesPerChecksum)];

    if (initTargetStreams() == 0) {
      String error = "All targets are failed.";
      throw new IOException(error);
    }
  }

  private void initTargetIndices() {
    BitSet bitset = reconstructor.getLiveBitSet();

    int m = 0;
    int k = 0;
    for (int i = 0; i < dataBlkNum + parityBlkNum; i++) {
      if (!bitset.get(i)) {
        if (reconstructor.getBlockLen(blockGroup, i) > 0) {
          if (m < targets.length) {
            targetIndices[m++] = (short)i;
          }
        }
      }
    }
  }

  /**
   * Send data to targets
   */
  int transferData2Targets() {
    int nSuccess = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        boolean success = false;
        try {
          getWriter(i).transferData2Target(packetBuf);
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
  protected void clearBuffers() {
    for (StripedWriter writer : writers) {
      ByteBuffer targetBuffer = writer.getTargetBuffer();
      if (targetBuffer != null) {
        targetBuffer.clear();
      }
    }
  }

  // send an empty packet to mark the end of the block
  void endTargetBlocks() {
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        try {
          writers[i].endTargetBlock(packetBuf);
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
  int initTargetStreams() {
    int nSuccess = 0;
    for (short i = 0; i < targets.length; i++) {
      try {
        writers[i] = new StripedWriter(this, datanode, conf, i);
        nSuccess++;
        targetsStatus[i] = true;
      } catch (Throwable e) {
        LOG.warn(e.getMessage());
      }
    }
    return nSuccess;
  }

  int getTargets() {
    return targets.length;
  }

  int getRealTargets() {
    int m = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        m++;
      }
    }
    return m;
  }

  int[] getRealTargetIndices() {
    int realTargets = getRealTargets();
    int[] results = new int[realTargets];
    int m = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        results[m++] = targetIndices[i];
      }
    }
    return results;
  }

  ByteBuffer[] getRealTargetBuffers(int toRecoverLen) {
    int numGood = getRealTargets();
    ByteBuffer[] outputs = new ByteBuffer[numGood];
    int m = 0;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        getWriter(i).getTargetBuffer().limit(toRecoverLen);
        outputs[m++] = getWriter(i).getTargetBuffer();
      }
    }
    return outputs;
  }

  void updateRealTargetBuffers(int toRecoverLen) {;
    for (int i = 0; i < targets.length; i++) {
      if (targetsStatus[i]) {
        long blockLen = reconstructor.getBlockLen(blockGroup, targetIndices[i]);
        long remaining = blockLen - reconstructor.getPositionInBlock();
        if (remaining <= 0) {
          getWriter(i).getTargetBuffer().limit(0);
        } else if (remaining < toRecoverLen) {
          getWriter(i).getTargetBuffer().limit((int)remaining);
        }
      }
    }
  }

  StripedWriter getWriter(int index) {
    return writers[index];
  }

  short[] getTargetIndices() {
    return targetIndices;
  }

  int getNumWriters() {
    return writers.length;
  }

  long getMaxTargetLength() {
    return maxTargetLength;
  }

  void close() {
    for (int i = 0; i < targets.length; i++) {
      writers[i].close();
    }
  }
}

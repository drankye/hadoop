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
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

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
  private final int minRequiredSources;

  protected boolean[] targetsStatus;

  private long maxTargetLength;

  // targets
  protected final DatanodeInfo[] targets;
  protected final StorageType[] targetStorageTypes;

  protected final short[] targetIndices;

  private StripedWriter[] writers;

  protected final long[] blockOffset4Targets;
  protected final long[] seqNo4Targets;

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
    minRequiredSources = Math.min(cellsNum, dataBlkNum);

    targets = recoveryInfo.getTargetDnInfos();
    targetStorageTypes = recoveryInfo.getTargetStorageTypes();
    targetIndices = new short[targets.length];

    Preconditions.checkArgument(targetIndices.length <= parityBlkNum,
        "Too much missed striped blocks.");

    writers = new StripedWriter[targets.length];

    blockOffset4Targets = new long[targets.length];
    seqNo4Targets = new long[targets.length];

    for (int i = 0; i < targets.length; i++) {
      blockOffset4Targets[i] = 0;
      seqNo4Targets[i] = 0;
    }

    cachingStrategy = CachingStrategy.newDefaultStrategy();

    // targetsStatus store whether some target is success, it will record
    // any failed target once, if some target failed (invalid DN or transfer
    // failed), will not transfer data to it any more.
    targetsStatus = new boolean[targets.length];

    maxTargetLength = 0L;
    for (short targetIndex : targetIndices) {
      maxTargetLength = Math.max(maxTargetLength,
          reconstructor.getBlockLen(blockGroup, targetIndex));
    }
  }

  void init() throws IOException {
    checksumSize = reconstructor.checksum.getChecksumSize();
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

  void close() {
    for (int i = 0; i < targets.length; i++) {
      writers[i].close();
    }
  }

  StripedWriter get(int i) {
    return writers[i];
  }

  public int getNum() {
    return writers.length;
  }
}

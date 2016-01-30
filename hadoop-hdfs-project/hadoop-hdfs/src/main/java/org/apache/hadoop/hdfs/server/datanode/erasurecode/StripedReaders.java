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
import org.apache.hadoop.hdfs.DFSUtilClient.CorruptedBlocks;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.protocol.BlockECRecoveryCommand.BlockECRecoveryInfo;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.hdfs.util.StripedBlockUtil.StripingChunkReadResult;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

@InterfaceAudience.Private
class StripedReaders {
  private static final Logger LOG = DataNode.LOG;

  protected StripedReconstructor reconstrutor;
  private final DataNode datanode;
  private final Configuration conf;

  private final ErasureCodingPolicy ecPolicy;
  private final int dataBlkNum;
  private final int parityBlkNum;
  private final int cellSize;

  protected final ExtendedBlock blockGroup;
  private final int minRequiredSources;
  protected DataChecksum checksum;
  private int[] successList;

  // sources
  protected final byte[] liveIndices;
  protected final DatanodeInfo[] sources;

  private final List<StripedReader> readers;

  private final Map<Future<Void>, Integer> futures = new HashMap<>();
  private final CompletionService<Void> readService;

  StripedReaders(StripedReconstructor rtb,
                 BlockECRecoveryInfo recoveryInfo, DataNode datanode,
                 Configuration conf) {
    this.reconstrutor = rtb;
    this.datanode = datanode;
    this.conf = conf;

    ecPolicy = recoveryInfo.getErasureCodingPolicy();
    dataBlkNum = ecPolicy.getNumDataUnits();
    parityBlkNum = ecPolicy.getNumParityUnits();
    cellSize = ecPolicy.getCellSize();

    blockGroup = recoveryInfo.getExtendedBlock();
    final int cellsNum = (int)((blockGroup.getNumBytes() - 1) / cellSize + 1);
    minRequiredSources = Math.min(cellsNum, dataBlkNum);

    liveIndices = recoveryInfo.getLiveBlockIndices();
    sources = recoveryInfo.getSourceDnInfos();
    readers = new ArrayList<>(sources.length);
    readService =
        new ExecutorCompletionService<>(reconstrutor.worker.STRIPED_READ_THREAD_POOL);

    Preconditions.checkArgument(liveIndices.length >= minRequiredSources,
        "No enough live striped blocks.");
    Preconditions.checkArgument(liveIndices.length == sources.length,
        "liveBlockIndices and source dns should match");
  }

  void init() throws IOException {
    // Store the array indices of source DNs we have read successfully.
    // In each iteration of read, the successList list may be updated if
    // some source DN is corrupted or slow. And use the updated successList
    // list of DNs for next iteration read.
    successList = new int[minRequiredSources];

    int nSuccess = 0;
    for (int i = 0;
         i < sources.length && nSuccess < minRequiredSources; i++) {
      StripedReader reader = new StripedReader(this, datanode, conf, i, 0);
      readers.add(reader);
      if (reader.blockReader != null) {
        initOrVerifyChecksum(reader);
        successList[nSuccess++] = i;
      }
    }

    if (nSuccess < minRequiredSources) {
      String error = "Can't find minimum sources required by "
          + "recovery, block id: " + blockGroup.getBlockId();
      throw new IOException(error);
    }
  }

  // init checksum from block reader
  private void initOrVerifyChecksum(StripedReader stripedReader) {
    if (checksum == null) {
      checksum = stripedReader.blockReader.getDataChecksum();
    } else {
      assert stripedReader.blockReader.getDataChecksum().equals(checksum);
    }
  }

  /** the reading length should not exceed the length for recovery */
  private int getReadLength(int index, int recoverLength) {
    long blockLen = reconstrutor.getBlockLen(blockGroup, index);
    long remaining = blockLen - reconstrutor.positionInBlock;
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
  int[] readMinimum(int recoverLength) throws IOException {
    CorruptedBlocks corruptedBlocks = new CorruptedBlocks();
    try {
      successList = doReadMinimum(recoverLength, corruptedBlocks);
      return successList;
    } finally {
      // report corrupted blocks to NN
      datanode.reportCorruptedBlocks(corruptedBlocks);
    }
  }

    int[] doReadMinimum(int recoverLength, CorruptedBlocks corruptedBlocks)
      throws IOException {
    Preconditions.checkArgument(recoverLength >= 0 &&
        recoverLength <= reconstrutor.getBufferSize());
    int nSuccess = 0;
    int[] newSuccess = new int[minRequiredSources];
    BitSet usedFlag = new BitSet(sources.length);
    /*
     * Read from minimum source DNs required, the success list contains
     * source DNs which we think best.
     */
    for (int i = 0; i < minRequiredSources; i++) {
      StripedReader reader = readers.get(successList[i]);
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
                readService, futures, reconstrutor.worker.STRIPED_READ_TIMEOUT_MILLIS);
        int resultIndex = -1;
        if (result.state == StripingChunkReadResult.SUCCESSFUL) {
          resultIndex = result.index;
        } else if (result.state == StripingChunkReadResult.FAILED) {
          // If read failed for some source DN, we should not use it anymore
          // and schedule read from another source DN.
          StripedReader failedReader = readers.get(result.index);
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
    int m = readers.size();
    int toRead = 0;
    while (reader == null && m < sources.length) {
      reader = new StripedReader(this, datanode, conf, m, reconstrutor.positionInBlock);
      readers.add(reader);
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
    for (int i = 0; reader == null && i < readers.size(); i++) {
      if (!used.get(i)) {
        StripedReader StripedReader = readers.get(i);
        toRead = getReadLength(liveIndices[i], recoverLength);
        if (toRead > 0) {
          StripedReader.closeBlockReader();
          StripedReader.resetBlockReader(reconstrutor.positionInBlock);
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

  void close() {
    for (StripedReader StripedReader : readers) {
      StripedReader.closeBlockReader();
    }
  }

  StripedReader get(int i) {
    return readers.get(i);
  }

  void clearBuffers() {
    for (StripedReader StripedReader : readers) {
      if (StripedReader.getReadBuffer() != null) {
        StripedReader.getReadBuffer().clear();
      }
    }
  }
}

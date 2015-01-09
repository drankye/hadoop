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
package org.apache.hadoop.io.ec.coder;


import org.apache.hadoop.io.ec.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractErasureCoder {

  private ErasureCoderCallback callback;

  public void setCallback(ErasureCoderCallback callback) {
    this.callback = callback;
  }

  protected void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    callback.beforeCoding(inputBlocks, outputBlocks);
  }

  protected boolean hasNextInputs() {
    return callback.hasNextInputs();
  }

  protected ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) {
    return callback.getNextInputChunks(inputBlocks);
  }

  protected ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks) {
    return callback.getNextOutputChunks(outputBlocks);
  }

  protected void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks) {
    callback.withCoded(inputChunks, outputChunks);
  }

  protected void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    callback.postCoding(inputBlocks, outputBlocks);
  }

  protected ByteBuffer[] convert(ECChunk[] chunks) {
    ByteBuffer[] buffers = new ByteBuffer[chunks.length];

    for (int i = 0; i < chunks.length; i++) {
      buffers[i] = chunks[i].getChunkBuffer();
    }

    return buffers;
  }

  /**
   * Find out which blocks are missing in order to get erased locations
   * @param inputBlocks all decode input blocks
   * @return erased location list
   */
  protected int[] getErasedLocations(ECBlock[] inputBlocks) {
    List<Integer> erasedLocationList = new ArrayList<Integer>();
    for (int i = 0; i < inputBlocks.length; i++) {
      ECBlock readBlock = inputBlocks[i];
      if (readBlock.isMissing()) {
        erasedLocationList.add(i);
      }
    }

    //change to arrays
    int[] erasedLocations = new int[erasedLocationList.size()];
    for (int i = 0; i < erasedLocationList.size(); i++) {
      erasedLocations[i] = erasedLocationList.get(i);
    }
    return erasedLocations;
  }

  protected ECBlock[] getErasedBlocks(ECBlock[] inputBlocks, int[] erasedLocations) {
    ECBlock[] outputBlocks = new ECBlock[erasedLocations.length];
    for (int i = 0; i < erasedLocations.length; i++) {
      ECBlock readBlock = inputBlocks[erasedLocations[i]];
      outputBlocks[i] = new ECBlock(readBlock.getBlockId(), readBlock.isParity());
    }
    return outputBlocks;
  }
}

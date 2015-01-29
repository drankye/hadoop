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
package org.apache.hadoop.io.erasurecode.blockcoder;


import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;
import org.apache.hadoop.io.ec.rawcoder.RawErasureCoder;

/**
 * A common class of basic facilities to be shared by encoder and decoder
 *
 * It implements the {@link ErasureCoder} interface.
 */
public abstract class AbstractErasureCoder implements ErasureCoder {

  private RawErasureCoder rawCoder;
  private ErasureCoderCallback callback;

  /**
   * Constructor providing with a rawCoder. The raw blockcoder can be determined by
   * configuration or by default for a codec.
   *
   * @param rawCoder
   */
  public AbstractErasureCoder(RawErasureCoder rawCoder) {
    this.rawCoder = rawCoder;
  }

  @Override
  public void setCallback(ErasureCoderCallback callback) {
    this.callback = callback;
  }

  /**
   * Get the underlying raw blockcoder.
   * @return the underlying raw blockcoder
   */
  protected RawErasureCoder getRawCoder() {
    return rawCoder;
  }

  /**
   * Notify the caller to prepare for reading the input blocks and writing to the
   * output blocks via the callback.
   * @param inputBlocks
   * @param outputBlocks
   */
  protected void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    callback.beforeCoding(inputBlocks, outputBlocks);
  }

  /**
   * Any next input chunk for coding via the callback
   * @return true if any left input chunks to code, otherwise false
   */
  protected boolean hasNextInputs() {
    return callback.hasNextInputs();
  }

  /**
   * Get next input chunks from the input blocks for coding via the callback.
   * @param inputBlocks
   * @return
   */
  protected ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) {
    return callback.getNextInputChunks(inputBlocks);
  }

  /**
   * Get next output chunks for from output blocks for coding via the callback.
   * @param outputBlocks
   * @return
   */
  protected ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks) {
    return callback.getNextOutputChunks(outputBlocks);
  }

  /**
   * Notify the caller it's done coding the chunks via the callback.
   * @param inputChunks
   * @param outputChunks
   */
  protected void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks) {
    callback.withCoded(inputChunks, outputChunks);
  }

  /**
   * Notify the caller it's done coding the group via the callback, good chances
   * to close input blocks and flush output blocks.
   */
  protected void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    callback.postCoding(inputBlocks, outputBlocks);
  }

  /**
   * Find out how many blocks are erased.
   * @param inputBlocks all the input blocks
   * @return number of erased blocks
   */
  protected static int getNumErasedBlocks(ECBlock[] inputBlocks) {
    int numErased = 0;
    for (int i = 0; i < inputBlocks.length; i++) {
      if (inputBlocks[i].isMissing()) {
        numErased ++;
      }
    }

    return numErased;
  }

  /**
   * Get indexes of erased blocks from inputBlocks
   * @param inputBlocks
   * @return indexes of erased blocks from inputBlocks
   */
  protected int[] getErasedIndexes(ECBlock[] inputBlocks) {
    int numErased = getNumErasedBlocks(inputBlocks);
    if (numErased == 0) {
      return new int[0];
    }

    int[] erasedIndexes = new int[numErased];
    for (int i = 0; i < inputBlocks.length; i++) {
      if (inputBlocks[i].isMissing()) {
        erasedIndexes[i] = i;
      }
    }

    return erasedIndexes;
  }

  /**
   * Get erased input blocks from inputBlocks
   * @param inputBlocks
   * @return an array of erased blocks from inputBlocks
   */
  protected ECBlock[] getErasedBlocks(ECBlock[] inputBlocks) {
    int numErased = getNumErasedBlocks(inputBlocks);
    if (numErased == 0) {
      return new ECBlock[0];
    }

    ECBlock[] erasedBlocks = new ECBlock[numErased];
    for (int i = 0; i < inputBlocks.length; i++) {
      if (inputBlocks[i].isMissing()) {
        erasedBlocks[i] = inputBlocks[i];
      }
    }

    return erasedBlocks;
  }

  @Override
  public void release() {
    rawCoder.release();
  }
}

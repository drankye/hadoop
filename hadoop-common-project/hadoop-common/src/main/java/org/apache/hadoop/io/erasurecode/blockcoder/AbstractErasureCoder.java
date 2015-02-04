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
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureCoder;

import java.util.Iterator;

/**
 * A common class of basic facilities to be shared by encoder and decoder
 *
 * It implements the {@link ErasureCoder} interface.
 */
public abstract class AbstractErasureCoder implements ErasureCoder {

  private RawErasureCoder rawCoder;

  /**
   * Constructor providing with a rawCoder. The raw coder can be determined by
   * configuration or by default for a codec.
   *
   * @param rawCoder
   */
  public AbstractErasureCoder(RawErasureCoder rawCoder) {
    setRawCoder(rawCoder);
  }

  /**
   * Default constructor.
   */
  public AbstractErasureCoder() {
    // Nothing to do
  }

  @Override
  public void setRawCoder(RawErasureCoder rawCoder) {
    this.rawCoder = rawCoder;
  }

  /**
   * Get the underlying raw blockcoder.
   * @return the underlying raw blockcoder
   */
  protected RawErasureCoder getRawCoder() {
    return rawCoder;
  }

  /**
   * Calculating a coding step
   * @return
   */
  protected abstract CodingStep perform();

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

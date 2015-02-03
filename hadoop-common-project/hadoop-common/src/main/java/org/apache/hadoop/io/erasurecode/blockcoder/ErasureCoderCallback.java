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
import org.apache.hadoop.io.erasurecode.ECChunk;

/**
 * An erasure coder callback to get input/output {@link ECChunk}s from
 * {@link ECBlock}. To ease the encoding or decoding process and management
 * of blocks and chunks for the caller, it also calls back in the beginning,
 * the coding and the end.
 */
public interface ErasureCoderCallback {

  /**
   * Notify the caller to prepare for reading the input blocks and writing to the
   * output blocks.
   * @param inputBlocks
   * @param outputBlocks
   */
  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);

  /**
   * Any next input chunk for coding?
   * @return true if any left input chunks to code, otherwise false
   */
  public boolean hasNextInputs();

  /**
   * Get next input chunks from the input blocks for coding.
   * @param inputBlocks
   * @return an array of input chunks, empty indicating EOF
   */
  public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks);

  /**
   * Get next chunks to output from the output blocks for coding.
   * @param outputBlocks
   * @return an array of output chunks
   */
  public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks);

  /**
   * Notify the caller it's done coding the chunks.
   * @param inputChunks
   * @param outputChunks
   */
  public void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks);

  /**
   * Notify the caller it's done coding the group, good chances to close input
   * blocks and flush output blocks
   */
  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);
}

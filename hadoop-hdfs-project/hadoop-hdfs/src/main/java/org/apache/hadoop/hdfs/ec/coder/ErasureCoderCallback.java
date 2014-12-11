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
package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECChunk;

import java.io.IOException;

/**
 * Callback to be called by ECWorker to get input/output ECChunks for an ECBlock
 */
public interface ErasureCoderCallback {

  /**
   * Prepare for reading input blocks and writing output blocks.
   * Will be called before any coding work with the input and output blocks
   * @param inputBlocks
   * @param outputBlocks
   */
  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);

  /**
   * Any next input chunk for coding?
   * Will be called to see if any left input chunks to code
   * @return
   */
  public boolean hasNextInputs();

  /**
   * Get next input chunks for the blocks for coding
   * @param inputBlocks
   * @return
   */
  public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks);

  /**
   * Get next chunks to output for the blocks for coding
   * @param outputBlocks
   * @return
   */
  public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks);

  /**
   * Notify the coding finished event for the group of chunks
   * @param inputChunks
   * @param outputChunks
   */
  public void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks);


  /**
   * Done with coding of group, chances to close input blocks and flush output blocks
   */
  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);
}

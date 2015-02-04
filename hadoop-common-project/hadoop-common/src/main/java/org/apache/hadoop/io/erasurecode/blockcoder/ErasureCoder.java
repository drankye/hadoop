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

import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureCoder;

/**
 * An erasure coder to perform encoding or decoding given a group. Generally it
 * involves calculating necessary internal steps according to codec logic. For
 * each step,it calculates necessary input blocks to read chunks from and output
 * parity blocks to write parity chunks into from the group; then extracts chunks
 * from inputs and invokes underlying RawErasureCoder to encode or decode until
 * the input blocks are exhausted.
 *
 * As to how to extract input chunks from input blocks and output chunk buffers
 * from output blocks, it leverages an {@link ErasureCoderCallback} for decoupling,
 * as itself doesn't know to do it since it depends on the context in which it's
 * called. In HDFS, it can be HDFS client (in stripping case) or DataNode (in
 * offline transforming case).
 */
public interface ErasureCoder {

  /**
   * Initialize with the important parameters for the code. These parameters will
   * be used to initialize the underlying raw erasure coder
   * {@link org.apache.hadoop.io.erasurecode.rawcoder.RawErasureCoder}.
   *
   * @param numDataUnits how many data inputs for the coding
   * @param numParityUnits how many parity outputs the coding generates
   * @param chunkSize the size of the input/output buffer
   */
  public void initialize(int numDataUnits, int numParityUnits, int chunkSize);

  /**
   * Set the underlying raw erasure coder or
   * {@link org.apache.hadoop.io.erasurecode.rawcoder.RawErasureCoder}
   *
   * @param rawCoder
   */
  public void setRawCoder(RawErasureCoder rawCoder);

  /**
   * Release the resources if any. Good chance to invoke RawErasureCoder#release.
   */
  public void release();
}

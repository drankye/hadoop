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
package org.apache.hadoop.io.ec.rawcoder;

import java.nio.ByteBuffer;

public abstract class AbstractRawErasureCoder {

  private int dataSize;
  private int paritySize;
  private int chunkSize;

  public AbstractRawErasureCoder(int dataSize, int paritySize, int chunkSize) {
    this.dataSize = dataSize;
    this.paritySize = paritySize;
    this.chunkSize = chunkSize;
  }

  /**
   * The number of elements in the message.
   */
  public int dataSize() {
    return dataSize;
  }

  /**
   * The number of elements in the code.
   */
  public int paritySize() {
    return paritySize;
  }

  /**
   * Chunk buffer size for an encode()/decode() call
   */
  public int chunkSize() {
    return chunkSize;
  }

  protected byte[][] getData(ByteBuffer[] byteBuffers) {
    byte[][] data = new byte[byteBuffers.length][];
    for (int i = 0; i < byteBuffers.length; i++) {
      data[i] = new byte[byteBuffers[i].limit()];
      byteBuffers[i].get(data[i]);
    }
    return data;
  }

  protected void writeBuffer(ByteBuffer[] byteBuffers, byte[][] data) {
    for (int i = 0;i < byteBuffers.length; i++) {
      byteBuffers[i].clear();
      byteBuffers[i].put(data[i]);
      byteBuffers[i].flip();
    }
  }

  public void clean() {}
}

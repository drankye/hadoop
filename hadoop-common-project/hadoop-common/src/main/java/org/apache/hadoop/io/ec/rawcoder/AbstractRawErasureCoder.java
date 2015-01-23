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

import org.apache.hadoop.io.ec.ECChunk;

import java.nio.ByteBuffer;

/**
 * A common class to be shared by encoder and decoder
 */
public abstract class AbstractRawErasureCoder {

  private int dataSize;
  private int paritySize;
  private int chunkSize;

  /**
   * The constructor
   * @param dataSize
   * @param paritySize
   * @param chunkSize
   */
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

  protected static ByteBuffer[] toBuffers(ECChunk[] chunks) {
    ByteBuffer[] buffers = new ByteBuffer[chunks.length];

    for (int i = 0; i < chunks.length; i++) {
      buffers[i] = chunks[i].getBuffer();
    }

    return buffers;
  }

  protected static byte[][] toArray(ECChunk[] chunks) {
    byte[][] bytesArr = new byte[chunks.length][];

    for (int i = 0; i < chunks.length; i++) {
      bytesArr[i] = chunks[i].getBuffer().array();
    }

    return bytesArr;
  }

  public void release() {
    // Nothing to do by default
  }
}

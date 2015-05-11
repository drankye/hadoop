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
package org.apache.hadoop.io.erasurecode;

import java.nio.ByteBuffer;

/**
 * A wrapper for ByteBuffer or bytes array for an erasure code chunk.
 */
public class ECChunk {

  private ByteBuffer chunkBuffer;

  /**
   * Wrapping a ByteBuffer
   * @param buffer
   */
  public ECChunk(ByteBuffer buffer) {
    this.chunkBuffer = buffer;
  }

  /**
   * Wrapping a bytes array
   * @param buffer
   */
  public ECChunk(byte[] buffer) {
    this.chunkBuffer = ByteBuffer.wrap(buffer);
  }

  /**
   * Convert to ByteBuffer
   * @return ByteBuffer
   */
  public ByteBuffer getBuffer() {
    return chunkBuffer;
  }

  /**
   * Convert an array of this chunks to an array of ByteBuffers
   * @param chunks
   * @return an array of ByteBuffers
   */
  public static ByteBuffer[] toBuffers(ECChunk[] chunks) {
    ByteBuffer[] buffers = new ByteBuffer[chunks.length];

    for (int i = 0; i < chunks.length; i++) {
      buffers[i] = chunks[i].getBuffer();
    }

    return buffers;
  }
}

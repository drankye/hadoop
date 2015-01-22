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
 * An abstract raw erasure decoder class
 */
public abstract class AbstractRawErasureDecoder extends AbstractRawErasureCoder implements RawErasureDecoder {

  public AbstractRawErasureDecoder(int dataSize, int paritySize, int chunkSize) {
    super(dataSize, paritySize, chunkSize);
  }

  @Override
  public void decode(ByteBuffer[] inputs, int[] erasedIndexes, ByteBuffer[] outputs) {
    doDecode(inputs, erasedIndexes, outputs);
  }

  protected abstract void doDecode(ByteBuffer[] inputs, int[] erasedIndexes, ByteBuffer[] outputs);

  @Override
  public void decode(byte[][] inputs, int[] erasedIndexes, byte[][] outputs) {
    doDecode(inputs, erasedIndexes, outputs);
  }

  protected abstract void doDecode(byte[][] inputs, int[] erasedIndexes, byte[][] outputs);

  @Override
  public void decode(ECChunk[] inputs, int[] erasedIndexes, ECChunk[] outputs) {
    doDecode(inputs, erasedIndexes, outputs);
  }

  protected abstract void doDecode(ECChunk[] inputs, int[] erasedIndexes, ECChunk[] outputs);
}

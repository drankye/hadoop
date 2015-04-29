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
package org.apache.hadoop.io.erasurecode.rawcoder;

import org.apache.hadoop.io.erasurecode.ECChunk;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An abstract raw erasure decoder that's to be inherited by new decoders.
 *
 * It implements the {@link RawErasureDecoder} interface.
 */
public abstract class AbstractRawErasureDecoder extends AbstractRawErasureCoder
    implements RawErasureDecoder {

  // Hope to reset decoding buffers a little faster using it
  protected static byte[] ZERO_BYTES;

  @Override
  public void initialize(int numDataUnits, int numParityUnits,
                         int chunkSize) {
    super.initialize(numDataUnits, numParityUnits, chunkSize);

    ZERO_BYTES = new byte[chunkSize];
    Arrays.fill(ZERO_BYTES, (byte) 0);
  }

  @Override
  public void decode(ByteBuffer[] inputs, int[] erasedIndexes,
                     ByteBuffer[] outputs) {
    checkParameters(inputs, erasedIndexes, outputs);

    doDecode(inputs, erasedIndexes, outputs);
  }

  /**
   * Perform the real decoding using ByteBuffer
   * @param inputs
   * @param erasedIndexes
   * @param outputs
   */
  protected abstract void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                                   ByteBuffer[] outputs);

  @Override
  public void decode(byte[][] inputs, int[] erasedIndexes, byte[][] outputs) {
    checkParameters(inputs, erasedIndexes, outputs);

    doDecode(inputs, erasedIndexes, outputs);
  }

  /**
   * Perform the real decoding using bytes array
   * @param inputs
   * @param erasedIndexes
   * @param outputs
   */
  protected abstract void doDecode(byte[][] inputs, int[] erasedIndexes,
                                   byte[][] outputs);

  @Override
  public void decode(ECChunk[] inputs, int[] erasedIndexes,
                     ECChunk[] outputs) {
    ECChunk goodChunk = inputs[0]; // Most often
    if (goodChunk == null) {
      for (int i = 0; i < inputs.length; ++i) {
        if (inputs[i] != null) {
          goodChunk = inputs[i];
          break;
        }
      }
    }
    if (goodChunk == null) {
      throw new IllegalArgumentException("No valid input provided");
    }

    boolean hasArray = goodChunk.getBuffer().hasArray();
    if (hasArray) {
      byte[][] inputBytesArr = ECChunk.toArrays(inputs);
      byte[][] outputBytesArr = ECChunk.toArrays(outputs);
      decode(inputBytesArr, erasedIndexes, outputBytesArr);
    } else {
      ByteBuffer[] inputBuffers = ECChunk.toBuffers(inputs);
      ByteBuffer[] outputBuffers = ECChunk.toBuffers(outputs);
      decode(inputBuffers, erasedIndexes, outputBuffers);
    }
  }

  /**
   * Perform the encoding work using bytes array, via converting to direct buffers.
   * Please note this may be not efficient and serves as fall-back. We should
   * avoid calling into this.
   * @param inputs
   * @param outputs
   */
  protected void doDecodeByConvertingToDirectBuffers(
      byte[][] inputs, int[] erasedIndexes, byte[][] outputs) {
    ByteBuffer[] inputBuffers = new ByteBuffer[inputs.length];
    ByteBuffer[] outputBuffers = new ByteBuffer[outputs.length];

    for(int i = 0; i < inputs.length; ++i) {
      inputBuffers[i] = convertInputBuffer(inputs[i]);
    }

    for(int i = 0; i < outputs.length; ++i) {
      outputBuffers[i] = convertOutputBuffer(outputs[i]);
    }

    doDecode(inputBuffers, erasedIndexes, outputBuffers);
  }

  /**
   * Check and validate decoding parameters, throw exception accordingly. The
   * checking assumes it's a MDS code. Other code  can override this.
   * @param inputs
   * @param erasedIndexes
   * @param outputs
   */
  protected void checkParameters(Object[] inputs, int[] erasedIndexes,
                                 Object[] outputs) {
    if (inputs.length != getNumParityUnits() + getNumDataUnits()) {
      throw new IllegalArgumentException("Invalid inputs length");
    }

    if (erasedIndexes.length != outputs.length) {
      throw new IllegalArgumentException(
          "erasedIndexes and outputs mismatch in length");
    }

    if (erasedIndexes.length > getNumParityUnits()) {
      throw new IllegalArgumentException(
          "Too many erased, not recoverable");
    }

    int validInputs = 0;
    for (int i = 0; i < inputs.length; ++i) {
      if (inputs[i] != null) {
        validInputs += 1;
      }
    }

    if (validInputs < getNumDataUnits()) {
      throw new IllegalArgumentException(
          "No enough valid inputs are provided, not recoverable");
    }
  }

  /**
   * Get indexes into inputs array for items marked as null, either erased or
   * not to read.
   * @return indexes into inputs array
   */
  protected int[] getErasedOrNotToReadIndexes(Object[] inputs) {
    int[] invalidIndexes = new int[inputs.length];
    int idx = 0;
    for (int i = 0; i < inputs.length; i++) {
      if (inputs[i] == null) {
        invalidIndexes[idx++] = i;
      }
    }

    return Arrays.copyOf(invalidIndexes, idx);
  }

  // parity units + data units
  protected int getNumInputUnits() {
    return getNumParityUnits() + getNumDataUnits();
  }
}

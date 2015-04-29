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

import org.apache.hadoop.io.erasurecode.rawcoder.util.RSUtil;

import java.nio.ByteBuffer;

/**
 * A raw erasure decoder in RS code scheme in pure Java in case native one
 * isn't available in some environment. Please always use native implementations
 * when possible.
 */
public class RSRawDecoder extends AbstractRawErasureDecoder {
  // To describe and calculate the needed Vandermonde matrix
  private int[] errSignature;
  private int[] primitivePower;

  /**
   * We need a set of reusable buffers either for the bytes array
   * decoding version or direct buffer decoding version. Not both.
   */
  // Reused buffers for decoding with bytes arrays
  private byte[][] byteArrayBuffersForInput;
  private byte[][] adjustedByteArrayInputsParameter;

  private byte[][] byteArrayBuffersForOutput;
  private byte[][] adjustedByteArrayOutputsParameter;

  // Reused buffers for decoding with direct ByteBuffers
  private ByteBuffer[] directBuffersForInput;
  private ByteBuffer[] adjustedDirectBufferInputsParameter;

  private ByteBuffer[] adjustedDirectBufferOutputsParameter;
  private ByteBuffer[] directBuffersForOutput;

  @Override
  public void initialize(int numDataUnits, int numParityUnits, int chunkSize) {
    super.initialize(numDataUnits, numParityUnits, chunkSize);
    assert (getNumDataUnits() + getNumParityUnits() < RSUtil.GF.getFieldSize());

    this.errSignature = new int[getNumParityUnits()];
    this.primitivePower = RSUtil.getPrimitivePower(getNumDataUnits(),
        getNumParityUnits());
  }

  @Override
  public void decode(ByteBuffer[] inputs, int[] erasedIndexes,
                     ByteBuffer[] outputs) {
    decodeWith(inputs, erasedIndexes, outputs, true);
  }

  @Override
  protected void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      RSUtil.GF.substitute(inputs, outputs[i], primitivePower[i]);
    }

    int dataLen = inputs[0].remaining();
    RSUtil.GF.solveVandermondeSystem(errSignature, outputs,
        erasedIndexes.length, dataLen);
  }

  @Override
  public void decode(byte[][] inputs, int[] erasedIndexes, byte[][] outputs) {
    decodeWith(inputs, erasedIndexes, outputs, false);
  }

  private void decodeWith(Object[] inputs, int[] erasedIndexes,
                          Object[] outputs, boolean usingDirectBuffer) {
    checkParameters(inputs, erasedIndexes, outputs);

    if (usingDirectBuffer) {
      ensureWhenUseDirectBuffers();
    } else {
      ensureWhenUseArrayBuffers();
    }

    /**
     * As passed parameters are friendly to callers but not to the underlying
     * implementations, so we have to adjust them before calling doDecoder.
     */

    int[] erasedOrNotToReadIndexes = getErasedOrNotToReadIndexes(inputs);

    // Prepare for adjustedDirectBufferInputsParameter and
    // adjustedDirectBufferOutputsParameter
    Object[] adjustedInputsParameter = usingDirectBuffer ?
        adjustedDirectBufferInputsParameter : adjustedByteArrayInputsParameter;
    Object[] adjustedBuffersForInput = usingDirectBuffer ?
        directBuffersForInput : byteArrayBuffersForInput;
    Object[] adjustedOutputsParameter = usingDirectBuffer ?
        adjustedDirectBufferOutputsParameter : adjustedByteArrayOutputsParameter;

    System.arraycopy(inputs, 0, adjustedInputsParameter, 0, inputs.length);
    int idx = 0, erasedIdx;
    for (int i = 0; i < erasedOrNotToReadIndexes.length; i++) {
      // Borrow it from byteArrayBuffersForInput for the temp usage.
      erasedIdx = erasedOrNotToReadIndexes[i];
      adjustedInputsParameter[erasedIdx] =
          adjustedBuffersForInput[idx++];
    }

    idx = 0;
    for (int i = 0; i < erasedIndexes.length; i++) {
      for (int j = 0; j < erasedOrNotToReadIndexes.length; j++) {
        // If this index is one requested by the caller via erasedIndexes, then
        // we use the passed output buffer to avoid copying data thereafter.
        if (erasedIndexes[i] == erasedOrNotToReadIndexes[j]) {
          adjustedOutputsParameter[j] = outputs[idx++];
        }
      }
    }

    if (usingDirectBuffer) {
      doDecode(adjustedDirectBufferInputsParameter, erasedOrNotToReadIndexes,
          adjustedDirectBufferOutputsParameter);
    } else {
      doDecode(adjustedByteArrayInputsParameter, erasedOrNotToReadIndexes,
          adjustedByteArrayOutputsParameter);
    }
  }

  @Override
  protected void doDecode(byte[][] inputs, int[] erasedIndexes,
                          byte[][] outputs) {
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      RSUtil.GF.substitute(inputs, outputs[i], primitivePower[i]);
    }

    int dataLen = inputs[0].length;
    RSUtil.GF.solveVandermondeSystem(errSignature,
        outputs, erasedIndexes.length, dataLen);
  }

  private void ensureWhenUseArrayBuffers() {
    boolean isFirstTime = (adjustedByteArrayInputsParameter == null);
    if (isFirstTime) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using bytes array.
       */
      adjustedByteArrayInputsParameter =
          new byte[getNumInputUnits()][];

      int numBadUnitsAtMost = getNumParityUnits();

      adjustedByteArrayOutputsParameter =
          new byte[numBadUnitsAtMost][];

      // These are temp buffers for bad inputs.
      byteArrayBuffersForInput = new byte[numBadUnitsAtMost][];
      for (int i = 0; i < byteArrayBuffersForInput.length; ++i) {
        byteArrayBuffersForInput[i] = new byte[getChunkSize()];
      }

      // These are temp buffers for recovered outputs.
      byteArrayBuffersForOutput = new byte[numBadUnitsAtMost][];
      for (int i = 0; i < byteArrayBuffersForOutput.length; ++i) {
        byteArrayBuffersForOutput[i] = new byte[getChunkSize()];
      }
    }

    /**
     * Reset to ZERO state to clean up any dirty data from previous calls, which
     * is needed for every time running into this, using bytes array.
     */
    // Ensure only ZERO bytes are read from
    for (int i = 0; i < byteArrayBuffersForInput.length; ++i) {
      System.arraycopy(ZERO_BYTES, 0, byteArrayBuffersForInput[i],
          0, ZERO_BYTES.length);
    }

    // Ensure only ZERO bytes are there, no dirty data from previous
    for (int i = 0; i < byteArrayBuffersForOutput.length; ++i) {
      System.arraycopy(ZERO_BYTES, 0, byteArrayBuffersForOutput[i],
          0, ZERO_BYTES.length);
    }

    for (int i = 0; i < adjustedByteArrayOutputsParameter.length; i++) {
      adjustedByteArrayOutputsParameter[i] = byteArrayBuffersForOutput[i];
    }
  }

  private void ensureWhenUseDirectBuffers() {
    boolean isFirstTime = (adjustedDirectBufferInputsParameter == null);
    if (isFirstTime) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using DirectBuffer.
       */
      adjustedDirectBufferInputsParameter = new ByteBuffer[getNumInputUnits()];

      int numBadUnitsAtMost = getNumParityUnits();

      adjustedDirectBufferOutputsParameter = new ByteBuffer[numBadUnitsAtMost];

      // These are temp buffers for bad inputs.
      directBuffersForInput = new ByteBuffer[numBadUnitsAtMost];
      for (int i = 0; i < directBuffersForInput.length; i++) {
        directBuffersForInput[i] =
            ByteBuffer.allocateDirect(getChunkSize());
      }

      // These are temp buffers for recovered outputs.
      directBuffersForOutput = new ByteBuffer[numBadUnitsAtMost];
      for (int i = 0; i < directBuffersForOutput.length; i++) {
        directBuffersForOutput[i] =
            ByteBuffer.allocateDirect(getChunkSize());
      }
    }

    /**
     * Reset to ZERO state to clean up any dirty data from previous calls, which
     * is needed for every time running into this, using DirectBuffer.
     */
    // Ensure only ZERO bytes are read from
    for (int i = 0; i < directBuffersForInput.length; i++) {
      directBuffersForInput[i].clear();
      directBuffersForInput[i].put(ZERO_BYTES);
      directBuffersForInput[i].flip();
    }

    // Ensure only ZERO bytes are there, no dirty data from previous
    for (int i = 0; i < directBuffersForOutput.length; i++) {
      directBuffersForOutput[i].clear();
      directBuffersForOutput[i].put(ZERO_BYTES);
      directBuffersForOutput[i].position(0);
    }

    for (int i = 0; i < adjustedDirectBufferOutputsParameter.length; i++) {
      adjustedDirectBufferOutputsParameter[i] = directBuffersForOutput[i];
    }
  }

  @Override
  public void release() {
    adjustedByteArrayInputsParameter = null;
    byteArrayBuffersForInput = null;
    adjustedByteArrayOutputsParameter = null;
    byteArrayBuffersForOutput = null;

    adjustedDirectBufferInputsParameter = null;
    directBuffersForInput = null;
    adjustedDirectBufferOutputsParameter = null;
    directBuffersForOutput = null;
  }
}

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
 *
 * TODO: HADOOP-11871
 * currently this implementation will compute and decode not to read
 * units unnecessarily due to the underlying implementation limit in GF.
 *
 */
public class RSRawDecoder extends AbstractRawErasureDecoder {
  // To describe and calculate the needed Vandermonde matrix
  private int[] errSignature;
  private int[] primitivePower;

  /**
   * We need a set of reusable buffers either for the bytes array
   * decoding version or direct buffer decoding version. Normally not both.
   *
   * For both input and output, in addition to the valid buffers from the caller
   * passed from above, we need to provide extra buffers for the internal
   * decoding implementation. For input, the caller should provide at least
   * numDataUnits valid buffers (non-NULL); for output, the caller should 
   * provide no more than numParityUnits but at least one buffers. And the left
   * buffers will be borrowed from either bytesArrayBuffersForInput or 
   * bytesArrayBuffersForOutput, for the bytes array version.
   *
   */
  // Reused buffers for decoding with bytes arrays
  private byte[][] bytesArrayBuffers;
  private byte[][] adjustedByteArrayInputsParameter;
  private byte[][] adjustedByteArrayOutputsParameter;
  private int[] adjustedInputOffsets;
  private int[] adjustedOutputOffsets;

  // Reused buffers for decoding with direct ByteBuffers
  private ByteBuffer[] directBuffers;
  private ByteBuffer[] adjustedDirectBufferInputsParameter;
  private ByteBuffer[] adjustedDirectBufferOutputsParameter;

  @Override
  public void initialize(int numDataUnits, int numParityUnits, int chunkSize) {
    super.initialize(numDataUnits, numParityUnits, chunkSize);
    assert (getNumDataUnits() + getNumParityUnits() < RSUtil.GF.getFieldSize());

    this.errSignature = new int[numParityUnits];
    this.primitivePower = RSUtil.getPrimitivePower(numDataUnits,
        numParityUnits);
  }

  private void doDecodeImpl(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      RSUtil.GF.substitute(inputs, outputs[i], primitivePower[i]);
    }

    RSUtil.GF.solveVandermondeSystem(errSignature,
        outputs, erasedIndexes.length);
  }

  private void doDecodeImpl(byte[][] inputs, int[] inputOffsets,
                          int dataLen, int[] erasedIndexes,
                          byte[][] outputs, int[] outputOffsets) {
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      RSUtil.GF.substitute(inputs, inputOffsets, dataLen, outputs[i],
          outputOffsets[i], primitivePower[i]);
    }

    RSUtil.GF.solveVandermondeSystem(errSignature, outputs, outputOffsets,
        erasedIndexes.length, dataLen);
  }

  @Override
  protected void doDecode(byte[][] inputs, int[] inputOffsets,
                          int dataLen, int[] erasedIndexes,
                          byte[][] outputs, int[] outputOffsets) {
    ensureBytesArrayBuffers(dataLen);

    /**
     * As passed parameters are friendly to callers but not to the underlying
     * implementations, so we have to adjust them before calling doDecoder.
     */

    int[] erasedOrNotToReadIndexes = getErasedOrNotToReadIndexes(inputs);
    int bufferIdx = 0, erasedIdx;

    // Prepare for adjustedInputsParameter and adjustedInputOffsets
    System.arraycopy(inputs, 0, adjustedByteArrayInputsParameter,
        0, inputs.length);
    System.arraycopy(inputOffsets, 0, adjustedInputOffsets,
        0, inputOffsets.length);
    for (int i = 0; i < erasedOrNotToReadIndexes.length; i++) {
      // Borrow it from bytesArrayBuffersForInput for the temp usage.
      erasedIdx = erasedOrNotToReadIndexes[i];
      adjustedByteArrayInputsParameter[erasedIdx] =
          resetBuffer(bytesArrayBuffers[bufferIdx++], 0, dataLen);
      adjustedInputOffsets[erasedIdx] = 0; // Always 0 for such temp input
    }

    // Prepare for adjustedOutputsParameter
    for (int i = 0; i < adjustedByteArrayOutputsParameter.length; i++) {
      adjustedByteArrayOutputsParameter[i] =
          resetBuffer(bytesArrayBuffers[bufferIdx++], 0, dataLen);
      adjustedOutputOffsets[i] = 0; // Always 0 for such temp output
    }
    for (int outputIdx = 0, i = 0;
         i < erasedIndexes.length; i++, outputIdx++) {
      for (int j = 0; j < erasedOrNotToReadIndexes.length; j++) {
        // If this index is one requested by the caller via erasedIndexes, then
        // we use the passed output buffer to avoid copying data thereafter.
        if (erasedIndexes[i] == erasedOrNotToReadIndexes[j]) {
          adjustedByteArrayOutputsParameter[j] =
              resetBuffer(outputs[outputIdx], 0, dataLen);
          adjustedOutputOffsets[j] = outputOffsets[outputIdx];
        }
      }
    }

    doDecodeImpl(adjustedByteArrayInputsParameter, inputOffsets,
        dataLen, erasedOrNotToReadIndexes,
        adjustedByteArrayOutputsParameter, outputOffsets);
  }

  @Override
  protected void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    ByteBuffer goodInput = (ByteBuffer) findGoodInput(inputs);
    ensureDirectBuffers(goodInput.remaining());

    /**
     * As passed parameters are friendly to callers but not to the underlying
     * implementations, so we have to adjust them before calling doDecoder.
     */

    int[] erasedOrNotToReadIndexes = getErasedOrNotToReadIndexes(inputs);
    int bufferIdx = 0, erasedIdx;

    // Prepare for adjustedDirectBufferInputsParameter
    System.arraycopy(inputs, 0, adjustedDirectBufferInputsParameter,
        0, inputs.length);
    for (int i = 0; i < erasedOrNotToReadIndexes.length; i++) {
      // Borrow it from bytesArrayBuffers for the temp usage.
      erasedIdx = erasedOrNotToReadIndexes[i];
      adjustedDirectBufferInputsParameter[erasedIdx] =
          resetBuffer(directBuffers[bufferIdx++]);
    }

    // Prepare for adjustedDirectBufferOutputsParameter
    for (int i = 0; i < erasedOrNotToReadIndexes.length; i++) {
      adjustedDirectBufferOutputsParameter[i] =
          resetBuffer(directBuffers[bufferIdx++]);
    }

    for (int outputIdx = 0, i = 0; i < erasedIndexes.length; i++) {
      for (int j = 0; j < erasedOrNotToReadIndexes.length; j++) {
        // If this index is one requested by the caller via erasedIndexes, then
        // we use the passed output buffer to avoid copying data thereafter.
        if (erasedIndexes[i] == erasedOrNotToReadIndexes[j]) {
          adjustedDirectBufferOutputsParameter[j] =
              resetBuffer(outputs[outputIdx++]);
        }
      }
    }

    doDecodeImpl(adjustedDirectBufferInputsParameter,
        erasedOrNotToReadIndexes, adjustedDirectBufferOutputsParameter);
  }

  private void ensureBytesArrayBuffers(int dataLen) {
    if (bytesArrayBuffers == null || bytesArrayBuffers[0].length < dataLen) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using bytes array.
       */
      int numInputs = getNumInputUnits();
      adjustedByteArrayInputsParameter = new byte[numInputs][];
      adjustedInputOffsets = new int[numInputs];

      // Erased or not to read
      int maxInvalidUnits = getNumParityUnits();
      adjustedByteArrayOutputsParameter = new byte[maxInvalidUnits][];
      adjustedOutputOffsets = new int[maxInvalidUnits];

      // These are temp buffers for both inputs and outputs
      bytesArrayBuffers = new byte[maxInvalidUnits * 2][];
      for (int i = 0; i < bytesArrayBuffers.length; ++i) {
        bytesArrayBuffers[i] = new byte[dataLen];
      }
    }
  }

  private void ensureDirectBuffers(int dataLen) {
    if (directBuffers == null || directBuffers[0].capacity() < dataLen) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using DirectBuffer.
       */
      adjustedDirectBufferInputsParameter = new ByteBuffer[getNumInputUnits()];

      // Erased or not to read
      int maxInvalidUnits = getNumParityUnits();
      adjustedDirectBufferOutputsParameter = new ByteBuffer[maxInvalidUnits];

      // These are temp buffers for both inputs and outputs
      directBuffers = new ByteBuffer[maxInvalidUnits * 2];
      for (int i = 0; i < directBuffers.length; i++) {
        directBuffers[i] = ByteBuffer.allocateDirect(dataLen);
      }
    }
  }
}

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
   * passed from above, we need to provide extra buffers for the internal decoding
   * implementation. For input, the caller should provide at least numDataUnits
   * valid buffers (non-NULL); for output, the caller should provide no more than
   * numParityUnits but at least one  buffers. And the left buffers will be
   * borrowed from either byteArrayBuffersForInput or byteArrayBuffersForOutput.
   *
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

    this.errSignature = new int[numParityUnits];
    this.primitivePower = RSUtil.getPrimitivePower(numDataUnits,
        numParityUnits);
  }

  @Override
  public void decode(ByteBuffer[] inputs, int[] erasedIndexes,
                     ByteBuffer[] outputs) {
    if (usingDirectBuffer(inputs)) {
      decodeWith(inputs, erasedIndexes, outputs, true);
    } else {
      byte[][] newInputs = toArrays(inputs);
      byte[][] newOutputs = toArrays(outputs);
      decodeWith(newInputs, erasedIndexes, newOutputs, false);
    }
  }

  @Override
  protected void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      RSUtil.GF.substitute(inputs, outputs[i], primitivePower[i]);
    }

    RSUtil.GF.solveVandermondeSystem(errSignature,
        outputs, erasedIndexes.length);
  }

  @Override
  protected void doDecode(byte[][] inputs, int[] inputOffsets,
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
  public void decode(byte[][] inputs, int[] erasedIndexes, byte[][] outputs) {
    decodeWith(inputs, erasedIndexes, outputs, false);
  }

  private void decodeWith(Object[] inputs, int[] erasedIndexes,
                          Object[] outputs, boolean usingDirectBuffer) {
    checkParameters(inputs, erasedIndexes, outputs);

    if (usingDirectBuffer) {
      ensureDirectBuffers();
    } else {
      ensureArrayBuffers();
    }

    /**
     * As passed parameters are friendly to callers but not to the underlying
     * implementations, so we have to adjust them before calling doDecoder.
     */

    int[] erasedOrNotToReadIndexes = getErasedOrNotToReadIndexes(inputs);

    Object[] adjustedInputsParameter = usingDirectBuffer ?
        adjustedDirectBufferInputsParameter : adjustedByteArrayInputsParameter;
    Object[] buffersForInput = usingDirectBuffer ?
        directBuffersForInput : byteArrayBuffersForInput;
    Object[] adjustedOutputsParameter = usingDirectBuffer ?
        adjustedDirectBufferOutputsParameter : adjustedByteArrayOutputsParameter;
    Object[] buffersForOutput = usingDirectBuffer ?
        directBuffersForOutput : byteArrayBuffersForOutput;

    // Prepare for adjustedInputsParameter
    System.arraycopy(inputs, 0, adjustedInputsParameter, 0, inputs.length);
    int idx = 0, erasedIdx;
    for (int i = 0; i < erasedOrNotToReadIndexes.length; i++) {
      // Borrow it from byteArrayBuffersForInput for the temp usage.
      erasedIdx = erasedOrNotToReadIndexes[i];
      adjustedInputsParameter[erasedIdx] = resetBuffer(buffersForInput[idx++]);
    }

    // Prepare for adjustedOutputsParameter
    for (int i = 0; i < adjustedOutputsParameter.length; i++) {
      adjustedOutputsParameter[i] = resetBuffer(buffersForOutput[i]);
    }
    idx = 0;
    for (int i = 0; i < erasedIndexes.length; i++) {
      for (int j = 0; j < erasedOrNotToReadIndexes.length; j++) {
        // If this index is one requested by the caller via erasedIndexes, then
        // we use the passed output buffer to avoid copying data thereafter.
        if (erasedIndexes[i] == erasedOrNotToReadIndexes[j]) {
          adjustedOutputsParameter[j] = resetBuffer(outputs[idx++]);
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

    RSUtil.GF.solveVandermondeSystem(errSignature,
        outputs, erasedIndexes.length, getChunkSize());
  }

  private void ensureArrayBuffers() {
    if (adjustedByteArrayInputsParameter == null) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using bytes array.
       */
      adjustedByteArrayInputsParameter =
          new byte[getNumInputUnits()][];

      // Erased or not to read
      int maxInvalidUnits = getNumParityUnits();

      adjustedByteArrayOutputsParameter = new byte[maxInvalidUnits][];

      // These are temp buffers for bad inputs, maybe more than needed
      byteArrayBuffersForInput = new byte[maxInvalidUnits][];
      for (int i = 0; i < byteArrayBuffersForInput.length; ++i) {
        byteArrayBuffersForInput[i] = new byte[getChunkSize()];
      }

      // These are temp buffers for recovering outputs, maybe more than needed
      byteArrayBuffersForOutput = new byte[maxInvalidUnits][];
      for (int i = 0; i < byteArrayBuffersForOutput.length; ++i) {
        byteArrayBuffersForOutput[i] = new byte[getChunkSize()];
      }
    }
  }

  private void ensureDirectBuffers() {
    if (adjustedDirectBufferInputsParameter == null) {
      /**
       * Create this set of buffers on demand, which is only needed at the first
       * time running into this, using DirectBuffer.
       */
      adjustedDirectBufferInputsParameter = new ByteBuffer[getNumInputUnits()];

      // Erased or not to read
      int maxInvalidUnits = getNumParityUnits();

      adjustedDirectBufferOutputsParameter = new ByteBuffer[maxInvalidUnits];

      // These are temp buffers for invalid inputs, maybe more than needed
      directBuffersForInput = new ByteBuffer[maxInvalidUnits];
      for (int i = 0; i < directBuffersForInput.length; i++) {
        directBuffersForInput[i] = ByteBuffer.allocateDirect(getChunkSize());
      }

      // These are temp buffers for recovering outputs, maybe more than needed
      directBuffersForOutput = new ByteBuffer[maxInvalidUnits];
      for (int i = 0; i < directBuffersForOutput.length; i++) {
        directBuffersForOutput[i] =
            ByteBuffer.allocateDirect(getChunkSize());
      }
    }
  }

  private Object resetBuffer(Object buffer) {
    if (buffer instanceof byte[]) {
      byte[] arrayBuffer = (byte[]) buffer;
      resetBuffer(arrayBuffer);
    } else {
      ByteBuffer byteBuffer = (ByteBuffer) buffer;
      resetBuffer(byteBuffer);
    }

    return buffer;
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

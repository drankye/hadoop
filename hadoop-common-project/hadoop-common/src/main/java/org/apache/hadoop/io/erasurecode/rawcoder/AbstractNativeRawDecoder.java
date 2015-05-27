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

import java.nio.ByteBuffer;

public abstract class AbstractNativeRawDecoder
    extends AbstractRawErasureDecoder {

  public AbstractNativeRawDecoder(int numDataUnits, int numParityUnits) {
    super(numDataUnits, numParityUnits);
  }

  @Override
  protected void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    // When not necessary to adjust the order, then we can remove the call
    // entirely
    adjustOrder(inputs, erasedIndexes, outputs);

    int[] inputOffsets = new int[inputs.length];
    int[] outputOffsets = new int[outputs.length];
    int dataLen = inputs[0].remaining();

    ByteBuffer buffer;
    for (int i = 0; i < inputs.length; ++i) {
      buffer = inputs[i];
      inputOffsets[i] = buffer.position();
    }

    for (int i = 0; i < outputs.length; ++i) {
      buffer = outputs[i];
      outputOffsets[i] = buffer.position();
      outputs[i] = resetBuffer(buffer);
    }

    performDecodeImpl(inputs, inputOffsets, dataLen, erasedIndexes, outputs,
        outputOffsets);

    for (int i = 0; i < inputs.length; ++i) {
      buffer = inputs[i];
      buffer.position(inputOffsets[i] + dataLen); // dataLen bytes consumed
    }
  }

  /*
   * From parity units + data units to data units + parity units. Hopefully we
   * could eliminate this in future.
   */
  private void adjustOrder(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    ByteBuffer[] inputs2 = new ByteBuffer[inputs.length];
    int[] erasedIndexes2 = new int[erasedIndexes.length];
    ByteBuffer[] outputs2 = new ByteBuffer[outputs.length];
    int numDataUnits = getNumDataUnits();
    int numParityUnits = getNumParityUnits();

    // From parity units + data units to data units + parity units
    System.arraycopy(inputs, 0, inputs2, numDataUnits, numParityUnits);
    System.arraycopy(inputs, numParityUnits, inputs2,
        0, numDataUnits);

    int numErasedDataUnits = 0, numErasedParityUnits = 0;
    int idx = 0;
    for (int i = 0; i < erasedIndexes.length; i++) {
      if (erasedIndexes[i] >= numParityUnits) {
        erasedIndexes2[idx++] = erasedIndexes[i] - numParityUnits;
        numErasedDataUnits++;
      }
    }
    for (int i = 0; i < erasedIndexes.length; i++) {
      if (erasedIndexes[i] < numParityUnits) {
          erasedIndexes2[idx++] = erasedIndexes[i] + numDataUnits;
          numErasedParityUnits++;
      }
    }

    // Copy for data units
    System.arraycopy(outputs, numErasedParityUnits, outputs2,
        0, numErasedDataUnits);
    // Copy for parity units
    System.arraycopy(outputs, 0, outputs2,
        numErasedDataUnits, numErasedParityUnits);

    // Copy back with adjusted order
    System.arraycopy(inputs2, 0, inputs, 0, inputs.length);
    System.arraycopy(erasedIndexes2, 0, erasedIndexes, 0, erasedIndexes.length);
    System.arraycopy(outputs2, 0, outputs, 0, outputs.length);
  }

  protected abstract void performDecodeImpl(
          ByteBuffer[] inputs, int[] inputOffsets, int dataLen, int[] erased,
          ByteBuffer[] outputs, int[] outputOffsets);

  @Override
  protected void doDecode(byte[][] inputs, int[] inputOffsets,
                          int dataLen, int[] erasedIndexes,
                          byte[][] outputs, int[] outputOffsets) {
    doDecodeByConvertingToDirectBuffers(inputs, inputOffsets, dataLen,
            erasedIndexes, outputs, outputOffsets);
  }

  @Override
  public boolean preferDirectBuffer() {
    return true;
  }

  private long __native_coder;
  // To be utilized by HADOOP-12011
  private long __native_verbose = 1;
}

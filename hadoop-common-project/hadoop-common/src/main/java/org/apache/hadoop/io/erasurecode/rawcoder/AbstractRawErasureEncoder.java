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

/**
 * An abstract raw erasure encoder that's to be inherited by new encoders.
 *
 * It implements the {@link RawErasureEncoder} interface.
 */
public abstract class AbstractRawErasureEncoder extends AbstractRawErasureCoder
    implements RawErasureEncoder {

  @Override
  public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
    checkParameters(inputs, outputs);

    doEncode(inputs, outputs);
  }

  /**
   * Perform the real encoding work using ByteBuffer
   * @param inputs
   * @param outputs
   */
  protected abstract void doEncode(ByteBuffer[] inputs, ByteBuffer[] outputs);

  @Override
  public void encode(byte[][] inputs, byte[][] outputs) {
    checkParameters(inputs, outputs);

    doEncode(inputs, outputs);
  }

  /**
   * Perform the real encoding work using bytes array
   * @param inputs
   * @param outputs
   */
  protected abstract void doEncode(byte[][] inputs, byte[][] outputs);

  @Override
  public void encode(ECChunk[] inputs, ECChunk[] outputs) {
    assert (inputs.length == getNumDataUnits());
    assert (outputs.length == getNumParityUnits());

    /**
     * Note callers may pass byte array, or direct buffer via ECChunk according
     * to how ECChunk is created. Some implementations of coder use byte array
     * (ex: pure Java), some use direct buffer (ex: ISA-L), all for the better
     * performance.
     */
    boolean hasArray = inputs[0].getBuffer().hasArray();
    if (hasArray) {
      byte[][] inputBytesArr = ECChunk.toArrays(inputs);
      byte[][] outputBytesArr = ECChunk.toArrays(outputs);
      encode(inputBytesArr, outputBytesArr);
    } else {
      ByteBuffer[] inputBuffers = ECChunk.toBuffers(inputs);
      ByteBuffer[] outputBuffers = ECChunk.toBuffers(outputs);
      encode(inputBuffers, outputBuffers);
    }
  }

  /**
   * Perform the encoding work using bytes array, via converting to ByteBuffers.
   * Please note this may be not efficient and serves as fall-back. We should
   * avoid calling into this.
   * @param inputs
   * @param outputs
   */
  protected void doEncodeByConvertingToDirectBuffers(
      byte[][] inputs, byte[][] outputs) {
    ByteBuffer[] inputBuffers = new ByteBuffer[inputs.length];
    ByteBuffer[] outputBuffers = new ByteBuffer[outputs.length];

    for(int i = 0; i < inputs.length; ++i) {
      inputBuffers[i] = convertInputBuffer(inputs[i]);
    }

    for(int i = 0; i < outputs.length; ++i) {
      outputBuffers[i] = convertOutputBuffer(outputs[i]);
    }

    doEncode(inputBuffers, outputBuffers);
  }

  /**
   * Check and validate decoding parameters, throw exception accordingly.
   * @param inputs
   * @param outputs
   */
  protected void checkParameters(Object[] inputs, Object[] outputs) {
    if (inputs.length != getNumDataUnits()) {
      throw new IllegalArgumentException("Invalid inputs length");
    }
    if (outputs.length != getNumParityUnits()) {
      throw new IllegalArgumentException("Invalid outputs length");
    }
  }
}

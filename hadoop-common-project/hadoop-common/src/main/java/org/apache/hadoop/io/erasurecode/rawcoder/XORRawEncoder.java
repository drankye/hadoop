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

/**
 * A raw encoder in XOR code scheme in pure Java, adapted from HDFS-RAID.
 */
public class XORRawEncoder extends AbstractRawErasureEncoder {

  protected void doEncode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
    ByteBuffer output = outputs[0];
    resetOutputBuffer(output);

    int dataLen = inputs[0].remaining();

    // Get the first buffer's data.
    int iIdx, oIdx, iPos, oPos;
    iPos = inputs[0].position();
    oPos = output.position();
    for (iIdx = iPos, oIdx = oPos;
         iIdx < iPos + dataLen; iIdx++, oIdx++) {
      output.put(oIdx, inputs[0].get(iIdx));
    }

    // XOR with everything else.
    for (int i = 1; i < inputs.length; i++) {
      iPos = inputs[i].position();
      for (iIdx = iPos, oIdx = oPos;
           iIdx < iPos + dataLen; iIdx++, oIdx++) {
        output.put(oIdx, (byte) (output.get(oIdx) ^ inputs[i].get(iIdx)));
      }
    }
  }

  @Override
  protected void doEncode(byte[][] inputs, int[] inputOffsets, int dataLen,
                          byte[][] outputs, int[] outputOffsets) {
    byte[] output = outputs[0];
    resetBuffer(output, outputOffsets[0], dataLen);

    // Get the first buffer's data.
    int iPos, iIdx, oPos, oIdx;
    iPos = inputOffsets[0];
    oPos = outputOffsets[0];
    for (iIdx = iPos, oIdx = oPos;
         iIdx < iPos + dataLen; iIdx++, oIdx++) {
      output[oIdx] = inputs[0][iIdx];
    }

    // XOR with everything else.
    for (int i = 1; i < inputs.length; i++) {
      iPos = inputOffsets[i];
      for (iIdx = iPos, oIdx = oPos;
           iIdx < iPos + dataLen; iIdx++, oIdx++) {
        output[oIdx] ^= inputs[i][iIdx];
      }
    }
  }
}

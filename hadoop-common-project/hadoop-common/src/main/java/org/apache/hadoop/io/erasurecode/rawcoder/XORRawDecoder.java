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
 * A raw decoder in XOR code scheme in pure Java, adapted from HDFS-RAID.
 */
public class XORRawDecoder extends AbstractRawErasureDecoder {

  @Override
  protected void doDecode(ByteBuffer[] inputs, int[] erasedIndexes,
                          ByteBuffer[] outputs) {
    ByteBuffer output = outputs[0];
    resetOutputBuffer(output);

    int dataLen = inputs[0].remaining();
    int erasedIdx = erasedIndexes[0];

    // Process the inputs.
    int iPos, oPos, iIdx, oIdx;
    oPos = output.position();
    for (int i = 0; i < inputs.length; i++) {
      // Skip the erased location.
      if (i == erasedIdx) {
        continue;
      }

      iPos = inputs[i].position();
      for (iIdx = iPos, oIdx = oPos;
           iIdx < iPos + dataLen; iIdx++, oIdx++) {
        output.put(oIdx, (byte) (output.get(oIdx) ^ inputs[i].get(iIdx)));
      }
    }
  }

  @Override
  protected void doDecode(byte[][] inputs, int[] inputOffsets, int dataLen,
                          int[] erasedIndexes, byte[][] outputs,
                          int[] outputOffsets) {
    byte[] output = outputs[0];
    resetBuffer(output, outputOffsets[0], dataLen);

    int erasedIdx = erasedIndexes[0];

    // Process the inputs.
    int iPos, iIdx, oPos, oIdx;
    oPos = outputOffsets[0];
    for (int i = 0; i < inputs.length; i++) {
      // Skip the erased location.
      if (i == erasedIdx) {
        continue;
      }

      iPos = inputOffsets[i];
      for (iIdx = iPos, oIdx = oPos;
           iIdx < iPos + dataLen; iIdx++, oIdx++) {
        output[oIdx] ^= inputs[i][iIdx];
      }
    }
  }

}

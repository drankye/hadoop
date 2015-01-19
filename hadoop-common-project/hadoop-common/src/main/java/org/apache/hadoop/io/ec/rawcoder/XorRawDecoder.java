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

import java.nio.ByteBuffer;

/**
 * Ported from HDFS-RAID
 */
public class XorRawDecoder extends AbstractRawErasureDecoder{

  public XorRawDecoder(int dataSize, int chunkSize) {
    super(dataSize, 1, chunkSize);
  }

  //inputs = data + parity
  @Override
  public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
    assert(erasedIndexes.length == outputs.length);
    assert(erasedIndexes.length <= 1);

    byte[][] inputData = getData(inputs);
    assert(inputData.length > 0);
    byte[][] outputData = new byte[outputs.length][];
    outputData[0] = new byte[inputData[0].length];
    int erasedIdx = erasedIndexes[0];
    // Set the output to zeros.
    for (int j = 0; j < outputData[0].length; j++) {
      outputData[0][j] = 0;
    }
    // Process the inputs.
    for (int i = 0; i < inputData.length; i++) {
      // Skip the erased location.
      if (i == erasedIdx) {
        continue;
      }
      byte[] input = inputData[i];
      for (int j = 0; j < input.length; j++) {
        outputData[0][j] ^= input[j];
      }
    }
    writeBuffer(outputs, outputData);
  }
}

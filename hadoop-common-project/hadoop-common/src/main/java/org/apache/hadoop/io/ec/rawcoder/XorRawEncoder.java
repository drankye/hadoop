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
public class XorRawEncoder extends AbstractRawErasureEncoder{

  public XorRawEncoder(int dataSize, int chunkSize) {
    super(dataSize, 1, chunkSize);
  }

  @Override
  public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
    byte[][] inputData = getData(inputs);
    assert(inputData.length > 0);
    int bufSize = inputData[0].length;

    byte[][] outputData = new byte[outputs.length][];
    outputData[0] = new byte[bufSize];
    // Get the first buffer's data.
    for (int j = 0; j < bufSize; j++) {
      outputData[0][j] = inputData[0][j];
    }
    // XOR with everything else.
    for (int i = 1; i < inputs.length; i++) {
      for (int j = 0; j < bufSize; j++) {
        outputData[0][j] ^= inputData[i][j];
      }
    }
    writeBuffer(outputs, outputData);
  }
}

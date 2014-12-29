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
package org.apache.hadoop.hdfs.ec.rawcoder;

import org.apache.hadoop.hdfs.ec.rawcoder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.rawcoder.util.RSUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class JavaRSRawDecoder extends AbstractRawErasureDecoder {
  private GaloisField GF = GaloisField.getInstance();

  private int PRIMITIVE_ROOT = 2;
  private int[] errSignature;
  private int[] primitivePower;

  public JavaRSRawDecoder(int dataSize, int paritySize, int chunkSize) {
    super(dataSize, paritySize, chunkSize);
    init();
  }

  private void init() {
    assert (dataSize() + paritySize() < GF.getFieldSize());
    this.errSignature = new int[paritySize()];
    this.primitivePower = RSUtil.getPrimitivePower(dataSize(), paritySize());
  }

  @Override
  public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
    if (erasedIndexes.length == 0) {
      return;
    }

    byte[][] outputsData = new byte[outputs.length][outputs[0].limit()];
    // cleanup the write buffer
    for (int i = 0; i < outputsData.length; i++) {
      Arrays.fill(outputsData[i], (byte) 0);
    }

    byte[][] inputsData = getData(inputs);
    for (int i = 0; i < erasedIndexes.length; i++) {
      errSignature[i] = primitivePower[erasedIndexes[i]];
      GF.substitute(inputsData, outputsData[i], primitivePower[i]);
    }

    GF.solveVandermondeSystem(errSignature, outputsData,
        erasedIndexes.length, inputsData[0].length);

    writeBuffer(outputs, outputsData);
  }
}

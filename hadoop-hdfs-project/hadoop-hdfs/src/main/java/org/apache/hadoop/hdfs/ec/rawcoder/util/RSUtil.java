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
package org.apache.hadoop.hdfs.ec.rawcoder.util;

import org.apache.hadoop.hdfs.ec.rawcoder.code.ReedSolomonCode;

public class RSUtil {

  private static final int PRIMITIVE_ROOT = 2;
  private static GaloisField GF = GaloisField.getInstance();

  public static int[] getPrimitivePower(int dataSize, int paritySize) {
    int[] primitivePower = new int[dataSize + paritySize];
    // compute powers of the primitive root
    for (int i = 0; i < dataSize + paritySize; i++) {
      primitivePower[i] = GF.power(PRIMITIVE_ROOT, i);
    }
    return primitivePower;
  }

  public static int[] initMatrix(int dataSize, int paritySize) {
    int[] matrix = new int[dataSize  * paritySize];

    ReedSolomonCode rs = new ReedSolomonCode(dataSize,paritySize);
    int[] code = new int[paritySize];
    for(int i = 0; i < dataSize; i++) {
      int[] data = new int[dataSize];
      data[i] = 1;
      rs.encode(data, code);
      for(int j = 0; j < code.length; j++) {
        matrix[i + j*dataSize] = code[j];
      }
    }

    return matrix;
  }
}

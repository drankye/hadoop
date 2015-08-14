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
package org.apache.hadoop.io.erasurecode.rawcoder.util;

import java.nio.ByteBuffer;

/**
 * An erasure code util class.
 */
public final class ErasureCodeUtil {

  private ErasureCodeUtil() { }

  public static void initTables(int k, int rows, byte[] codingMatrix,
                                int matrixOffset, byte[] gftbls) {
    int i, j;

    int offset = 0, idx = matrixOffset;
    for (i = 0; i < rows; i++) {
      for (j = 0; j < k; j++) {
        GF256.gfVectMulInit(codingMatrix[idx++], gftbls, offset);
        offset += 32;
      }
    }
  }

  public static void genRSMatrix(byte[] a, int m, int k) {
    int i, j;
    byte p, gen = 1;

    for (i = 0; i < k; i++) {
      a[k * i + i] = 1;
    }

    for (i = k; i < m; i++) {
      p = 1;
      for (j = 0; j < k; j++) {
        a[k * i + j] = p;
        p = GF256.gfMul(p, gen);
      }
      gen = GF256.gfMul(gen, (byte) 0x10);
    }
  }

  public static void genCauchyMatrix(byte[] a, int m, int k) {
    int i, j;
    byte[] p;

    // Identity matrix in high position
    for (i = 0; i < k; i++) {
      a[k * i + i] = 1;
    }

    // For the rest choose 1/(i + j) | i != j
    int pos = k * k;
    for (i = k; i < m; i++) {
      for (j = 0; j < k; j++) {
        a[pos++] = GF256.gfInv((byte) (i ^ j));
      }
    }
  }

  /**
   * Generate Cauchy matrix.
   * @param matrix
   * @param numParityUnits
   * @param numDataUnits
   */
  public static void genCauchyMatrix_JE(byte[] matrix,
                                        int numDataUnits, int numParityUnits) {
    for (int i = 0; i < numParityUnits; i++) {
      for (int j = 0; j < numDataUnits; j++) {
        matrix[i * numDataUnits + j] =
                GF256.gfInv((byte) (i ^ (numParityUnits + j)));
      }
    }
  }

  public static void encodeData(byte[] gftbls, int dataLen, byte[][] inputs,
                                int[] inputOffsets, byte[][] outputs,
                                int[] outputOffsets) {
    int numInputs = inputs.length;
    int numOutputs = outputs.length;
    int l, i, j, iPos, oPos;
    byte[] input, output;
    byte s;
    final int times = dataLen / 8;
    final int extra = dataLen - dataLen % 8;
    byte[] tableLine;

    for (l = 0; l < numOutputs; l++) {
      output = outputs[l];

      for (j = 0; j < numInputs; j++) {
        input = inputs[j];
        iPos = inputOffsets[j];
        oPos = outputOffsets[l];

        s = gftbls[j * 32 + l * numInputs * 32 + 1];
        tableLine = GF256.gfMulTab[s & 0xff];

        for (i = 0; i < times; i++, iPos += 8, oPos += 8) {
          output[oPos + 0] ^= tableLine[0xff & input[iPos + 0]];
          output[oPos + 1] ^= tableLine[0xff & input[iPos + 1]];
          output[oPos + 2] ^= tableLine[0xff & input[iPos + 2]];
          output[oPos + 3] ^= tableLine[0xff & input[iPos + 3]];
          output[oPos + 4] ^= tableLine[0xff & input[iPos + 4]];
          output[oPos + 5] ^= tableLine[0xff & input[iPos + 5]];
          output[oPos + 6] ^= tableLine[0xff & input[iPos + 6]];
          output[oPos + 7] ^= tableLine[0xff & input[iPos + 7]];
        }

        for (i = extra; i < dataLen; i++, iPos++, oPos++) {
          output[oPos] ^= tableLine[0xff & input[iPos]];
        }
      }
    }
  }

  public static void encodeData(byte[] gftbls, ByteBuffer[] inputs,
                                ByteBuffer[] outputs) {
    int numInputs = inputs.length;
    int numOutputs = outputs.length;
    int dataLen = inputs[0].remaining();
    int l, i, j, iPos, oPos;
    ByteBuffer input, output;
    byte s;
    final int times = dataLen / 8;
    final int extra = dataLen - dataLen % 8;
    byte[] tableLine;

    for (l = 0; l < numOutputs; l++) {
      output = outputs[l];

      for (j = 0; j < numInputs; j++) {
        input = inputs[j];
        iPos = input.position();
        oPos = output.position();

        s = gftbls[j * 32 + l * numInputs * 32 + 1];
        tableLine = GF256.gfMulTab[s & 0xff];

        for (i = 0; i < times; i++, iPos += 8, oPos += 8) {
          output.put(oPos + 0, (byte) (output.get(oPos + 0) ^ tableLine[0xff & input.get(iPos + 0)]));
          output.put(oPos + 1, (byte) (output.get(oPos + 1) ^ tableLine[0xff & input.get(iPos + 1)]));
          output.put(oPos + 2, (byte) (output.get(oPos + 2) ^ tableLine[0xff & input.get(iPos + 2)]));
          output.put(oPos + 3, (byte) (output.get(oPos + 3) ^ tableLine[0xff & input.get(iPos + 3)]));
          output.put(oPos + 4, (byte) (output.get(oPos + 4) ^ tableLine[0xff & input.get(iPos + 4)]));
          output.put(oPos + 5, (byte) (output.get(oPos + 5) ^ tableLine[0xff & input.get(iPos + 5)]));
          output.put(oPos + 6, (byte) (output.get(oPos + 6) ^ tableLine[0xff & input.get(iPos + 6)]));
          output.put(oPos + 7, (byte) (output.get(oPos + 7) ^ tableLine[0xff & input.get(iPos + 7)]));
        }

        for (i = extra; i < dataLen; i++, iPos++, oPos++) {
          output.put(oPos, (byte) (output.get(oPos) ^
              tableLine[0xff & input.get(iPos)]));
        }
      }
    }
  }

  public static void encodeDotprod(byte[] matrix, int matrixOffset, byte[][] inputs,
                                   int[] inputOffsets, int dataLen,
                                   byte[] output, int outputOffset) {
    //First copy or xor any data that does not need to be multiplied by a factor
    boolean init = true;
    for (int i = 0; i < inputs.length; i++) {
      if (matrix[matrixOffset + i] == 1) {
        if (init) {
          System.arraycopy(inputs[i], inputOffsets[i], output, 0, dataLen);
          init = false;
        } else {
          for (int j = 0; j < dataLen; j++) {
            output[outputOffset + j] ^= inputs[i][inputOffsets[i] + j];
          }
        }
      }
    }

    //Now do the data that needs to be multiplied by a factor
    for (int i = 0; i < inputs.length; i++) {
      if (matrix[matrixOffset + i] != 0 && matrix[matrixOffset + i] != 1) {
        regionMultiply(inputs[i], inputOffsets[i], dataLen, matrix[matrixOffset + i],
            output, outputOffset, init);
        init = false;
      }
    }
  }

  public static void encodeDotprod(byte[] matrix, int matrixOffset,
                                   ByteBuffer[] inputs, ByteBuffer output) {
    ByteBuffer input;
    int iPos;
    int oPos = output.position();

    //First copy or xor any data that does not need to be multiplied by a factor
    boolean init = true;
    for (int i = 0; i < inputs.length; i++) {
      if (matrix[matrixOffset + i] == 1) {
        input = inputs[i];
        iPos = input.position();
        if (init) {
          output.put(input);
          output.position(oPos);
          init = false;
        } else {
          for (int j = 0; j < input.remaining(); j++) {
            output.put(oPos + j,
                (byte) ((output.get(oPos + j) ^ input.get(iPos + j)) & 0xff));
          }
        }
      }
    }

    //Now do the data that needs to be multiplied by a factor
    for (int i = 0; i < inputs.length; i++) {
      if (matrix[matrixOffset + i] != 0 && matrix[matrixOffset + i] != 1) {
        input = inputs[i];
        regionMultiply(input, matrix[matrixOffset + i], output, init);
        init = false;
      }
    }
  }

  /*
  public static void decodeDotprod(int numDataUnits, byte[] matrix, int matrixOffset,
                                   int[] validIndexes, byte[][] inputs, byte[] output) {
    byte[] input;
    int size = 16; //inputs[0].length;
    int i;

    //First copy or xor any data that does not need to be multiplied by a factor
    boolean init = true;
    for (i = 0; i < numDataUnits; i++) {
      if (matrix[matrixOffset + i] == 1) {
        input = inputs[validIndexes[i]];
        if (init) {
          System.arraycopy(input, 0, output, 0, size);
          init = false;
        } else {
          for (int j = 0; j < size; j++) {
            output[j] ^= input[j];
          }
        }
      }
    }

    //Now do the data that needs to be multiplied by a factor
    for (i = 0; i < numDataUnits; i++) {
      if (matrix[matrixOffset + i] != 0 && matrix[matrixOffset + i] != 1) {
        input = inputs[validIndexes[i]];
        regionMultiply(input, matrix[matrixOffset + i], output, init);
        init = false;
      }
    }
  }*/

  public static void regionMultiply(byte[] input, int inputOffset, int dataLen,
                                    byte multiply, byte[] output, int outputOffset,
                                    boolean init) {
    if (init) {
      for (int i = 0; i < dataLen; i++) {
        output[outputOffset + i] = GF256.gfMul(input[inputOffset + i], multiply);
      }
    } else {
      for (int i = 0; i < dataLen; i++) {
        byte tmp = GF256.gfMul(input[inputOffset + i], multiply);
        output[outputOffset + i] = (byte) ((output[outputOffset + i] ^ tmp) & 0xff);
      }
    }
  }

  public static void regionMultiply(ByteBuffer input, byte multiply,
                                    ByteBuffer output, boolean init) {
    int iPos = input.position();
    int oPos = output.position();
    if (init) {
      for (int i = 0; i < input.remaining(); i++) {
        output.put(oPos + i, GF256.gfMul(input.get(iPos + i), multiply));
      }
    } else {
      for (int i = 0; i < input.remaining(); i++) {
        byte tmp = GF256.gfMul(input.get(iPos + i), multiply);
        try {
          output.put(oPos + i, (byte) ((output.get(oPos + i) ^ tmp) & 0xff));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}

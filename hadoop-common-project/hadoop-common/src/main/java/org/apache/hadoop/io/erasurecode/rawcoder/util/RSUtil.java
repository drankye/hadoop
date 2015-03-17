package org.apache.hadoop.io.erasurecode.rawcoder.util;

import org.apache.hadoop.io.erasurecode.rawcoder.code.ReedSolomonCode;

/**
 * Some utilities for Reed-Solomon coding.
 */
public class RSUtil {

  // We always use the byte system (with symbol size 8, field size 256,
  // primitive polynomial 285, and primitive root 2).
  public static GaloisField GF = GaloisField.getInstance();
  public static final int PRIMITIVE_ROOT = 2;

  public static int[] getPrimitivePower(int numDataUnits, int numParityUnits) {
    int[] primitivePower = new int[numDataUnits + numParityUnits];
    // compute powers of the primitive root
    for (int i = 0; i < numDataUnits + numParityUnits; i++) {
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

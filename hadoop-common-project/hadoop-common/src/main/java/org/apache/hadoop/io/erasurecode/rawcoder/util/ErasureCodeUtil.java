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

/**
 * An erasure code util class.
 */
public final class ErasureCodeUtil {

  private ErasureCodeUtil() { }

  public static void initTables(int k, int rows, byte[] a, byte[] gftbls) {
    int i, j;

    for (i = 0; i < rows; i++) {
      for (j = 0; j < k; j++) {
        //GaloisFieldUtil.gfVectMulInit(*a++, gftbls);
        //gftbls += 32;
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
        p = GaloisFieldUtil.gfMul(p, gen);
      }
      gen = GaloisFieldUtil.gfMul(gen, (byte) 0x10);
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
        a[pos++] = GaloisFieldUtil.gfInv((byte) (i ^ j));
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
            GaloisFieldUtil.gfInv((byte) (i ^ (numParityUnits + j)));
      }
    }
  }

  public static void encodeData(int numDataUnits, int numParityUnits, byte[] matrix,
                                byte[][] inputs, byte[][] outputs) {
    for (int i = 0; i < numParityUnits; i++) {
      dotprod(numDataUnits, matrix, i * numDataUnits, null, numDataUnits + i, inputs, outputs);
    }
  }

  public static void dotprod(int k, byte[] matrix, int rowIdx, int[] srcIds,
                             int destId, byte[][] inputs, byte[][] outputs) {
    byte[] dptr, sptr;
    int size = inputs[0].length;
    int i;

    dptr = (destId < k) ? inputs[destId] : outputs[destId - k];

    //First copy or xor any data that does not need to be multiplied by a factor
    int init = 0;
    for (i = 0; i < k; i++) {
      if (matrix[rowIdx + i] == 1) {
        if (srcIds == null) {
          sptr = inputs[i];
        } else if (srcIds[i] < k) {
          sptr = inputs[srcIds[i]];
        } else {
          sptr = outputs[srcIds[i]-k];
        }
        if (init == 0) {
          System.arraycopy(sptr, 0, dptr, 0, size);
          init = 1;
        } else {
          for (int j = 0; j < size; j++) {
            dptr[j] ^= sptr[j];
          }
        }
      }
    }

    //Now do the data that needs to be multiplied by a factor
    for (i = 0; i < k; i++) {
      if (matrix[rowIdx + i] != 0 && matrix[rowIdx + i] != 1) {
        if (srcIds == null) {
          sptr = inputs[i];
        } else if (srcIds[i] < k) {
          sptr = inputs[srcIds[i]];
        } else {
          sptr = outputs[srcIds[i]-k];
        }
        regionMultiply(sptr, matrix[rowIdx + i], size, dptr, init);
        init = 1;
      }
    }
  }

  private static int[] nw = { 0, (1 << 1), (1 << 2), (1 << 3), (1 << 4),
      (1 << 5), (1 << 6), (1 << 7), (1 << 8), (1 << 9), (1 << 10),
      (1 << 11), (1 << 12), (1 << 13), (1 << 14), (1 << 15), (1 << 16),
      (1 << 17), (1 << 18), (1 << 19), (1 << 20), (1 << 21), (1 << 22),
      (1 << 23), (1 << 24), (1 << 25), (1 << 26), (1 << 27), (1 << 28),
      (1 << 29), (1 << 30), (1 << 31), -1 };

  public static void regionMultiply(byte[] region, int multby,
                                    int nbytes, byte[] r2, int add) {
    byte[] ur1, ur2;
    int i, j;
    long l;
    int sol;

    ur1 = region;
    ur2 = (r2 == null) ? ur1 : r2;

    if (r2 == null || add == 0) {
      for (i = 0; i < nbytes; i++) ur2[i] = GaloisFieldUtil.gfMul(ur1[i], (byte) multby);
    } else {
      for (i = 0; i < nbytes; i++) {
        ur2[i] = (byte) (((ur2[i] ^ GaloisFieldUtil.gfMul(ur1[i], (byte) multby))) & 0xff);
      }
    }
  }

  public static void decodeData(int k, int m, byte[] matrix, int row_k_ones, int[] erasures,
                               byte[][] data_ptrs, byte[][] coding_ptrs) {
    int i, edd, lastdrive;
    int[] tmpids;
    boolean[] erased;
    int[] dmIds;

    erased = erasures2erased(k, m, erasures);

    //Find the number of data drives failed

    lastdrive = k;

    edd = 0;
    for (i = 0; i < k; i++) {
      if (erased[i]) {
        edd++;
        lastdrive = i;
      }
    }

  /* You only need to create the decoding matrix in the following cases:

      1. edd > 0 and row_k_ones is false.
      2. edd > 0 and row_k_ones is true and coding device 0 has been erased.
      3. edd > 1

      We're going to use lastdrive to denote when to stop decoding data.
      At this point in the code, it is equal to the last erased data device.
      However, if we can't use the parity row to decode it (i.e. row_k_ones=0
         or erased[k] = 1, we're going to set it to k so that the decoding
         pass will decode all data.
   */

    if (row_k_ones == 0 || erased[k]) {
      lastdrive = k;
    }

    dmIds = null;
    byte[] decodingMatrix = null;

    if (edd > 1 || (edd > 0 && (row_k_ones ==0 || erased[k]))) {
      dmIds = new int[k];
      decodingMatrix = new byte[k * k];
      makeDecodingMatrix(k, m, matrix, erased, decodingMatrix, dmIds);
    }

    /*
    Decode the data drives.
     If row_k_ones is true and coding device 0 is intact, then only decode edd-1 drives.
     This is done by stopping at lastdrive.
     We test whether edd > 0 so that we can exit the loop early if we're done.
   */

    for (i = 0; edd > 0 && i < lastdrive; i++) {
      if (erased[i]) {
        dotprod(k, decodingMatrix, i * k, dmIds, i, data_ptrs, coding_ptrs);
        edd--;
      }
    }

    // Then if necessary, decode drive lastdrive

    if (edd > 0) {
      tmpids = new int[k];
      for (i = 0; i < k; i++) {
        tmpids[i] = (i < lastdrive) ? i : i+1;
      }
      dotprod(k, matrix, 0, tmpids, lastdrive, data_ptrs, coding_ptrs);
    }

    // Finally, re-encode any erased coding devices

    for (i = 0; i < m; i++) {
      if (erased[k+i]) {
        dotprod(k, matrix, i * k, null, i + k, data_ptrs, coding_ptrs);
      }
    }
  }

  private static boolean[] erasures2erased(int k, int m, int[] erasures) {
    int td;
    boolean[] erased;
    int i;

    td = k+m;
    erased = new boolean[td];

    for (i = 0; i < td; i++) {
      erased[i] = false;
    }

    for (i = 0; i < erasures.length; i++) {
      erased[erasures[i]] = true;
    }

    return erased;
  }

  public static int makeDecodingMatrix(int k, int m, byte[] matrix,
                                       boolean[] erased, byte[] decodingMatrix,
                                       int[] dmIds) {
    int i, j;
    j = 0;
    for (i = 0; j < k; i++) {
      if (!erased[i]) {
        dmIds[j] = i;
        j++;
      }
    }

    byte[] tmpmat = new byte[k * k];
    if (tmpmat == null) {
      return -1;
    }

    for (i = 0; i < k; i++) {
      if (dmIds[i] < k) {
        for (j = 0; j < k; j++) {
          tmpmat[i * k + j] = 0;
        }
        tmpmat[i * k + dmIds[i]] = 1;
      } else {
        for (j = 0; j < k; j++) {
          tmpmat[i * k + j] = matrix[(dmIds[i] - k) * k + j];
        }
      }
    }

    i = GaloisFieldUtil.gfInvertMatrix(tmpmat, decodingMatrix, k);
    return i;
  }
}

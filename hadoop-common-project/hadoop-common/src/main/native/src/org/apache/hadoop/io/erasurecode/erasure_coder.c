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

#include "erasure_code.h"
#include "gf_util.h"
#include "erasure_coder.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int inited = 0;
static unsigned char* mulTab[256];

static void initTab() {
  int i, j;
  if (inited == 1) {
    return;
  }

  inited = 1;

  for (i = 0; i < 256; i++) {
    mulTab[i] = (unsigned char*)malloc(256);
    for (j = 0; j < 256; j++) {
      mulTab[i][j] = h_gf_mul(i, j);
    }
  }
}

static void ec_encode_data(int len, int srcs, int dests, unsigned char *gftbls,
             unsigned char **src, unsigned char **dest)
{
    int i, j, l, iPos, oPos;
    unsigned char s;
    unsigned char *pdest, *psrc, *tab;

    for (l = 0; l < dests; l++) {
      pdest = dest[l];

      for (j = 0; j < srcs; j++) {
        psrc = src[j];
        iPos = oPos = 0;

        s = gftbls[j * 32 + l * srcs * 32 + 1];
        tab = mulTab[s];

        for (i = 0; i < len; i++, iPos++, oPos++) {
          pdest[oPos] ^= tab[psrc[iPos]];
        }
      }
    }

    /*
    for (l = 0; l < dests; l++) {
        for (i = 0; i < len; i++) {
            s = 0;
            for (j = 0; j < srcs; j++)
                s ^= gf_mul(src[j][i], v[j * 32 + l * srcs * 32 + 1]);

            dest[l][i] = s;
        }
    }*/
}

void initCoder(CoderState* pCoderState, int numDataUnits, int numParityUnits) {
  initTab();

  pCoderState->verbose = 0;
  pCoderState->numParityUnits = numParityUnits;
  pCoderState->numDataUnits = numDataUnits;
  pCoderState->numAllUnits = numDataUnits + numParityUnits;
}

// 0 not to verbose, 1 to verbose
void allowVerbose(CoderState* pCoderState, int flag) {
  pCoderState->verbose = flag;
}

static void initEncodeMatrix(int numDataUnits, int numParityUnits,
                                                unsigned char* encodeMatrix) {
  // Generate encode matrix, always invertible
  h_gf_gen_cauchy_matrix(encodeMatrix,
                          numDataUnits + numParityUnits, numDataUnits);
}

void initEncoder(EncoderState* pCoderState, int numDataUnits,
                            int numParityUnits) {
  initCoder((CoderState*)pCoderState, numDataUnits, numParityUnits);

  initEncodeMatrix(numDataUnits, numParityUnits, pCoderState->encodeMatrix);

  // Generate gftbls from encode matrix
  h_ec_init_tables(numDataUnits, numParityUnits,
               &pCoderState->encodeMatrix[numDataUnits * numDataUnits],
               pCoderState->gftbls);

  if (((CoderState*)pCoderState)->verbose > 0) {
    dumpEncoder(pCoderState);
  }
}

void initDecoder(DecoderState* pCoderState, int numDataUnits,
                                  int numParityUnits) {
  initCoder((CoderState*)pCoderState, numDataUnits, numParityUnits);

  initEncodeMatrix(numDataUnits, numParityUnits, pCoderState->encodeMatrix);
}

int encode(EncoderState* pCoderState, unsigned char** dataUnits,
    unsigned char** parityUnits, int chunkSize) {
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numParityUnits;

  ec_encode_data(chunkSize, numDataUnits, numParityUnits,
                         pCoderState->gftbls, dataUnits, parityUnits);

  return 0;
}

// Return 1 when diff, 0 otherwise
static int compare(int* arr1, int len1, int* arr2, int len2) {
  int i;

  if (len1 == len2) {
    for (i = 0; i < len1; i++) {
      if (arr1[i] != arr2[i]) {
        return 1;
      }
    }
    return 0;
  }

  return 1;
}

static int processErasures(DecoderState* pCoderState, unsigned char** inputs,
                                    int* erasedIndexes, int numErased) {
  int i, r, ret, index;
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int isChanged = 0;

  for (i = 0, r = 0; i < numDataUnits; i++, r++) {
    while (inputs[r] == NULL) {
      r++;
    }

    if (pCoderState->decodeIndex[i] != r) {
      pCoderState->decodeIndex[i] = r;
      isChanged = 1;
    }
  }

  for (i = 0; i < numDataUnits; i++) {
    pCoderState->realInputs[i] = inputs[pCoderState->decodeIndex[i]];
  }

  if (isChanged == 0 &&
          compare(pCoderState->erasedIndexes, pCoderState->numErased,
                           erasedIndexes, numErased) == 0) {
    return 0; // Optimization, nothing to do
  }

  clearDecoder(pCoderState);

  for (i = 0; i < numErased; i++) {
    index = erasedIndexes[i];
    pCoderState->erasedIndexes[i] = index;
    pCoderState->erasureFlags[index] = 1;
    if (index < numDataUnits) {
      pCoderState->numErasedDataUnits++;
    }
  }

  pCoderState->numErased = numErased;

  ret = generateDecodeMatrix(pCoderState);
  if (ret != 0) {
    printf("Failed to generate decode matrix\n");
    return -1;
  }

  h_ec_init_tables(numDataUnits, pCoderState->numErased,
                      pCoderState->decodeMatrix, pCoderState->gftbls);

  if (((CoderState*)pCoderState)->verbose > 0) {
    dumpDecoder(pCoderState);
  }

  return 0;
}

int decode(DecoderState* pCoderState, unsigned char** inputs,
                  int* erasedIndexes, int numErased,
                   unsigned char** outputs, int chunkSize) {
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;

  processErasures(pCoderState, inputs, erasedIndexes, numErased);

  ec_encode_data(chunkSize, numDataUnits, pCoderState->numErased,
      pCoderState->gftbls, pCoderState->realInputs, outputs);

  return 0;
}

// Clear variables used per decode call
void clearDecoder(DecoderState* decoder) {
  memset(decoder->gftbls, 0, sizeof(decoder->gftbls));
  memset(decoder->decodeMatrix, 0, sizeof(decoder->decodeMatrix));
  memset(decoder->tmpMatrix, 0, sizeof(decoder->tmpMatrix));
  memset(decoder->invertMatrix, 0, sizeof(decoder->invertMatrix));
  memset(decoder->erasureFlags, 0, sizeof(decoder->erasureFlags));
  memset(decoder->erasedIndexes, 0, sizeof(decoder->erasedIndexes));
}

// Generate decode matrix from encode matrix
int generateDecodeMatrix(DecoderState* pCoderState) {
  int i, j, r, p;
  unsigned char s;
  int numDataUnits;

  numDataUnits = ((CoderState*)pCoderState)->numDataUnits;

  // Construct matrix b by removing error rows
  for (i = 0; i < numDataUnits; i++) {
    r = pCoderState->decodeIndex[i];
    for (j = 0; j < numDataUnits; j++) {
      pCoderState->tmpMatrix[numDataUnits * i + j] =
                pCoderState->encodeMatrix[numDataUnits * r + j];
    }
  }

  h_gf_invert_matrix(pCoderState->tmpMatrix,
                                pCoderState->invertMatrix, numDataUnits);

  for (i = 0; i < pCoderState->numErasedDataUnits; i++) {
    for (j = 0; j < numDataUnits; j++) {
      pCoderState->decodeMatrix[numDataUnits * i + j] =
                      pCoderState->invertMatrix[numDataUnits *
                      pCoderState->erasedIndexes[i] + j];
    }
  }

  for (p = pCoderState->numErasedDataUnits; p < pCoderState->numErased; p++) {
    for (i = 0; i < numDataUnits; i++) {
      s = 0;
      for (j = 0; j < numDataUnits; j++) {
        s ^= h_gf_mul(pCoderState->invertMatrix[j * numDataUnits + i],
          pCoderState->encodeMatrix[numDataUnits *
                                        pCoderState->erasedIndexes[p] + j]);
      }

      pCoderState->decodeMatrix[numDataUnits * p + i] = s;
    }
  }

  return 0;
}

void dumpEncoder(EncoderState* pCoderState) {
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numAllUnits = ((CoderState*)pCoderState)->numAllUnits;

  printf("Encoding (numAlnumParityUnitslUnits = %d, numDataUnits = %d)\n",
                                    numParityUnits, numDataUnits);

  printf("\n\nEncodeMatrix:\n");
  dumpCodingMatrix((unsigned char*) pCoderState->encodeMatrix,
                                           numDataUnits, numAllUnits);
}

void dumpDecoder(DecoderState* pCoderState) {
  int i, j;
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numAllUnits = ((CoderState*)pCoderState)->numAllUnits;

  printf("Recovering (numAllUnits = %d, numDataUnits = %d, numErased = %d)\n",
                       numAllUnits, numDataUnits, pCoderState->numErased);

  printf(" - ErasedIndexes = ");
  for (j = 0; j < pCoderState->numErased; j++) {
    printf(" %d", pCoderState->erasedIndexes[j]);
  }
  printf("       - DecodeIndex = ");
  for (i = 0; i < numDataUnits; i++) {
    printf(" %d", pCoderState->decodeIndex[i]);
  }

  printf("\n\nEncodeMatrix:\n");
  dumpCodingMatrix((unsigned char*) pCoderState->encodeMatrix,
                                    numDataUnits, numAllUnits);

  printf("InvertMatrix:\n");
  dumpCodingMatrix((unsigned char*) pCoderState->invertMatrix,
                                   numDataUnits, numDataUnits);

  printf("DecodeMatrix:\n");
  dumpCodingMatrix((unsigned char*) pCoderState->decodeMatrix,
                                    numDataUnits, numAllUnits);
}

void dump(unsigned char* buf, int len) {
  int i;
  for (i = 0; i < len;) {
    printf(" %2x", 0xff & buf[i++]);
    if (i % 32 == 0)
      printf("\n");
  }
}

void dumpMatrix(unsigned char** buf, int n1, int n2) {
  int i, j;
  for (i = 0; i < n1; i++) {
    for (j = 0; j < n2; j++) {
      printf(" %2x", buf[i][j]);
    }
    printf("\n");
  }
  printf("\n");
}

void dumpCodingMatrix(unsigned char* buf, int n1, int n2) {
  int i, j;
  for (i = 0; i < n1; i++) {
    for (j = 0; j < n2; j++) {
      printf(" %d", 0xff & buf[j + (i * n2)]);
    }
    printf("\n");
  }
  printf("\n");
}



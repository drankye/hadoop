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

#include "coder_common.h"
#include "erasure_code.h"
#include "gf_util.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void initCoder(CoderState* pCoderState, int isEncode, int numDataUnits, int numParityUnits) {
  pCoderState->numParityUnits = numParityUnits;
  pCoderState->numDataUnits = numDataUnits;
  pCoderState->numAllUnits = numDataUnits + numParityUnits;
  int numInputs, maxNumOutputs;
  if (isEncode) {
    numInputs = numDataUnits;
    maxNumOutputs = numParityUnits;
  } else {
    numInputs = numDataUnits + numParityUnits;
    maxNumOutputs = numParityUnits;
  }

  pCoderState->inputs = (unsigned char **)malloc(sizeof(unsigned char*) * numInputs);
  pCoderState->outputs = (unsigned char **)malloc(sizeof(unsigned char*) * maxNumOutputs);
}

EncoderState* initEncoder(int numDataUnits, int numParityUnits) {
  EncoderState* pCoderState = (EncoderState*)malloc(sizeof(EncoderState));
  initCoder((CoderState*)pCoderState, 1, numDataUnits, numParityUnits);

  // Generate encode matrix, always invertible
  h_gf_gen_cauchy1_matrix(pCoderState->encodeMatrix,
                        numDataUnits + numParityUnits, numDataUnits);

  // Generate gftbls from encode matrix
  h_ec_init_tables(numDataUnits, numParityUnits,
               &pCoderState->encodeMatrix[numDataUnits * numDataUnits],
               pCoderState->gftbls);

  return pCoderState;
}

DecoderState* initDecoder(int numDataUnits, int numParityUnits) {
  DecoderState* pCoderState = (DecoderState*)malloc(sizeof(DecoderState));
  initCoder((CoderState*)pCoderState, 1, numDataUnits, numParityUnits);

  // Generate encode matrix, always invertible
  h_gf_gen_cauchy1_matrix(pCoderState->encodeMatrix,
                        numDataUnits + numParityUnits, numDataUnits);

  return pCoderState;
}

int encode(EncoderState* pCoderState, unsigned char** dataUnits,
    unsigned char** parityUnits, int chunkSize) {

  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numParityUnits;

  h_ec_encode_data(chunkSize, numDataUnits, numParityUnits,
                         pCoderState->gftbls, dataUnits, parityUnits);

  return 0;
}

int decode(DecoderState* pCoderState, unsigned char** inputs,
                  int* erasedIndexes, int numErased,
                   unsigned char** outputs, int chunkSize) {
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numParityUnits;

  int i, idx, ret;
  memcpy(pCoderState->allUnits, inputs,
         ((CoderState*)pCoderState)->numAllUnits * (sizeof (unsigned char*)));

  // Choose random buffers to be in erasure
  processErasures(pCoderState, erasedIndexes, numErased);

  // Generate decode matrix
  ret = generateDecodeMatrix(pCoderState);
  if (ret != 0) {
    printf("Fail to gf_gen_decode_matrix\n");
    return -1;
  }

  // Pack recovery array as list of valid sources
  // Its order must be the same as the order
  // to generate matrix b in gf_gen_decode_matrix
  for (i = 0; i < numDataUnits; i++) {
    pCoderState->recover[i] = pCoderState->allUnits[pCoderState->decodeIndex[i]];
  }

  for (idx = 0, i = 0; i < pCoderState->numErased; i++) {
    pCoderState->recover[numDataUnits + i] = outputs[idx++];
  }

  // Recover data
  h_ec_init_tables(numDataUnits, pCoderState->numErased,
                      pCoderState->decodeMatrix, &pCoderState->gftbls);
  h_ec_encode_data(chunkSize, numDataUnits, pCoderState->numErased, &pCoderState->gftbls,
                               pCoderState->recover, &pCoderState->recover[numDataUnits]);

  return 0;
}

// Generate Random errors
void processErasures(DecoderState* pCoderState, int* erasedIndexes, int numErased) {
  memset(pCoderState->erasureFlags, 0, sizeof(pCoderState->erasureFlags));

  int i, index;
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  for (i = 0; i < numErased; i++) {
    index = erasedIndexes[i];
    pCoderState->erasedIndexes[i] = index;
    pCoderState->erasureFlags[index] = 1;
    if (index < numDataUnits) {
      pCoderState->numErasedDataUnits++;
    }
  }

  pCoderState->numErased = numErased;
}

// Generate decode matrix from encode matrix
int generateDecodeMatrix(DecoderState* pCoderState) {
  int i, j, r, p;
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numParityUnits;
  unsigned char* decodeMatrix;
  unsigned char s;
  int incr = 0;

  // Construct matrix b by removing error rows
  for (i = 0, r = 0; i < numDataUnits; i++, r++) {
    while (pCoderState->erasureFlags[r]) {
      r++;
    }
    for (j = 0; j < numDataUnits; j++) {
      pCoderState->b[numDataUnits * i + j] =
                pCoderState->encodeMatrix[numDataUnits * r + j];
    }
    pCoderState->decodeIndex[i] = r;
  }
  incr = 0;
  h_gf_invert_matrix(pCoderState->b, pCoderState->invertMatrix, numDataUnits);

  for (i = 0; i < pCoderState->numErasedDataUnits; i++) {
    for (j = 0; j < numDataUnits; j++) {
      pCoderState->decodeMatrix[numDataUnits * i + j] =
                      pCoderState->invertMatrix[numDataUnits *
                      pCoderState->erasedIndexes[i] + j];
    }
  }

  /* erasedIndexes from encode_matrix * invert of b for parity decoding */
  for (p = pCoderState->numErasedDataUnits; p < pCoderState->numErased; p++) {
    for (i = 0; i < numDataUnits; i++) {
      s = 0;
      for (j = 0; j < numDataUnits; j++)
        s ^= h_gf_mul(pCoderState->invertMatrix[j * numDataUnits + i],
          pCoderState->encodeMatrix[numDataUnits * pCoderState->erasedIndexes[p] + j]);

      pCoderState->decodeMatrix[numDataUnits * p + i] = s;
    }
  }

  return 0;
}

void dumpDecoder(DecoderState* pCoderState, int chunkSize) {
  int i, j;
  int numDataUnits = ((CoderState*)pCoderState)->numDataUnits;
  int numParityUnits = ((CoderState*)pCoderState)->numParityUnits;
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
  dumpU8xU8((unsigned char*) pCoderState->encodeMatrix, numDataUnits, numAllUnits);

  printf("InvertMatrix:\n");
  dumpU8xU8((unsigned char*) pCoderState->invertMatrix, numDataUnits, numDataUnits);

  printf("DecodeMatrix:\n");
  dumpU8xU8((unsigned char*) pCoderState->decodeMatrix, numDataUnits, numAllUnits);

  const int LIMIT = 32;
  int len = LIMIT >= chunkSize ? chunkSize : LIMIT;
  for (i = 0; i < pCoderState->numErased; i++) {
    printf("Recovered %d:", pCoderState->erasedIndexes[i]);
    dump(pCoderState->recover[numDataUnits + i], len);
    printf("Original   :");
    dump(pCoderState->allUnits[pCoderState->erasedIndexes[i]], len);
  }
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

void dumpU8xU8(unsigned char* buf, int n1, int n2) {
  int i, j;
  for (i = 0; i < n1; i++) {
    for (j = 0; j < n2; j++) {
      printf(" %2x", 0xff & buf[j + (i * n2)]);
    }
    printf("\n");
  }
  printf("\n");
}



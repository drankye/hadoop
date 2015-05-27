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

/**
 * This is a sample program illustrating how to use the Intel ISA-L library.
 * Note it's adapted from erasure_code_test.c test program, but trying to use
 * variable names and styles we're more familiar with already similar to Java
 * coders.
 */

#include "erasure_code.h"
#include "gf_util.h"
#include "coder_common.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(int argc, char *argv[]) {
  char err_msg[256];
  const char* error = load_erasurecode_lib(err_msg, sizeof(err_msg));
  if (error) {
    // TODO: this may indicate an severe error instead, failing the test.
    printf("loading erasurecode library failed: %s, skipping tests\n", error);
    return 0;
  }

  int i, j, k;
  int chunkSize = 1024;
  int numDataUnits = 10;
  int numParityUnits = 4;
  int numAllUnits = numDataUnits + numParityUnits;

  unsigned char* dataUnits[numDataUnits];
  unsigned char* parityUnits[numParityUnits];

  // Allocate and generate data units
  srand(135);
  for (i = 0; i < numDataUnits; i++) {
    dataUnits[i] = malloc(chunkSize);
    for (j = 0; j < chunkSize; j++) {
      dataUnits[i][j] = rand();
    }
  }

  // Allocate and initialize parity units
  for (i = 0; i < numParityUnits; i++) {
    parityUnits[i] = malloc(chunkSize);
    for (j = 0; j < chunkSize; j++) {
      parityUnits[i][j] = 0;
    }
  }

  EncoderState* pEncoder = initEncoder(numDataUnits, numParityUnits);
  encode(pEncoder, dataUnits, parityUnits, chunkSize);

  DecoderState* pDecoder = initDecoder(numDataUnits, numParityUnits);

  unsigned char* allUnits[MMAX];
  memcpy(allUnits, dataUnits, numDataUnits * (sizeof (unsigned char*)));
  memcpy(allUnits + numDataUnits, parityUnits, numParityUnits * (sizeof (unsigned char*)));

  int erasedIndexes[4];
  erasedIndexes[0] = 0;
  erasedIndexes[1] = 1;
  erasedIndexes[2] = 10;
  erasedIndexes[3] = 11;
  unsigned char** decodingOutput[4];
  decodingOutput[0] = malloc(chunkSize);
  decodingOutput[1] = malloc(chunkSize);
  decodingOutput[2] = malloc(chunkSize);
  decodingOutput[3] = malloc(chunkSize);
  decode(pDecoder, allUnits, erasedIndexes, 4, decodingOutput, chunkSize);

  for (i = 0; i < pDecoder->numErased; i++) {
    if (0 != memcmp(pDecoder->recover[numDataUnits + i],
                      pDecoder->allUnits[pDecoder->erasedIndexes[i]], chunkSize)) {
      dumpDecoder(pDecoder, chunkSize);
      return -1;
    }
  }

  dumpDecoder(pDecoder, chunkSize);
  printf("done EC tests: Pass\n");

  return 0;
}

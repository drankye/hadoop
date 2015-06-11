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

#ifndef _ERASURE_CODER_H_
#define _ERASURE_CODER_H_

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MMAX 30
#define KMAX 20

typedef struct _CoderState {
  int verbose;
  int numParityUnits;
  int numDataUnits;
  int numAllUnits;
} CoderState;

typedef struct _EncoderState {
  CoderState coderState;

  unsigned char gftbls[MMAX * KMAX * 32];

  unsigned char encodeMatrix[MMAX * KMAX];
} EncoderState;

typedef struct _DecoderState {
  CoderState coderState;

  unsigned char encodeMatrix[MMAX * KMAX];

  // Below are per decode call
  unsigned char gftbls[MMAX * KMAX * 32];
  unsigned int decodeIndex[MMAX];
  unsigned char b[MMAX * KMAX];
  unsigned char invertMatrix[MMAX * KMAX];
  unsigned char decodeMatrix[MMAX * KMAX];
  unsigned char erasureFlags[MMAX];
  int erasedIndexes[MMAX];
  int numErased;
  int numErasedDataUnits;
  unsigned char* realInputs[MMAX];
} DecoderState;

void initCoder(CoderState* pCoderState, int numDataUnits, int numParityUnits);

void allowVerbose(CoderState* pCoderState, int flag);

void initEncoder(EncoderState* encoder, int numDataUnits, int numParityUnits);

void initDecoder(DecoderState* decoder, int numDataUnits,
                                int numParityUnits, int* initialMatrix);

void clearDecoder(DecoderState* decoder);

int encode(EncoderState* pCoderState, unsigned char** dataUnits,
    unsigned char** parityUnits, int chunkSize);

int decode(DecoderState* pCoderState, unsigned char** allUnits,
    int* erasedIndexes, int numErased,
    unsigned char** recoveredUnits, int chunkSize);

void processErasures(DecoderState* pCoderState,
                              int* erasedIndexes, int numErased);

int generateDecodeMatrix(DecoderState* pCoderState);

void dumpEncoder(EncoderState* pCoderState);

void dumpDecoder(DecoderState* pCoderState);

void dump(unsigned char* buf, int len);

void dumpMatrix(unsigned char** s, int k, int m);

void dumpU8xU8(unsigned char* s, int n1, int n2);

#endif //_ERASURE_CODER_H_

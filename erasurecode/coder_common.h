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

#ifndef _CODER_COMMON_H_
#define _CODER_COMMON_H_

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MMAX 30
#define KMAX 20

typedef struct _CoderState {
  int numParityUnits;
  int numDataUnits;
  int numAllUnits;
  unsigned char** inputs;
  unsigned char** outputs;
} CoderState;

typedef struct _EncoderState {
  CoderState coderState;

  unsigned char gftbls[MMAX * KMAX * 32];
  unsigned char encodeMatrix[MMAX * KMAX];
} EncoderState;

typedef struct _DecoderState {
  CoderState coderState;

  unsigned char gftbls[MMAX * KMAX * 32];
  unsigned char* allUnits[MMAX];
  unsigned char encodeMatrix[MMAX * KMAX];
  unsigned int decodeIndex[MMAX];
  unsigned char b[MMAX * KMAX];
  unsigned char invertMatrix[MMAX * KMAX];
  unsigned char decodeMatrix[MMAX * KMAX];
  unsigned char erasureFlags[MMAX];
  int erasedIndexes[MMAX];
  int numErased;
  int numErasedDataUnits;
  unsigned char* recover[MMAX];
} DecoderState;

void initCoder(CoderState* pCoderState, int isEncode, int numDataUnits,
       int numParityUnits);

EncoderState* initEncoder(int numDataUnits, int numParityUnits);

DecoderState* initDecoder(int numDataUnits, int numParityUnits);

int encode(EncoderState* pCoderState, unsigned char** dataUnits,
    unsigned char** parityUnits, int chunkSize);

int decode(DecoderState* pCoderState, unsigned char** allUnits,
    int* erasedIndexes, int numErased,
    unsigned char** recoveredUnits, int chunkSize);

void processErasures(DecoderState* pCoderState, int* erasedIndexes, int numErased);

int generateDecodeMatrix(DecoderState* pCoderState);

void dumpDecoder(DecoderState* pCoderState, int chunkSize);

void dump(unsigned char* buf, int len);

void dumpMatrix(unsigned char** s, int k, int m);

void dumpU8xU8(unsigned char* s, int n1, int n2);

#endif //_CODER_COMMON_H

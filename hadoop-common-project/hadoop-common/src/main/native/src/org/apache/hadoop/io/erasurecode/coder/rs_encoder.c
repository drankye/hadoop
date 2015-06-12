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

#include <sys/time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <dlfcn.h>

#include "config.h"
#include "org_apache_hadoop.h"
#include "../include/erasure_code.h"
#include "../include/gf_util.h"
#include "coder_util.h"
#include "org_apache_hadoop_io_erasurecode_rawcoder_NativeRSRawEncoder.h"

typedef struct _RSEncoderState {
  EncoderState coderStates;
  unsigned char* inputs[MMAX];
  unsigned char* outputs[MMAX];
} RSEncoderState;

JNIEXPORT void JNICALL
Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeRSRawEncoder_loadLibImpl(
  JNIEnv *env, jclass myclass) {
  loadLib(env);
}

JNIEXPORT void JNICALL
Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeRSRawEncoder_initImpl(
  JNIEnv *env, jobject thiz, jint numDataUnits, jint numParityUnits) {
  RSEncoderState* pCoderState = (RSEncoderState*)malloc(sizeof(RSEncoderState));
  memset(pCoderState, 0, sizeof(*pCoderState));
  initEncoder((EncoderState*)pCoderState, (int)numDataUnits, (int)numParityUnits);

  setCoderState(env, thiz, (CoderState*)pCoderState);
}

JNIEXPORT void JNICALL
Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeRSRawEncoder_encodeImpl(
  JNIEnv *env, jobject thiz, jobjectArray inputs, jintArray inputOffsets,
    jint dataLen, jobjectArray outputs, jintArray outputOffsets) {
  RSEncoderState* rsEncoder = (RSEncoderState*)getCoderState(env, thiz);
  
  int numDataUnits = ((CoderState*)rsEncoder)->numDataUnits;
  int numParityUnits = ((CoderState*)rsEncoder)->numParityUnits;
  int chunkSize = (int)dataLen;

  getInputs(env, inputs, inputOffsets, rsEncoder->inputs, numDataUnits);
  getOutputs(env, outputs, outputOffsets, rsEncoder->outputs, numParityUnits);

  encode((EncoderState*)rsEncoder, rsEncoder->inputs,
                                              rsEncoder->outputs, chunkSize);
}

JNIEXPORT void JNICALL
Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeRSRawEncoder_destroyImpl(
  JNIEnv *env, jobject thiz) {
  RSEncoderState* rsEncoder = (RSEncoderState*)getCoderState(env, thiz);
  free(rsEncoder);
}


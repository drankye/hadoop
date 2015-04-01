/**********************************************************************
INTEL CONFIDENTIAL
Copyright 2012 Intel Corporation All Rights Reserved.

The source code contained or described herein and all documents
related to the source code ("Material") are owned by Intel Corporation
or its suppliers or licensors. Title to the Material remains with
Intel Corporation or its suppliers and licensors. The Material may
contain trade secrets and proprietary and confidential information of
Intel Corporation and its suppliers and licensors, and is protected by
worldwide copyright and trade secret laws and treaty provisions. No
part of the Material may be used, copied, reproduced, modified,
published, uploaded, posted, transmitted, distributed, or disclosed in
any way without Intel's prior express written permission.

No license under any patent, copyright, trade secret or other
intellectual property right is granted to or conferred upon you by
disclosure or delivery of the Materials, either expressly, by
implication, inducement, estoppel or otherwise. Any license under such
intellectual property rights must be express and approved by Intel in
writing.

Unless otherwise agreed by Intel in writing, you may not remove or
alter this notice or any other notice embedded in Materials by Intel
or Intel's suppliers or licensors in any way.
**********************************************************************/

#include <sys/time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>  // for memset, memcmp

#include "../include/erasure_code.h"
#include "../include/types.h"
#include "../include/gf_vect_mul.h"
#include "org_apache_hadoop.h"
#include "org_apache_hadoop_io_erasurecode_rawcoder_ISARSRawEncoder.h"
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <dlfcn.h>
#include "config.h"

#define MMAX 30
#define KMAX 20

typedef unsigned char u8;

pthread_key_t en_key;
//extern pthread_key_t de_key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;

typedef struct _codec_parameter{
    int paritySize;
    int stripeSize;
    u8 a[MMAX*KMAX];
    u8 b[MMAX*KMAX];
    u8 d[MMAX*KMAX];
    u8 g_tbls[MMAX*KMAX*32];
    u8 ** data;
    u8 ** code;
    jobject * codebuf;
}Codec_Parameter;

static void (*dlsym_ec_init_tables)(int, int, unsigned char*, unsigned char*);
static void (*dlsym_ec_encode_data)(int, int, int, unsigned char*, unsigned char*, unsigned char*);

static void make_key(){
    (void) pthread_key_create(&en_key, NULL);
    //(void) pthread_key_create(&de_key, NULL);
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_ISARSRawEncoder_loadLib
  (JNIEnv *env, jclass myclass) {
    // Load libsnappy.so
  void *libec = dlopen("liberasure_code.so", RTLD_LAZY | RTLD_GLOBAL);
  if (!libec) {
    char msg[1000];
    snprintf(msg, 1000, "%s (%s)!", "Cannot load " "liberasure_code.so", dlerror());
    THROW(env, "java/lang/UnsatisfiedLinkError", msg);
    return 0;
  }
  dlerror();                                 // Clear any existing error
  LOAD_DYNAMIC_SYMBOL(dlsym_ec_init_tables, env, libec, "ec_init_tables");
  LOAD_DYNAMIC_SYMBOL(dlsym_ec_encode_data, env, libec, "ec_encode_data");
  return 0;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_ISARSRawEncoder_init
  (JNIEnv *env, jclass myclass, jint stripeSize, jint paritySize, jintArray matrix) {
        fprintf(stdout, "[Encoder Init]before init.\n");
        Codec_Parameter * pCodecParameter = NULL;
        jint * jmatrix = NULL;
        pthread_once(&key_once, make_key);

        if(NULL == (pCodecParameter = (Codec_Parameter *)pthread_getspecific(en_key))){
            pCodecParameter = (Codec_Parameter *)malloc(sizeof(Codec_Parameter));
            if(!pCodecParameter){
                fprintf(stderr, "[Encoder Init]Out of memory in ISA encoder init\n");
                return -1;
            }

            if (stripeSize > KMAX || paritySize > (MMAX - KMAX)){
                fprintf(stderr, "[Encoder Init]max stripe size is %d and max parity size is %d\n", KMAX, MMAX - KMAX);
                return -2;
            }

            int totalSize = paritySize + stripeSize;
            pCodecParameter->paritySize = paritySize;
            pCodecParameter->stripeSize = stripeSize;
            pCodecParameter->data = (u8 **)malloc(sizeof(u8*) * stripeSize);
            pCodecParameter->code = (u8 **)malloc(sizeof(u8*) * paritySize);

            pCodecParameter->codebuf = (jobject *)malloc(sizeof(jobject) * paritySize);


           // gf_mk_field();
            //gf_gen_rs_matrix(pCodecParameter->a, totalSize, stripeSize);
            int i, j;
            jmatrix = (*env)->GetIntArrayElements(env, matrix, JNI_FALSE);
            memset(pCodecParameter->a, 0, stripeSize*totalSize);
            for(i=0; i<stripeSize; i++){
                 pCodecParameter->a[stripeSize*i + i] = 1;
            }
            for(i=stripeSize; i<totalSize; i++){
                for(j=0; j<stripeSize; j++){
                   pCodecParameter->a[stripeSize*i+j] = jmatrix[stripeSize*(i-stripeSize)+j];
           //        printf(".....=%d\n",pCodecParameter->a[stripeSize*i+j]);
                }
            }
               
            dlsym_ec_init_tables(stripeSize, paritySize, &(pCodecParameter->a)[stripeSize * stripeSize], pCodecParameter->g_tbls);
            
            (void) pthread_setspecific(en_key, pCodecParameter);
        }
        fprintf(stdout, "[Encoder Init]init success.\n");
        return 0;
  }

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_ISARSRawEncoder_encode
  (JNIEnv *env, jclass myclass, jobjectArray data, jobjectArray code, jint chunkSize){
        fprintf(stderr, "[Encoding]Before encoding...\n");
        int dataLen, codeLen;
        Codec_Parameter * pCodecParameter = NULL;
        pthread_once(&key_once, make_key);

        if(NULL == (pCodecParameter = (Codec_Parameter *)pthread_getspecific(en_key))){
           fprintf(stderr, "[Encoding]ISA encoder not initilized!\n");
            return -3;
        }
        dataLen = (*env)->GetArrayLength(env, data);
        codeLen = (*env)->GetArrayLength(env, code);

        if(dataLen != pCodecParameter->stripeSize){
            fprintf(stderr, "[Encoding]wrong stripe size, expect %d but got %d\n", pCodecParameter->stripeSize, dataLen);
            return -4;
        }
        if(codeLen != pCodecParameter->paritySize){
            fprintf(stderr, "[Encoding]wrong paritySize, expect %d but got %d\n", pCodecParameter->paritySize, codeLen);
            return -5;
        }
        int stripeSize = pCodecParameter->stripeSize;
        int paritySize = pCodecParameter->paritySize;
        int k;
        for(k = 0;k < dataLen; k++){
            jobject byteBuffer = (*env)->GetObjectArrayElement(env, data, k);
            pCodecParameter->data[k] = (u8 *)((*env)->GetDirectBufferAddress(env,byteBuffer));
        }
        int m;
        for(m = 0; m < codeLen; m++){
            pCodecParameter->codebuf[m] = (*env)->GetObjectArrayElement(env, code, m);
            pCodecParameter->code[m] = (u8 *)(*env)->GetDirectBufferAddress(env, pCodecParameter->codebuf[m]);
        }
        dlsym_ec_encode_data(chunkSize, stripeSize, paritySize, pCodecParameter->g_tbls, pCodecParameter->data, pCodecParameter->code);
        for(m = 0; m < codeLen; m++){
            (*env)->SetObjectArrayElement(env, code, m, pCodecParameter->codebuf[m]);
        }
        fprintf(stderr, "[Encoding]encode success.\n");
        return 0;
  }

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_ISARSRawEncoder_destroy
  (JNIEnv *env, jclass myclass){
        fprintf(stdout, "[Encoder destory]before destory\n");
        Codec_Parameter * pCodecParameter = NULL;
        pthread_once(&key_once, make_key);

        if(NULL == (pCodecParameter = (Codec_Parameter *)pthread_getspecific(en_key))){
            fprintf(stderr, "[Encoder destory]ISA encoder not initilized!\n");
            return 0;
        }
        
        free(pCodecParameter->data);
        free(pCodecParameter->code);
        free(pCodecParameter->codebuf);
        free(pCodecParameter);
        (void)pthread_setspecific(en_key, NULL);
        fprintf(stdout, "[Encoder destory]destory success.\n");
        return 0;
  }


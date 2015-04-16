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

#include "org_apache_hadoop.h"
#include "org_apache_hadoop_io_erasurecode_rawcoder_NativeXORRawEncoder.h"
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <dlfcn.h>
#include "config.h"


JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeXORRawEncoder_init
  (JNIEnv *env, jclass myclass, jint stripeSize, jint paritySize, jintArray matrix) {
        fprintf(stdout, "[Encoder Init]before init.\n");

        return 0;
  }

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeXORRawEncoder_encode
  (JNIEnv *env, jclass myclass, jobjectArray data, jobjectArray code, jint chunkSize){
        fprintf(stderr, "[Encoding]Before encoding...\n");

        /*
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
        */

        return 0;
  }

JNIEXPORT jint JNICALL Java_org_apache_hadoop_io_erasurecode_rawcoder_NativeXORRawEncoder_destroy
  (JNIEnv *env, jclass myclass){
        fprintf(stdout, "[Encoder destory]before destory\n");

        return 0;
  }


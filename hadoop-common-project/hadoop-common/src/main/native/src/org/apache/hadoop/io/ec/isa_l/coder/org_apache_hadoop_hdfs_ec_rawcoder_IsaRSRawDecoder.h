/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder */

#ifndef _Included_org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder
#define _Included_org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder
 * Method:    init
 * Signature: (II[I)I
 */
JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder_init
  (JNIEnv *, jclass, jint, jint, jintArray);

/*
 * Class:     org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder
 * Method:    decode
 * Signature: ([Ljava/nio/ByteBuffer;[II)I
 */
JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder_decode
  (JNIEnv *, jclass, jobjectArray, jintArray, jint);

/*
 * Class:     org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder
 * Method:    destroy
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_IsaRSRawDecoder_destroy
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif

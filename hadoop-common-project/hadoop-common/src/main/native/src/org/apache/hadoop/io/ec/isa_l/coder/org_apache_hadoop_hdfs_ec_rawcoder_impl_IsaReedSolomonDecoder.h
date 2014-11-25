#include <jni.h>

#ifndef _Included_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonDecoder
#define _Included_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonDecoder
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonDecoder_jni_init
  (JNIEnv *, jclass, jint, jint, jintArray);


JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonDecoder_jni_decode
  (JNIEnv *, jclass, jobjectArray, jintArray, jint);


JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonDecoder_jni_destroy
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif

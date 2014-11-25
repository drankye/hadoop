#include <jni.h>

#ifndef _Included_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonEncoder
#define _Included_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonEncoder
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonEncoder_jni_init
  (JNIEnv *, jclass, jint, jint, jintArray);


JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonEncoder_jni_encode
  (JNIEnv *, jclass, jobjectArray, jobjectArray, jint);


JNIEXPORT jint JNICALL Java_org_apache_hadoop_hdfs_ec_rawcoder_impl_IsaReedSolomonEncoder_jni_destroy
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif

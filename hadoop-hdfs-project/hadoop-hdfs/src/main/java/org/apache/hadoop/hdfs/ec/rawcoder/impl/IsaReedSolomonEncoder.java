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
package org.apache.hadoop.hdfs.ec.rawcoder.impl;

import java.nio.ByteBuffer;

public class IsaReedSolomonEncoder {
  private int dataSize;
  private int paritySize;

  public IsaReedSolomonEncoder(int dataSize, int paritySize) {
    this.dataSize = dataSize;
    this.paritySize = paritySize;
    jni_init(dataSize, paritySize);
  }

  private static native int jni_init(int dataSize, int paritySize);

  private static native int jni_encode(ByteBuffer[] data, ByteBuffer[] code, int chunkSize);

  private static native int jni_destroy();

  static {
    System.loadLibrary("isajni");
  }

  public void encode(ByteBuffer[] data, ByteBuffer[] code, int chunkSize) {
    jni_encode(data, code, chunkSize);
  }

  public void end() {
    jni_destroy();
  }

  @Override
  protected void finalize() {
    jni_destroy();
  }
}

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
package org.apache.hadoop.io.erasurecode.rawcoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.erasurecode.rawcoder.util.RSUtil;
import org.apache.hadoop.util.NativeCodeLoader;

import java.nio.ByteBuffer;

public class ISARSRawEncoder extends AbstractRawErasureEncoder {

  public static final Log LOG = LogFactory.getLog(ISARSRawEncoder.class.getName());
  private int[] matrix;

  static {
    if (NativeCodeLoader.isNativeCodeLoaded()) {
      loadLib();
    } else {
      LOG.error("failed to load ISA_RS Encoder");
    }
  }

  @Override
  public void initialize(int numDataUnits, int numParityUnits, int chunkSize) {
    super.initialize(numDataUnits, numParityUnits, chunkSize);

    matrix = RSUtil.initMatrix(numDataUnits, numParityUnits);
    init(numDataUnits, numParityUnits, matrix);
  }

  @Override
  protected void doEncode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
    encode(inputs, outputs, getChunkSize());
  }

  @Override
  protected void doEncode(byte[][] inputs, byte[][] outputs) {
    throw new RuntimeException(
        "To be implemented, please use chunk or bytebuffer versions");
  }

  private static native int loadLib();

  private static native int init(int dataSize, int paritySize, int[] matrix);

  private static native int encode(ByteBuffer[] data, ByteBuffer[] code, int chunkSize);

  private static native int destroy();

  @Override
  public void release() {
    destroy();
  }
}

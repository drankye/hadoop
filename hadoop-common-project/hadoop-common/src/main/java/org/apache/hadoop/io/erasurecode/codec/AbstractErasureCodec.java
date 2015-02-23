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
package org.apache.hadoop.io.erasurecode.codec;

import org.apache.hadoop.io.erasurecode.ECSchema;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureDecoder;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureEncoder;
import org.apache.hadoop.io.erasurecode.grouper.BlockGrouper;

/**
 * Erasure Codec to be managed by ECManager and used by ECWorker
 */
public abstract class AbstractErasureCodec implements ErasureCodec {
  private ECSchema schema;

  public void initWith(ECSchema schema) {
    this.schema = schema;
  }

  public String getName() {
    return schema.getCodecName();
  }

  protected ECSchema getSchema() {
    return schema;
  }

  /**
   * Create block grouper, to be called by ECManager
   * @return
   */
  public abstract BlockGrouper createBlockGrouper();

  /**
   * Create Erasure Encoder, to be called by ECWorker
   * @return
   */
  public abstract ErasureEncoder createEncoder();

  /**
   * Create Erasure Decoder, to be called by ECWorker
   * @return
   */
  public abstract ErasureDecoder createDecoder();

  /**
   * For ECManager
   * @param schema
   * @return
   * @throws Exception
   */
  public static AbstractErasureCodec createErasureCodec(ECSchema schema) throws Exception {
    String codecClassName = schema.getSchemaClassName();
    Class codecClass = Class.forName(codecClassName);
    AbstractErasureCodec codec = (AbstractErasureCodec) codecClass.newInstance();
    codec.initWith(schema);

    return codec;
  }

}

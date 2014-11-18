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
package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.grouper.BlockGrouper;

/**
 * Erasure Codec to be managed by ECManager and used by ECWorker
 */
public abstract class ErasureCodec {

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
   * Create Erasure Coder, to be called by ECWorker
   * @return
   */
  public abstract ErasureCoder createErasureCoder();


  public static ErasureCodec createErasureCodec(ECSchema schema) throws Exception {
    String codecName = schema.getCodecName();
    String codecClassName = null; // TODO: convert from codecName
    Class codecClass = Class.forName(codecClassName);
    ErasureCodec codec = (ErasureCodec) codecClass.newInstance();
    codec.initWith(schema);

    return codec;
  }
}

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
package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;

public class AbstractErasureCoder implements ErasureCoder {

  protected ECSchema schema;
  protected Encoder encoder;
  protected  Decoder decoder;

  public void initWith(ECSchema schema) {
    this.schema = schema;
  }

  @Override
  public void encode(ECChunk[] dataChunks, ECChunk outputChunk) {
    encoder.encode(dataChunks, outputChunk);
  }

  @Override
  public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {
    encoder.encode(dataChunks, outputChunks);
  }

  @Override
  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, String annotation, ECChunk outputChunk) {
    decoder.decode(dataChunks, parityChunks, annotation, outputChunk);
  }

  @Override
  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, String annotation, ECChunk[] outputChunks) {
    decoder.decode(dataChunks, parityChunks, annotation, outputChunks);
  }
}

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
import org.apache.hadoop.io.erasurecode.coder.ErasureDecoder;
import org.apache.hadoop.io.erasurecode.coder.ErasureEncoder;
import org.apache.hadoop.io.erasurecode.grouper.BlockGrouper;

/**
 * Erasure Codec to be used by ECManager, ECWorker and ECClient
 */
public interface ErasureCodec {

  public void initWith(ECSchema schema);

  /**
   * Create block grouper, to be called by ECManager
   * @return
   */
  public BlockGrouper createBlockGrouper();

  /**
   * Create Erasure Encoder, to be called by ECWorker or ECClient
   * @return
   */
  public ErasureEncoder createEncoder();

  /**
   * Create Erasure Decoder, to be called by ECWorker or ECClient
   * @return
   */
  public ErasureDecoder createDecoder();

}

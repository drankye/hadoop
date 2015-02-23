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

import org.apache.hadoop.io.erasurecode.blockcoder.ErasureDecoder;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureEncoder;
import org.apache.hadoop.io.erasurecode.blockcoder.RSErasureDecoder;
import org.apache.hadoop.io.erasurecode.blockcoder.RSErasureEncoder;
import org.apache.hadoop.io.erasurecode.grouper.BlockGrouper;

/**
 * Reed-Solomon codec
 */
public class RSErasureCodec extends AbstractErasureCodec {

  @Override
  public BlockGrouper createBlockGrouper() {
    BlockGrouper blockGrouper = new BlockGrouper();
    blockGrouper.initWith(getSchema());
    return blockGrouper;
  }

  @Override
  public ErasureEncoder createEncoder() {
    ErasureEncoder encoder = new RSErasureEncoder();
    encoder.initialize(getSchema());

    return encoder;
  }

  @Override
  public ErasureDecoder createDecoder() {
    ErasureDecoder decoder = new RSErasureDecoder();
    decoder.initialize(getSchema());

    return decoder;
  }

}

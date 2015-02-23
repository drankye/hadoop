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
package org.apache.hadoop.io.ec.codec;

import org.apache.hadoop.io.ec.ECSchema;
import org.apache.hadoop.io.ec.coder.ErasureDecoder;
import org.apache.hadoop.io.ec.coder.ErasureEncoder;
import org.apache.hadoop.io.ec.coder.XorDecoder;
import org.apache.hadoop.io.ec.coder.XorEncoder;
import org.apache.hadoop.io.ec.grouper.BlockGrouper;
import org.apache.hadoop.io.ec.grouper.XorBlockGrouper;

public class XorErasureCodec extends ErasureCodec{

  @Override
  public void initWith(ECSchema schema) {
    super.initWith(schema);
    assert(schema.getParityBlocks() == 1);
  }

  @Override
  public BlockGrouper createBlockGrouper() {
    BlockGrouper blockGrouper = new XorBlockGrouper();
    blockGrouper.initWith(getSchema());
    return blockGrouper;
  }

  @Override
  public ErasureEncoder createEncoder() {
    return new XorEncoder(getSchema().getDataBlocks(), getSchema().getChunkSize());
  }

  @Override
  public ErasureDecoder createDecoder() {
    return new XorDecoder(getSchema().getDataBlocks(), getSchema().getChunkSize());
  }
}

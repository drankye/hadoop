package org.apache.hadoop.io.ec.coder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.ec.BlockGroup;
import org.apache.hadoop.io.ec.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;
import org.apache.hadoop.io.ec.SubBlockGroup;
import org.apache.hadoop.io.ec.rawcoder.XorRawEncoder;

import java.nio.ByteBuffer;

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
public class XorEncoder extends AbstractErasureEncoder{
  private static final Log LOG = LogFactory.getLog(XorEncoder.class.getName());

  public XorEncoder(int dataSize, int chunkSize) {
    super(new XorRawEncoder(dataSize, chunkSize));
  }

  @Override
  public void encode(BlockGroup blockGroup) {
    SubBlockGroup subGroup = blockGroup.getSubGroups().iterator().next();
    ECBlock[] inputBlocks = subGroup.getDataBlocks();
    ECBlock[] outputBlocks = subGroup.getParityBlocks();

    try {
      beforeCoding(inputBlocks, outputBlocks);

      while (hasNextInputs()) {
        ECChunk[] dataChunks = getNextInputChunks(inputBlocks);
        ECChunk[] parityChunks = getNextOutputChunks(outputBlocks);

        ByteBuffer[] data = convert(dataChunks);
        ByteBuffer[] parity = convert(parityChunks);
        getRawEncoder().encode(data, parity);

        withCoded(dataChunks, parityChunks);
      }
    } catch (Exception e) {
      LOG.info("Error in encode " + e);
    } finally {
      postCoding(inputBlocks, outputBlocks);
    }
  }
}

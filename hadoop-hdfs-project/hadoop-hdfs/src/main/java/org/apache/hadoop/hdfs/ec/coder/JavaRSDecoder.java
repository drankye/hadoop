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

import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.SubBlockGroup;
import org.apache.hadoop.hdfs.ec.coder.util.TransformUtil;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawDecoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class JavaRSDecoder extends AbstractErasureDecoder {

  public JavaRSDecoder(int dataSize, int paritySize, int chunkSize) {
    super(new JavaRSRawDecoder(dataSize, paritySize, chunkSize));
  }

  @Override
  public void decode(BlockGroup blockGroup) {
    SubBlockGroup subBlockGroup = blockGroup.getSubGroups().iterator().next();
    ECBlock[] readBlocks = combineBlocks(subBlockGroup.getDataBlocks(), subBlockGroup.getParityBlocks());
    int[] erasedLocations = null;
    ECBlock[] outputBlocks = null;
    getErasedLocationsAndOutputBlocks(readBlocks, erasedLocations, outputBlocks);
    getCallback().beforeCoding(readBlocks, outputBlocks);

    while (getCallback().hasNextInputs()) {
      ECChunk[] dataChunks = getCallback().getNextInputChunks(readBlocks);
      ByteBuffer[] readBuffs = TransformUtil.changeToByteBufferArray(dataChunks);
      ECChunk[] outputChunks = getCallback().getNextOutputChunks(outputBlocks);
      ByteBuffer[] outputBuffs = TransformUtil.changeToByteBufferArray(outputChunks);

      getRawDecoder().decode(readBuffs, outputBuffs, erasedLocations);
    }

    getCallback().postCoding(readBlocks, outputBlocks);
  }

  private ECBlock[] combineBlocks(ECBlock[] dataBlocks, ECBlock[] parityBlocks) {
    ECBlock[] result = new ECBlock[dataBlocks.length + parityBlocks.length];
    for (int i = 0; i < parityBlocks.length; ++i) {
      result[i] = parityBlocks[i];
    }
    for (int i = 0; i < dataBlocks.length; ++i) {
      result[i + parityBlocks.length] = dataBlocks[i];
    }
    return result;
  }

  private void getErasedLocationsAndOutputBlocks(ECBlock[] readBlocks, int[] erasedLocations, ECBlock[] outputBlocks) {
    List<Integer> erasedLocationList = new ArrayList<Integer>();
    List<ECBlock> outputBlockList = new ArrayList<ECBlock>();
    for (int i = 0; i < readBlocks.length; i++) {
      ECBlock readBlock = readBlocks[i];
      if (readBlock.isMissing()) {
        erasedLocationList.add(i);
        outputBlockList.add(new ECBlock(readBlock.getExtendedBlockId(), readBlock.isParity()));
      }
    }

    //change to arrays
    erasedLocations = new int[erasedLocationList.size()];
    for (int i = 0; i < erasedLocationList.size(); i++) {
      erasedLocations[i] = erasedLocationList.get(i);
    }
    outputBlocks = new ECBlock[outputBlockList.size()];
    outputBlocks = outputBlockList.toArray(outputBlocks);
  }

}

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
package org.apache.hadoop.hdfs.ec.grouper;

import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.SubBlockGroup;

import java.util.List;

/**
 * A BlockGrouper for RS codec
 */
public class RSBlockGrouper extends BlockGrouper {


  @Override
  public BlockGroup makeBlockGroup(List<ExtendedBlockId> dataBlocks,
                                   List<ExtendedBlockId> parityBlocks) {
	  ECBlock[] dataEcBlocks = new ECBlock[dataBlocks.size()];
	  for (int i = 0; i < dataBlocks.size(); i++) {
		  dataEcBlocks[i] = new ECBlock(dataBlocks.get(i), false);
	  }
	  ECBlock[] parityEcBlocks = new ECBlock[parityBlocks.size()];
	  for (int i = 0; i < parityBlocks.size(); i++) {
		  parityEcBlocks[i] = new ECBlock(parityBlocks.get(i), true);
	  }
	  SubBlockGroup subBlockGroup = new SubBlockGroup(dataEcBlocks, parityEcBlocks);
	  BlockGroup group = new BlockGroup();
	  group.addSubGroup(subBlockGroup);
	  return group;
  }

  @Override
  public boolean anyRecoverable(BlockGroup blockGroup) {
    return true;
  }

  @Override
  public BlockGroup makeRecoverableGroup(BlockGroup blockGroup) {
	  //FIXME write for test
	  return blockGroup;
  }
}

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
package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

/**
 * Abstract Raw Erasure Coder that corresponds to an erasure code algorithm, to be used by an Erasure Coder
 */
public abstract class AbstractRawErasureCoder implements RawErasureCoder {
  private int dataSize;
  private int paritySize;
	private int chunkSize;
	
	
	public AbstractRawErasureCoder(int dataSize, int paritySize, int chunkSize) {
		this.dataSize = dataSize;
		this.paritySize = paritySize;
		this.chunkSize = chunkSize;
	}

	@Override
	public abstract void encode(ByteBuffer[] inputs, ByteBuffer[] outputs);

	@Override
	public abstract void decode(ByteBuffer[] readBufs, ByteBuffer[] writeBufs, int[] erasedIndexes);
	
	/**
	 * The number of elements in the message.
	 */
	public int dataSize() {
		return dataSize;
	}

	/**
	 * The number of elements in the code.
	 */
	public int paritySize() {
		return paritySize;
	}

  /**
   * Chunk buffer size for an encod()/decode() call
   * @return
   */
  public int chunkSize() {
	  return chunkSize;
  }
}

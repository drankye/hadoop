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

import org.apache.hadoop.hdfs.ec.rawcoder.impl.IsaReedSolomonDecoder;
import org.apache.hadoop.hdfs.ec.rawcoder.impl.IsaReedSolomonEncoder;

import java.nio.ByteBuffer;

/**
 * An RS Erasure Coder that uses ISA-L library
 */
public class IsaRSRawErasureCoder extends AbstractRawErasureCoder {
	private IsaReedSolomonEncoder encoder;
  private IsaReedSolomonDecoder decoder;

	public IsaRSRawErasureCoder(int dataSize, int paritySize, int chunkSize) {
		super(dataSize, paritySize, chunkSize);
		init();
	}

	private void init() {
    this.encoder = new IsaReedSolomonEncoder(dataSize(), paritySize());
    this.decoder = new IsaReedSolomonDecoder(dataSize(), paritySize());
	}

	@Override
	public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
		assert (dataSize() == inputs.length);
		assert (paritySize() == outputs.length);

    encoder.encode(inputs, outputs, chunkSize());
	}

	@Override
	public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
		if (erasedIndexes.length == 0) {
			return;
		}

    ByteBuffer[] allData = null; // TODO: inputs + outputs;
    decoder.decode(allData, erasedIndexes, chunkSize());
	}
}

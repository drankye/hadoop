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

import org.apache.hadoop.hdfs.ec.coder.old.impl.help.GaloisField;

import java.nio.ByteBuffer;

/**
 * Raw Erasure Coder that corresponds to an erasure code algorithm
 */
public class AbstractRawErasureCoder implements RawErasureCoder {
	protected GaloisField GF = GaloisField.getInstance();
	protected int dataSize;
	protected int paritySize;
	
	
	public AbstractRawErasureCoder(int dataSize, int paritySize) {
		this.dataSize = dataSize;
		this.paritySize = paritySize;
	}
	
	/**
	 * Encodes the given message.
	 *
	 * @param message
	 *            The data of the message. The data is present in the least
	 *            significant bits of each int. The number of data bits is
	 *            symbolSize(). The number of elements of message is
	 *            stripeSize().
	 * @param parity
	 *            (out) The information is present in the least significant bits
	 *            of each int. The number of parity bits is symbolSize(). The
	 *            number of elements in the code is paritySize().
	 */
	@Override
	public void encode(int[] message, int[] parity) {

	}

	/**
	 * Generates missing portions of data.
	 *
	 * @param data
	 *            The message and parity. The parity should be placed in the
	 *            first part of the array. In each integer, the relevant portion
	 *            is present in the least significant bits of each int. The
	 *            number of elements in data is stripeSize() + paritySize().
	 * @param erasedLocations
	 *            The indexes in data which are not available.
	 * @param erasedValues
	 *            (out)The decoded values corresponding to erasedLocations.
	 */
	@Override
	public void decode(int[] data, int[] erasedLocations, int[] erasedValues) {

	}

	/**
	 * This method would be overridden in the subclass, so that the subclass
	 * will have its own encodeBulk behavior.
	 */
	@Override
	public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
		final int stripeSize = dataSize();
		final int paritySize = paritySize();
		assert (stripeSize == inputs.length);
		assert (paritySize == outputs.length);
		int[] data = new int[stripeSize];
		int[] code = new int[paritySize];

		for (int j = 0; j < inputs[0].array().length; j++) {
			for (int i = 0; i < paritySize; i++) {
				code[i] = 0;
			}
			for (int i = 0; i < stripeSize; i++) {
				data[i] = inputs[i].array()[j] & 0x000000FF;
			}
			encode(data, code);
			for (int i = 0; i < paritySize; i++) {
				outputs[i].array()[j] = (byte) code[i];
			}
		}
	}

	@Override
	public void decode(ByteBuffer[] readBufs, ByteBuffer[] writeBufs, int[] erasedLocation) {}
	
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

	@Override
	public int symbolSize() {
		return (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
	}

  /**
   * Chunk buffer size for an encod()/decode() call
   * @return
   */
  public int getChunkSize() {
    return 16 * 1024;
  }
}

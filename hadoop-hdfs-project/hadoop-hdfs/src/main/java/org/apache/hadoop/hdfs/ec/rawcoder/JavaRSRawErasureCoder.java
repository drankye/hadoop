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

import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A raw RS Erasure Coder in pure Java, that can be used as fallback when native ones not available
 */
public class JavaRSRawErasureCoder extends AbstractRawErasureCoder {
	private int[] generatingPolynomial;
	private int PRIMITIVE_ROOT = 2;
	private int[] primitivePower;
	private GaloisField GF = GaloisField.getInstance();
	private int[] paritySymbolLocations;
	private int[] errSignature;

	public JavaRSRawErasureCoder(int dataSize, int paritySize, int chunkSize) {
		super(dataSize, paritySize, chunkSize);
		init();
	}

	private void init() {
		assert (dataSize() + paritySize() < GF.getFieldSize());
		this.errSignature = new int[paritySize()];
		this.paritySymbolLocations = new int[paritySize()];
		for (int i = 0; i < paritySize(); i++) {
			paritySymbolLocations[i] = i;
		}

		this.primitivePower = new int[dataSize() + paritySize()];
		// compute powers of the primitive root
		for (int i = 0; i < dataSize() + paritySize(); i++) {
			primitivePower[i] = GF.power(PRIMITIVE_ROOT, i);
		}
		// compute generating polynomial
		int[] gen = { 1 };
		int[] poly = new int[2];
		for (int i = 0; i < paritySize(); i++) {
			poly[0] = primitivePower[i];
			poly[1] = 1;
			gen = GF.multiply(gen, poly);
		}
		// generating polynomial has all generating roots
		generatingPolynomial = gen;
	}

	/**
	 * This function (actually, the GF.remainder() function) will modify
	 * the "inputs" parameter.
	 */
	@Override
	public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
		assert (dataSize() == inputs.length);
		assert (paritySize() == outputs.length);

		for (int i = 0; i < outputs.length; i++) {
			Arrays.fill(outputs[i].array(), (byte) 0);
		}

		byte[][] data = new byte[dataSize() + paritySize()][];

		for (int i = 0; i < paritySize(); i++) {
			data[i] = outputs[i].array();
		}
		for (int i = 0; i < dataSize(); i++) {
			data[i + paritySize()] = inputs[i].array();
		}
		// Compute the remainder
		GF.remainder(data, generatingPolynomial);
	}

	@Override
	public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
		if (erasedIndexes.length == 0) {
			return;
		}

		// cleanup the write buffer
		for (int i = 0; i < outputs.length; i++) {
			Arrays.fill(outputs[i].array(), (byte) 0);
		}

		for (int i = 0; i < erasedIndexes.length; i++) {
			errSignature[i] = primitivePower[erasedIndexes[i]];
			GF.substitute(inputs, outputs[i], primitivePower[i]);
		}
		
		GF.solveVandermondeSystem(errSignature, outputs,
				erasedIndexes.length, inputs[0].array().length);
	}
}

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
package org.apache.hadoop.hdfs.ec;

import java.nio.ByteBuffer;
import java.util.Random;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.coder.AbstractErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.JavaRSErasureCoder;

import junit.framework.TestCase;

public class TestRawErasureCodes extends TestCase {
	final int TEST_CODES = 100;
	final int TEST_TIMES = 1000;
	final Random RAND = new Random();

	public void testRSPerformance() {
		int dataSize = 10;
		int paritySize = 4;

		ECSchema schema = new ECSchema("", null, "");
		schema.setDataBlocks(dataSize);
		schema.setParityBlocks(paritySize);

		AbstractErasureCoder ec = new JavaRSErasureCoder();
		ec.initWith(schema);

		int symbolMax = (int) Math.pow(2,
				((JavaRSErasureCoder) ec).symbolSize());
		ECChunk[] message = new ECChunk[dataSize];
		int bufsize = 1024 * 1024 * 10;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			ByteBuffer buffer = ByteBuffer.wrap(byteArray);
			message[i] = new ECChunk(buffer);
		}
		ECChunk[] parity = new ECChunk[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		long encodeStart = System.currentTimeMillis();

		ECChunk[] tmpIn = new ECChunk[dataSize];
		ECChunk[] tmpOut = new ECChunk[paritySize];
		for (int i = 0; i < tmpOut.length; i++) {
			tmpOut[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		for (int j = 0; j < dataSize; j++)
			tmpIn[j] = message[j].clone();
		ec.encode(tmpIn, tmpOut);
		// Copy parity.
		for (int j = 0; j < paritySize; j++)
			parity[j] = tmpOut[j].clone();
		long encodeEnd = System.currentTimeMillis();
		float encodeMSecs = (encodeEnd - encodeStart);
		System.out.println("Time to encode rs = " + encodeMSecs + "msec ("
				+ message[0].getChunkBuffer().array().length
				/ (1000 * encodeMSecs) + " MB/s)");

		String erasedAnnotation = "__D2__D4D5__D7";
		ECChunk[] erasedValues = new ECChunk[4];
		for (int i = 0; i < erasedValues.length; i++) {
			erasedValues[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		ECChunk copy = message[0].clone();
		message[0].setChunkBuffer(ByteBuffer.wrap(new byte[bufsize]));

		long decodeStart = System.currentTimeMillis();
		ec.decode(message, parity, erasedAnnotation, erasedValues);
		message[0] = erasedValues[0];
		long decodeEnd = System.currentTimeMillis();
		float decodeMSecs = (decodeEnd - decodeStart);
		System.out.println("Time to decode = " + decodeMSecs + "msec ("
				+ message[0].getChunkBuffer().array().length
				/ (1000 * decodeMSecs) + " MB/s)");
		assertTrue("Decode failed", copy.equals(message[0]));
	}

	public void testRSEncodeDecodeBulk() {
		// verify the production size.
		verifyRSEncodeDecode(10, 4);

		// verify a test size
		verifyRSEncodeDecode(3, 3);
	}

	public void verifyRSEncodeDecode(int dataSize, int paritySize) {
		ECSchema schema = new ECSchema("", null, "");
		schema.setDataBlocks(dataSize);
		schema.setParityBlocks(paritySize);

		AbstractErasureCoder ec = new JavaRSErasureCoder();
		ec.initWith(schema);

		int symbolMax = (int) Math.pow(2,
				((JavaRSErasureCoder) ec).symbolSize());
		ECChunk[] message = new ECChunk[dataSize];
		ECChunk[] cpMessage = new ECChunk[dataSize];
		int bufsize = 1024 * 1024 * 10;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			ByteBuffer buffer = ByteBuffer.wrap(byteArray);
			message[i] = new ECChunk(buffer);
			cpMessage[i] = message[i].clone();
		}
		ECChunk[] parity = new ECChunk[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}

		// encode.
		ec.encode(cpMessage, parity);

		int erasedLocation = RAND.nextInt(dataSize);
		String annotation = generateAnnotation(erasedLocation);
		ECChunk copy = message[erasedLocation].clone();
		message[erasedLocation].setChunkBuffer(ByteBuffer
				.wrap(new byte[bufsize]));

		// test decode
		ECChunk[] output = new ECChunk[1];
		output[0] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		ec.decode(message, parity, annotation, output);
		assertTrue("Decode failed", copy.equals(output[0]));
	}

	private String generateAnnotation(int erasedLocation) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < erasedLocation; i++) {
			sb.append("XX");
		}
		sb.append("__");
		return sb.toString();
	}
}

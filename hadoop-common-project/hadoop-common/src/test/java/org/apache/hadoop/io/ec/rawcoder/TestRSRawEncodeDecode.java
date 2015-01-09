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
package org.apache.hadoop.io.ec.rawcoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.ec.rawcoder.*;
import org.apache.hadoop.io.ec.rawcoder.util.GaloisField;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 * Ported from HDFS-RAID
 */
public class TestRSRawEncodeDecode {
	public static final Log LOG = LogFactory.getLog(IsaRSRawEncoder.class.getName());
	final Random RAND = new Random();
  private static GaloisField GF = GaloisField.getInstance();
  private static int symbolSize = 0;
	private static int CHUNK_SIZE = 1024 * 1024;

  static {
    symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
  }

	@Test
	public void testJavaRSPerformance() {
		int dataSize = 10;
		int paritySize = 4;
		RawErasureEncoder encoder = new JavaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
		RawErasureDecoder decoder = new JavaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
		testRSPerformance(encoder, decoder);
	}

	@Test
	public void testIsaRSPerformance() {
		int dataSize = 10;
		int paritySize = 4;
		RawErasureEncoder encoder = new IsaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
		RawErasureDecoder decoder = new IsaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
		testRSPerformance(encoder, decoder);
	}

	private void testRSPerformance(RawErasureEncoder rawEncoder, RawErasureDecoder rawDecoder) {
		int dataSize = 10;
		int paritySize = 4;
		int bufsize = CHUNK_SIZE;

		int symbolMax = (int) Math.pow(2, symbolSize);
		byte[][] message = new byte[dataSize][];

		ByteBuffer[] dataForEncode = new ByteBuffer[dataSize];
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = byteArray;
			dataForEncode[i] = ByteBuffer.allocateDirect(bufsize);
			dataForEncode[i].put(byteArray);
			dataForEncode[i].flip();
		}
		ByteBuffer[] parity = new ByteBuffer[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = ByteBuffer.allocateDirect(bufsize);
		}

		long encodeStart = System.currentTimeMillis();
		rawEncoder.encode(dataForEncode, parity);
		long encodeEnd = System.currentTimeMillis();
		float encodeMSecs = (encodeEnd - encodeStart);
		LOG.info("Time to " + rawEncoder.getClass().getName() + " = " + encodeMSecs + "msec ("
				+ message[0].length / (1000 * encodeMSecs) + " MB/s)");

		int[] erasedLocations = new int[] { 4, 1, 5, 7 };
		ByteBuffer[] erasedValues = new ByteBuffer[4];
		for (int i = 0; i < erasedValues.length; i++) {
			erasedValues[i] = ByteBuffer.allocateDirect(bufsize);
		}
		byte[] realDataIndex0 = Arrays.copyOfRange(message[0], 0, message[0].length);

		ByteBuffer[] data = new ByteBuffer[paritySize + dataSize];
		for (int i = 0; i < paritySize; i++) {
			data[i] = parity[i];
		}
		data[paritySize] = ByteBuffer.allocateDirect(bufsize);
		data[paritySize].put(new byte[bufsize]);
		data[paritySize].flip();
		for (int i = 1; i < dataSize; i++) {
			data[i + paritySize] = ByteBuffer.allocateDirect(bufsize);
			data[i + paritySize].put(message[i]);
			data[i + paritySize].flip();
		}

		long decodeStart = System.currentTimeMillis();
		rawDecoder.decode(data, erasedValues, erasedLocations);
		long decodeEnd = System.currentTimeMillis();
		float decodeMSecs = (decodeEnd - decodeStart);
		LOG.info("Time to " + rawDecoder.getClass().getName() + " = " + decodeMSecs + "msec ("
				+ message[0].length / (1000 * decodeMSecs) + " MB/s)");

		byte[] dataAfterCorrect = new byte[bufsize];
		erasedValues[0].get(dataAfterCorrect);
    Assert.assertTrue("Decode failed", Arrays.equals(realDataIndex0, dataAfterCorrect));
	}

	@Test
	public void testEncodeDecode() {
		// verify the production size.
		verifyJavaRSRawEncodeDecode(10, 4);
		verifyIsaRSRawEncodeDecode(10, 4);

		// verify a test size
		verifyJavaRSRawEncodeDecode(3, 3);
		verifyIsaRSRawEncodeDecode(3, 3);
	}

	private void verifyJavaRSRawEncodeDecode(int dataSize, int paritySize) {
		RawErasureEncoder rawEncoder = new JavaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
		RawErasureDecoder rawDecoder = new JavaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
		verifyRSEncodeDecode(rawEncoder, rawDecoder, dataSize, paritySize);
	}

	private void verifyIsaRSRawEncodeDecode(final int dataSize, final int paritySize) {
		Thread encodeDecodeThread = new Thread(new Runnable() {

			@Override
			public void run() {
				RawErasureEncoder rawEncoder = new IsaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
				RawErasureDecoder rawDecoder = new IsaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
				verifyRSEncodeDecode(rawEncoder, rawDecoder, dataSize, paritySize);
			}
		});
		encodeDecodeThread.start();
	}

	private void verifyRSEncodeDecode(RawErasureEncoder rawEncoder, RawErasureDecoder rawDecoder, int dataSize, int paritySize) {
		int bufsize = CHUNK_SIZE;
		int symbolMax = (int) Math.pow(2, symbolSize);
		byte[][] message = new byte[dataSize][];
		ByteBuffer[] dataForEncode = new ByteBuffer[dataSize];
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = byteArray;
			dataForEncode[i] = ByteBuffer.allocateDirect(bufsize);
			dataForEncode[i].put(byteArray);
			dataForEncode[i].flip();
		}
		
		ByteBuffer[] parity = new ByteBuffer[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = ByteBuffer.allocateDirect(bufsize);
		}

		// encode.
		rawEncoder.encode(dataForEncode, parity);

		int erasedLocation = RAND.nextInt(dataSize);
		
		byte[] rightDateInErasedLocation = Arrays.copyOfRange(message[erasedLocation], 0, message[erasedLocation].length);

		ByteBuffer[] dataForDecode = new ByteBuffer[dataSize + paritySize];
		for (int i = 0; i < paritySize; i++) {
			dataForDecode[i] = parity[i];
		}
		for (int i = 0; i < dataSize; i++) {
			dataForDecode[i + paritySize] = ByteBuffer.allocateDirect(bufsize);
			if (i == erasedLocation) {
				dataForDecode[i + paritySize].put(new byte[bufsize]);
			} else {
				dataForDecode[i + paritySize].put(message[i]);
			}
			dataForDecode[i + paritySize].flip();
		}
		ByteBuffer[] writeBufs = new ByteBuffer[1];
		writeBufs[0] = ByteBuffer.allocateDirect(bufsize);
		rawDecoder.decode(dataForDecode, writeBufs, new int[] {erasedLocation + paritySize });

		byte[] outputDataArray = new byte[bufsize];
		writeBufs[0].get(outputDataArray);
		Assert.assertTrue("Decode failed", Arrays.equals(rightDateInErasedLocation, outputDataArray));
	}
}

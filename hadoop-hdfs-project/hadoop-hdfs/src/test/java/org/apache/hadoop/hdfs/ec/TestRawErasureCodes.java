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

import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.rawcoder.RawErasureDecoder;
import org.apache.hadoop.hdfs.ec.rawcoder.RawErasureEncoder;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawDecoder;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawEncoder;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 * Ported from HDFS-RAID
 */
public class TestRawErasureCodes {
	final Random RAND = new Random();
  private static GaloisField GF = GaloisField.getInstance();
  private static int symbolSize = 0;

  static {
    symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
  }

  @Test
	public void testRSPerformance() {
		int dataSize = 10;
		int paritySize = 4;

	  RawErasureEncoder rawEncoder = new JavaRSRawEncoder(dataSize, paritySize, 16 * 1024);
	  RawErasureDecoder rawDecoder = new JavaRSRawDecoder(dataSize, paritySize, 16 * 1024);

		int symbolMax = (int) Math.pow(2, symbolSize);
		ByteBuffer[] message = new ByteBuffer[dataSize];
		int bufsize = 1024 * 1024 * 10;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = ByteBuffer.wrap(byteArray);
		}
		ByteBuffer[] parity = new ByteBuffer[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = ByteBuffer.wrap(new byte[bufsize]);
		}
		long encodeStart = System.currentTimeMillis();

		ByteBuffer[] tmpIn = new ByteBuffer[dataSize];
		ByteBuffer[] tmpOut = new ByteBuffer[paritySize];
		for (int i = 0; i < tmpOut.length; i++) {
			tmpOut[i] = ByteBuffer.wrap(new byte[bufsize]);
		}
		for (int i = 0; i < dataSize; i++) {
			byte[] cpByte = Arrays.copyOfRange(message[i].array(), 0, message[i].array().length);
			tmpIn[i] = ByteBuffer.wrap(cpByte);
		}
		rawEncoder.encode(tmpIn, tmpOut);
		// Copy parity.
		for (int i = 0; i < paritySize; i++) {
			byte[] cpByte = Arrays.copyOfRange(tmpOut[i].array(), 0, tmpOut[i].array().length);
			parity[i] = ByteBuffer.wrap(cpByte);
		}
		long encodeEnd = System.currentTimeMillis();
		float encodeMSecs = (encodeEnd - encodeStart);
		System.out.println("Time to encode rs = " + encodeMSecs + "msec ("
				+ message[0].array().length / (1000 * encodeMSecs) + " MB/s)");

		int[] erasedLocations = new int[] { 4, 1, 5, 7 };
		ByteBuffer[] erasedValues = new ByteBuffer[4];
		for (int i = 0; i < erasedValues.length; i++) {
			erasedValues[i] = ByteBuffer.wrap(new byte[bufsize]);
		}
		byte[] cpByte = Arrays.copyOfRange(message[0].array(), 0, message[0].array().length);
		ByteBuffer copy = ByteBuffer.wrap(cpByte);

		ByteBuffer[] data = new ByteBuffer[paritySize + dataSize];
		for (int i = 0; i < paritySize; i++) {
			data[i] = parity[i];
		}
		data[paritySize] = ByteBuffer.wrap(new byte[bufsize]);
		for (int i = 1; i < dataSize; i++) {
			data[i + paritySize] = message[i];
		}

		long decodeStart = System.currentTimeMillis();
		rawDecoder.decode(data, erasedValues, erasedLocations);
		long decodeEnd = System.currentTimeMillis();
		float decodeMSecs = (decodeEnd - decodeStart);
		System.out.println("Time to decode = " + decodeMSecs + "msec ("
				+ message[0].array().length / (1000 * decodeMSecs) + " MB/s)");
    Assert.assertTrue("Decode failed", copy.equals(erasedValues[0]));
	}

  @Test
	public void testRSEncodeDecodeByteBuffer() {
		// verify the production size.
		verifyRSEncodeDecode(10, 4);

		// verify a test size
		verifyRSEncodeDecode(3, 3);
	}

	private void verifyRSEncodeDecode(int dataSize, int paritySize) {
		RawErasureEncoder rawEncoder = new JavaRSRawEncoder(dataSize, paritySize, 16 * 1024);
		RawErasureDecoder rawDecoder = new JavaRSRawDecoder(dataSize, paritySize, 16 * 1024);

		int symbolMax = (int) Math.pow(2, symbolSize);
		ByteBuffer[] message = new ByteBuffer[dataSize];
		ByteBuffer[] cpMessage = new ByteBuffer[dataSize];
		int bufsize = 3;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = ByteBuffer.wrap(byteArray);
			byte[] cpByte = Arrays.copyOfRange(message[i].array(), 0, message[i].array().length);
			cpMessage[i] = ByteBuffer.wrap(cpByte);
		}
		
		ByteBuffer[] parity = new ByteBuffer[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = ByteBuffer.wrap(new byte[bufsize]);
		}

		// encode.
		rawEncoder.encode(cpMessage, parity);

		int erasedLocation = RAND.nextInt(dataSize);
		
		byte[] copyByte = Arrays.copyOfRange(message[erasedLocation].array(), 0, message[erasedLocation].array().length);
		ByteBuffer copy = ByteBuffer.wrap(copyByte);
		message[erasedLocation] = ByteBuffer.wrap(new byte[bufsize]);
		
		// test decode
		ByteBuffer[] data = new ByteBuffer[dataSize + paritySize];
		for (int i = 0; i < paritySize; i++) {
			data[i] = parity[i];
		}

		for (int i = 0; i < dataSize; i++) {
			data[i + paritySize] = message[i];
		}
		ByteBuffer[] writeBufs = new ByteBuffer[1];
		writeBufs[0] = ByteBuffer.wrap(new byte[bufsize]);
		rawDecoder.decode(data, writeBufs, new int[] {erasedLocation + paritySize });
		Assert.assertTrue("Decode failed", copy.equals(writeBufs[0]));
	}
}

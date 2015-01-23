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
import org.apache.hadoop.io.ec.ECChunk;
import org.apache.hadoop.io.ec.rawcoder.util.GaloisField;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Ported from HDFS-RAID
 */
public class TestJavaRSRawCoder {
  private static final int DATA_SIZE = 10;
  private static final int ERASED_INDEX = 0;

	public static final Log LOG = LogFactory.getLog(TestJavaRSRawCoder.class.getName());
	final Random RAND = new Random();
  private static GaloisField GF = GaloisField.getInstance();
  private static int symbolSize = 0;
	private static int CHUNK_SIZE = 1024 * 1024;

  static {
    symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
  }

	@Test
	public void testEncodeDecode() {
		// verify the production size.
		verifyJavaRSRawEncodeDecode(10, 4);

		// verify a test size
		verifyJavaRSRawEncodeDecode(3, 3);
	}

	private void verifyJavaRSRawEncodeDecode(int dataSize, int paritySize) {
		RawErasureEncoder rawEncoder = new JavaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
		RawErasureDecoder rawDecoder = new JavaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
		verifyRSEncodeDecode(rawEncoder, rawDecoder, dataSize, paritySize);
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
		rawDecoder.decode(dataForDecode, new int[] {erasedLocation + paritySize }, writeBufs);

		byte[] outputDataArray = new byte[bufsize];
		writeBufs[0].get(outputDataArray);
		Assert.assertTrue("Decode failed", Arrays.equals(rightDateInErasedLocation, outputDataArray));
	}

  @Test
  public void testECChunkCoding() {
    /**
     * Generate data and encode
     */
    byte[][] encodingData = generateEncodingData();
    ECChunk[] encodingChunks = toECChunks(encodingData);
    ECChunk[] parityChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};

    RawErasureEncoder encoder = new JavaRSRawEncoder(DATA_SIZE, CHUNK_SIZE);
    encoder.encode(encodingChunks, parityChunks);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(encodingData);

    //Decode
    byte[] parityData = parityChunks[0].getBuffer().array();
    byte[][] decodingData = generateDecodingData(encodingData, parityData);
    ECChunk[] decodingChunks = toECChunks(decodingData);
    ECChunk[] recoveredChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};
    RawErasureDecoder decoder = new JavaRSRawDecoder(DATA_SIZE, CHUNK_SIZE);
    decoder.decode(decodingChunks, new int[] {ERASED_INDEX}, recoveredChunks);

    //Compare
    byte[] recoveredData = recoveredChunks[0].getBuffer().array();
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData);
  }

  private byte[][] generateEncodingData() {
    byte[][] encodingData = new byte[DATA_SIZE][];
    for (int i = 0; i < DATA_SIZE; i++) {
      encodingData[i] = new byte[CHUNK_SIZE];
      for (int j = 0; j < CHUNK_SIZE; j++) {
        encodingData[i][j] = (byte)RAND.nextInt(256);
      }
    }
    return encodingData;
  }

  private byte[][] generateDecodingData(byte[][] encodingData, byte[] parityData) {
    byte[][] decodingData = new byte[DATA_SIZE + 1][];
    for (int i = 0; i < DATA_SIZE; i++) {
      decodingData[i] = encodingData[i];
    }
    decodingData[DATA_SIZE] = parityData;
    return decodingData;
  }

  private byte[] eraseData(byte[][] encodingData) {
    byte[] erasedData = new byte[CHUNK_SIZE];
    for (int j = 0; j < CHUNK_SIZE; j++) {
      erasedData[j] = encodingData[ERASED_INDEX][j];
      encodingData[ERASED_INDEX][j] = 0;
    }
    return erasedData;
  }

  private ByteBuffer[] toBuffers(byte[][] bytes) {
    ByteBuffer[] buffers = new ByteBuffer[bytes.length];
    for (int i = 0; i < buffers.length; i++) {
      buffers[i] = ByteBuffer.allocateDirect(bytes[i].length);
      buffers[i].put(bytes[i]);
      buffers[i].flip();
    }
    return buffers;
  }

  private ECChunk[] toECChunks(byte[][] bytes) {
    ECChunk[] ecChunks = new ECChunk[bytes.length];
    for (int i = 0; i < ecChunks.length; i++) {
      ecChunks[i] = new ECChunk(ByteBuffer.wrap(bytes[i]));
    }
    return ecChunks;
  }
}

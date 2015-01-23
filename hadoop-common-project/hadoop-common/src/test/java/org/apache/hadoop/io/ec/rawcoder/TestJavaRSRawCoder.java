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
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Ported from HDFS-RAID
 */
public class TestJavaRSRawCoder {
  public static final Log LOG = LogFactory.getLog(TestJavaRSRawCoder.class.getName());
  private final Random RAND = new Random();
  private static GaloisField GF = GaloisField.getInstance();

  private static final int CHUNK_SIZE = 16 * 1024;

  private int symbolMax;

  @Before
  public void init() {
    int symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
    symbolMax = (int) Math.pow(2, symbolSize);
  }

	@Test
	public void testCoding() {
      // verify the production size.
      testBytesCoding(10, 4);
      testECChunksCoding(10, 4);

      // verify a test size
      testBytesCoding(3, 3);
      testECChunksCoding(3, 3);
	}

  private void testBytesCoding(int dataSize, int paritySize) {
    /**
     * Generate data and encode
     */
    final byte[][] message = generateEncodingData(dataSize);
    byte[][] encodingData = new byte[dataSize][CHUNK_SIZE];
    for (int i = 0;i < dataSize; i++) {
      for (int j = 0;j < CHUNK_SIZE; j++) {
        encodingData[i][j] = message[i][j];
      }
    }
    byte[][] parityData = new byte[paritySize][CHUNK_SIZE];

    RawErasureEncoder encoder = new JavaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
    encoder.encode(encodingData, parityData);

    // Make a copy of a strip for later comparing then erase it
    int erasedLocation = RAND.nextInt(dataSize);
    byte[] erasedData = eraseData(message, erasedLocation);

    /**
     * Decode and compare
     */
    byte[][] decodingData = generateDecodingData(message, parityData);
    byte[][] recoveredData = new byte[][] {new byte[CHUNK_SIZE]};
    RawErasureDecoder decoder = new JavaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
    decoder.decode(decodingData, new int[] {erasedLocation + paritySize}, recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData[0]);
  }

  private void testECChunksCoding(int dataSize, int paritySize) {
    /**
     * Generate data and encode
     */
    byte[][] encodingData = generateEncodingData(dataSize);
    ECChunk[] encodingChunks = toECChunks(encodingData);
    ECChunk[] parityChunks = new ECChunk[paritySize];
    for (int i = 0;i < paritySize; i++) {
      parityChunks[i] = new ECChunk(ByteBuffer.allocateDirect(CHUNK_SIZE));
    }

    RawErasureEncoder encoder = new JavaRSRawEncoder(dataSize, paritySize, CHUNK_SIZE);
    encoder.encode(encodingChunks, parityChunks);

    // Make a copy of a strip for later comparing then erase it
    int erasedLocation = RAND.nextInt(encodingData.length);
    byte[] erasedData = eraseData(encodingData, erasedLocation);

    //Decode
    byte[][] parityData = toArrays(parityChunks);
    byte[][] decodingData = generateDecodingData(encodingData, parityData);
    ECChunk[] decodingChunks = toECChunks(decodingData);
    ECChunk[] recoveredChunks = new ECChunk[]{new ECChunk(ByteBuffer.allocateDirect(CHUNK_SIZE))};
    RawErasureDecoder decoder = new JavaRSRawDecoder(dataSize, paritySize, CHUNK_SIZE);
    decoder.decode(decodingChunks, new int[] {erasedLocation + paritySize}, recoveredChunks);

    //Compare
    byte[] recoveredData = new byte[CHUNK_SIZE];
    recoveredChunks[0].getBuffer().get(recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData);
  }


  private byte[][] generateEncodingData(int dataSize) {
    byte[][] encodingData = new byte[dataSize][];
    for (int i = 0; i < dataSize; i++) {
      encodingData[i] = new byte[CHUNK_SIZE];
      for (int j = 0; j < CHUNK_SIZE; j++) {
        encodingData[i][j] = (byte) RAND.nextInt(symbolMax);
      }
    }
    return encodingData;
  }

  private byte[][] generateDecodingData(byte[][] encodingData, byte[][] parityData) {
    byte[][] decodingData = new byte[encodingData.length + parityData.length][];
    for (int i = 0; i < parityData.length; i++) {
      decodingData[i] = parityData[i];
    }
    for (int i = 0; i < encodingData.length; i++) {
      decodingData[i + parityData.length] = encodingData[i];
    }
    return decodingData;
  }

  private byte[] eraseData(byte[][] encodingData, int erasedLocation) {
    byte[] erasedData = new byte[CHUNK_SIZE];
    for (int j = 0; j < CHUNK_SIZE; j++) {
      erasedData[j] = encodingData[erasedLocation][j];
      encodingData[erasedLocation][j] = 0;
    }
    return erasedData;
  }

  private ECChunk[] toECChunks(byte[][] bytes) {
    ECChunk[] ecChunks = new ECChunk[bytes.length];
    for (int i = 0; i < ecChunks.length; i++) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(bytes[i].length);
      buffer.put(bytes[i]);
      buffer.flip();
      ecChunks[i] = new ECChunk(buffer);
    }
    return ecChunks;
  }

  private byte[][] toArrays(ECChunk[] chunks) {
    byte[][] bytes = new byte[chunks.length][CHUNK_SIZE];
    for (int i = 0; i < chunks.length; i++) {
      ByteBuffer buffer = chunks[i].getBuffer();
      buffer.get(bytes[i]);
    }
    return bytes;
  }
}

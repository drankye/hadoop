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

import org.apache.hadoop.io.ec.ECChunk;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test XOR encoding and decoding, adapted from HDFS-RAID.
 */
public class TestXorRawEncoder {
  private Random RAND = new Random();
  private static final int DATA_SIZE = 10;
  private static final int CHUNK_SIZE = 16 * 1024;
  private static final int ERASED_INDEX = 0;

  @Test
  public void testBytesCoding() {
    /**
     * Generate data and encode
     */
    byte[][] encodingData = generateEncodingData();
    byte[][] parityData = new byte[][]{new byte[CHUNK_SIZE]};

    RawErasureEncoder encoder = new XorRawEncoder(DATA_SIZE, CHUNK_SIZE);
    encoder.encode(encodingData, parityData);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(encodingData);

    /**
     * Decode and compare
     */
    byte[][] decodingData = generateDecodingData(encodingData, parityData[0]);
    byte[][] recoveredData = new byte[][] {new byte[CHUNK_SIZE]};
    RawErasureDecoder decoder = new XorRawDecoder(DATA_SIZE, CHUNK_SIZE);
    decoder.decode(decodingData, new int[] {ERASED_INDEX}, recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData[0]);
  }

  @Test
  public void testBufferCoding() {
    /**
     * Generate data and encode
     */
    byte[][] encodingData = generateEncodingData();
    ByteBuffer[] encodingBuffers = toBuffers(encodingData);
    ByteBuffer[] parityBuffers = new ByteBuffer[]{ByteBuffer.allocateDirect(CHUNK_SIZE)};

    RawErasureEncoder encoder = new XorRawEncoder(DATA_SIZE, CHUNK_SIZE);
    encoder.encode(encodingBuffers, parityBuffers);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(encodingData);

    //Decode
    byte[] parityData = new byte[CHUNK_SIZE];
    parityBuffers[0].get(parityData);
    byte[][] decodingData = generateDecodingData(encodingData, parityData);
    ByteBuffer[] decodingBuffers = toBuffers(decodingData);
    ByteBuffer[] recoveredBuffers = new ByteBuffer[]{ByteBuffer.allocateDirect(CHUNK_SIZE)};
    RawErasureDecoder decoder = new XorRawDecoder(DATA_SIZE, CHUNK_SIZE);
    decoder.decode(decodingBuffers, new int[] {ERASED_INDEX}, recoveredBuffers);

    //Compare
    byte[] recoveredData = new byte[CHUNK_SIZE];
    recoveredBuffers[0].get(recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData);
  }

  @Test
  public void testECChunkCoding() {
    /**
     * Generate data and encode
     */
    byte[][] encodingData = generateEncodingData();
    ECChunk[] encodingChunks = toECChunks(encodingData);
    ECChunk[] parityChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};

    RawErasureEncoder encoder = new XorRawEncoder(DATA_SIZE, CHUNK_SIZE);
    encoder.encode(encodingChunks, parityChunks);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(encodingData);

    //Decode
    byte[] parityData = parityChunks[0].getBuffer().array();
    byte[][] decodingData = generateDecodingData(encodingData, parityData);
    ECChunk[] decodingChunks = toECChunks(decodingData);
    ECChunk[] recoveredChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};
    RawErasureDecoder decoder = new XorRawDecoder(DATA_SIZE, CHUNK_SIZE);
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
      buffers[i] = ByteBuffer.allocateDirect(CHUNK_SIZE);
      buffers[i].put(bytes[i]);
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

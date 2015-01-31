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
package org.apache.hadoop.io.erasurecode.rawcoder;

import org.apache.hadoop.io.erasurecode.ECChunk;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test XOR encoding and decoding.
 */
public class TestXorRawCoder {
  private Random RAND = new Random();
  private static final int NUM_DATA_UNITS = 10;
  private static final int NUM_PARITY_UNITS = 1;
  private static final int CHUNK_SIZE = 16 * 1024;
  private static final int ERASED_INDEX = 0;

  @Test
  public void testBytesCoding() {
    /**
     * Generate data and encode
     */
    byte[][] sourceData = generateSourceData();
    byte[][] parityData = new byte[][]{new byte[CHUNK_SIZE]};

    RawErasureEncoder encoder = createEncoder();
    encoder.encode(sourceData, parityData);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(sourceData);

    /**
     * Decode and compare
     */
    byte[][] decodingData = makeDecodingData(sourceData, parityData);
    byte[][] recoveredData = new byte[][] {new byte[CHUNK_SIZE]};
    RawErasureDecoder decoder = createDecoder();
    decoder.decode(decodingData, new int[] {ERASED_INDEX}, recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData[0]);
  }

  @Test
  public void testBufferCoding() {
    /**
     * Generate data and encode
     */
    byte[][] sourceData = generateSourceData();
    ByteBuffer[] sourceBuffers = toBuffers(sourceData);
    ByteBuffer[] parityBuffers = new ByteBuffer[]{ByteBuffer.allocateDirect(CHUNK_SIZE)};

    RawErasureEncoder encoder = createEncoder();
    encoder.encode(sourceBuffers, parityBuffers);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(sourceData);

    //Decode
    byte[][] parityData = new byte[][]{new byte[CHUNK_SIZE]};
    parityBuffers[0].get(parityData[0]);
    byte[][] decodingData = makeDecodingData(sourceData, parityData);
    ByteBuffer[] decodingBuffers = toBuffers(decodingData);
    ByteBuffer[] recoveredBuffers = new ByteBuffer[]{ByteBuffer.allocateDirect(CHUNK_SIZE)};
    RawErasureDecoder decoder = createDecoder();
    decoder.decode(decodingBuffers, new int[] {ERASED_INDEX}, recoveredBuffers);

    //Compare
    byte[] recoveredData = new byte[CHUNK_SIZE];
    recoveredBuffers[0].get(recoveredData);
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData);
  }

  @Test
  public void testChunkCoding() {
    /**
     * Generate data and encode
     */
    byte[][] sourceData = generateSourceData();
    ECChunk[] sourceChunks = toChunks(sourceData);
    ECChunk[] parityChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};

    RawErasureEncoder encoder = createEncoder();
    encoder.encode(sourceChunks, parityChunks);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = eraseData(sourceData);

    //Decode
    byte[] parityData = parityChunks[0].getBuffer().array();
    byte[][] decodingData = makeDecodingData(sourceData, parityData);
    ECChunk[] decodingChunks = toChunks(decodingData);
    ECChunk[] recoveredChunks = new ECChunk[]{new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]))};
    RawErasureDecoder decoder = createDecoder();
    decoder.decode(decodingChunks, new int[] {ERASED_INDEX}, recoveredChunks);

    //Compare
    byte[] recoveredData = recoveredChunks[0].getBuffer().array();
    assertArrayEquals("Decoding and comparing failed.", erasedData, recoveredData);
  }

  private byte[][] generateSourceData() {
    byte[][] sourceData = new byte[NUM_DATA_UNITS][];
    for (int i = 0; i < NUM_DATA_UNITS; i++) {
      sourceData[i] = new byte[CHUNK_SIZE];
      for (int j = 0; j < CHUNK_SIZE; j++) {
        sourceData[i][j] = (byte)RAND.nextInt(256);
      }
    }
    return sourceData;
  }

  private byte[][] makeDecodingData(byte[][] sourceData, byte[][] parityData) {
    byte[][] decodingData = new byte[NUM_DATA_UNITS + NUM_PARITY_UNITS][];
    
    int idx = 0;
    for (int i = 0; i < NUM_DATA_UNITS; i++) {
      decodingData[idx ++] = sourceData[i];
    }
    for (int i = 0; i < NUM_PARITY_UNITS; i++) {
      decodingData[idx ++] = parityData[i];
    }
    
    return decodingData;
  }

  private byte[] eraseData(byte[][] sourceData) {
    byte[] erasedData = new byte[CHUNK_SIZE];
    for (int j = 0; j < CHUNK_SIZE; j++) {
      erasedData[j] = sourceData[ERASED_INDEX][j];
      sourceData[ERASED_INDEX][j] = 0;
    }
    return erasedData;
  }

  private RawErasureEncoder createEncoder() {
    RawErasureEncoder encoder = new XorRawEncoder();
    encoder.initialize(NUM_DATA_UNITS, NUM_PARITY_UNITS, CHUNK_SIZE);
    return encoder;
  }

  private RawErasureDecoder createDecoder() {
    RawErasureDecoder decoder = new XorRawDecoder();
    decoder.initialize(NUM_DATA_UNITS, NUM_PARITY_UNITS, CHUNK_SIZE);
    return decoder;
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

  private ECChunk[] toChunks(byte[][] bytes) {
    ECChunk[] ecChunks = new ECChunk[bytes.length];
    for (int i = 0; i < ecChunks.length; i++) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(bytes[i].length);
      buffer.put(bytes[i]);
      buffer.flip();
      ecChunks[i] = new ECChunk(buffer);
    }
    return ecChunks;
  }

}

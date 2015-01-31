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

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Raw coder test base with utilities.
 */
public abstract class TestRawCoderBase {
  protected static Random RAND = new Random();
  protected static int NUM_DATA_UNITS;
  protected static int NUM_PARITY_UNITS;
  protected static int CHUNK_SIZE = 16 * 1024;
  protected static int[] ERASED_INDEXES = new int[] {0};

  protected void testCoding(boolean usingDirectBuffer) {
    // Generate data and encode
    ECChunk[] sourceChunks = prepareSourceChunks(usingDirectBuffer);
    ECChunk[] parityChunks = prepareParityChunks(usingDirectBuffer);
    RawErasureEncoder encoder = createEncoder();
    encoder.encode(sourceChunks, parityChunks);

    // Make a copy of a strip for later comparing then erase it
    byte[][] erasedSources = copyAndEraseSources(sourceChunks);

    //Decode
    ECChunk[] inputChunks = prepareInputChunks(sourceChunks, parityChunks);
    ECChunk[] recoveredChunks = prepareOutputChunks(usingDirectBuffer);
    RawErasureDecoder decoder = createDecoder();
    decoder.decode(inputChunks, ERASED_INDEXES, recoveredChunks);

    //Compare
    compareAndVerify(erasedSources, recoveredChunks);
  }

  private static void compareAndVerify(byte[][] erasedSources, ECChunk[] recoveredChunks) {
    byte[][] recoveredSources = ECChunk.toArray(recoveredChunks);
    for (int i = 0; i < erasedSources.length; ++i) {
      assertArrayEquals("Decoding and comparing failed.", erasedSources[i], recoveredSources[i]);
    }
  }

  private static ECChunk[] prepareInputChunks(ECChunk[] sourceChunks, ECChunk[] parityChunks) {
    ECChunk[] inputChunks = new ECChunk[NUM_DATA_UNITS + NUM_PARITY_UNITS];
    
    int idx = 0;
    for (int i = 0; i < NUM_DATA_UNITS; i++) {
      inputChunks[idx ++] = sourceChunks[i];
    }
    for (int i = 0; i < NUM_PARITY_UNITS; i++) {
      inputChunks[idx ++] = parityChunks[i];
    }
    
    return inputChunks;
  }

  private static byte[][] copyAndEraseSources(ECChunk[] sourceChunks) {
    byte[][] erasedSources = new byte[ERASED_INDEXES.length][];

    for (int i = 0; i < ERASED_INDEXES.length; ++i) {
      erasedSources[i] = copyAndEraseSource(sourceChunks, ERASED_INDEXES[i]);
    }

    return erasedSources;
  }

  private static byte[] copyAndEraseSource(ECChunk[] sourceChunks, int erasedIndex) {
    byte[] erasedData = new byte[CHUNK_SIZE];
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // copy data out
    chunkBuffer.position(0);
    chunkBuffer.get(erasedData);

    // erase the data
    chunkBuffer.clear();
    for (int i = 0; i < CHUNK_SIZE; ++i) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();

    return erasedData;
  }

  protected abstract RawErasureEncoder createEncoder();

  protected abstract RawErasureDecoder createDecoder();

  private static ECChunk allocateChunk(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(length, usingDirectBuffer);

    return new ECChunk(buffer);
  }

  private static ByteBuffer allocateBuffer(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = usingDirectBuffer ? ByteBuffer.allocateDirect(length) :
        ByteBuffer.allocate(length);

    return buffer;
  }

  private static ECChunk[] prepareOutputChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[ERASED_INDEXES.length];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(CHUNK_SIZE, usingDirectBuffer);
    }

    return chunks;
  }

  private static ECChunk[] prepareParityChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[NUM_PARITY_UNITS];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(CHUNK_SIZE, usingDirectBuffer);
    }

    return chunks;
  }

  private static ECChunk[] prepareSourceChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[NUM_DATA_UNITS];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateSourceChunk(usingDirectBuffer);
    }

    return chunks;
  }

  private static ECChunk generateSourceChunk(boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(CHUNK_SIZE, usingDirectBuffer);
    for (int i = 0; i < CHUNK_SIZE; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }
}

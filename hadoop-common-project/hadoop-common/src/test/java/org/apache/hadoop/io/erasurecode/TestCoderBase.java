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
package org.apache.hadoop.io.erasurecode;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

/**
 * Raw coder test base with utilities.
 */
public abstract class TestCoderBase {
  protected static Random RAND = new Random();

  protected int numDataUnits;
  protected int numParityUnits;
  protected int chunkSize = 16 * 1024;
  protected int[] erasedIndexes = new int[] {0};
  protected boolean usingDirectBuffer = true;

  protected void compareAndVerify(byte[][] erasedSources,
                                ECChunk[] recoveredChunks) {
    byte[][] recoveredSources = ECChunk.toArray(recoveredChunks);
    for (int i = 0; i < erasedSources.length; ++i) {
      assertArrayEquals("Decoding and comparing failed.", erasedSources[i],
          recoveredSources[i]);
    }
  }

  protected int[] getErasedIndexesForDecoding() {
    int[] erasedIndexesForDecoding = new int[erasedIndexes.length];
    for (int i = 0; i < erasedIndexes.length; ++i) {
      erasedIndexesForDecoding[i] = erasedIndexes[i] + numParityUnits;
    }
    return erasedIndexesForDecoding;
  }

  protected ECChunk[] prepareInputChunksForDecoding(ECChunk[] sourceChunks,
                                                  ECChunk[] parityChunks) {
    ECChunk[] inputChunks = new ECChunk[numDataUnits + numParityUnits];
    
    int idx = 0;
    for (int i = 0; i < numParityUnits; i++) {
      inputChunks[idx ++] = parityChunks[i];
    }
    for (int i = 0; i < numDataUnits; i++) {
      inputChunks[idx ++] = sourceChunks[i];
    }
    
    return inputChunks;
  }

  protected byte[][] copyToBeErasedSources(ECChunk[] sourceChunks) {
    byte[][] copiedSources = new byte[erasedIndexes.length][];

    for (int i = 0; i < erasedIndexes.length; ++i) {
      copiedSources[i] = copyToBeErasedSource(sourceChunks, erasedIndexes[i]);
    }

    return copiedSources;
  }

  protected void eraseSources(ECChunk[] sourceChunks) {
    for (int i = 0; i < erasedIndexes.length; ++i) {
      eraseSource(sourceChunks, erasedIndexes[i]);
    }
  }

  protected void eraseSource(ECChunk[] sourceChunks, int erasedIndex) {
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // erase the data
    chunkBuffer.position(0);
    for (int i = 0; i < chunkSize; ++i) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();
  }

  protected byte[] copyToBeErasedSource(ECChunk[] sourceChunks, int erasedIndex) {
    byte[] copiedData = new byte[chunkSize];
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // copy data out
    chunkBuffer.position(0);
    chunkBuffer.get(copiedData);
    chunkBuffer.position(0);

    return copiedData;
  }

  protected static ECChunk[] cloneDataChunks(ECChunk[] sourceChunks) {
    ECChunk[] results = new ECChunk[sourceChunks.length];
    for (int i = 0; i < sourceChunks.length; ++i) {
      results[i] = cloneDataChunk(sourceChunks[i]);
    }

    return results;
  }

  /**
   * Clone exactly a chunk, avoiding affecting the original chunk.
   * @param chunk
   * @return a new chunk
   */
  protected static ECChunk cloneDataChunk(ECChunk chunk) {
    ByteBuffer srcBuffer = chunk.getBuffer();
    ByteBuffer destBuffer;

    byte[] bytesArr = new byte[srcBuffer.remaining()];
    srcBuffer.mark();
    srcBuffer.get(bytesArr);
    srcBuffer.reset();

    if (srcBuffer.hasArray()) {
      destBuffer = ByteBuffer.wrap(bytesArr);
    } else {
      destBuffer = ByteBuffer.allocateDirect(srcBuffer.remaining());
      destBuffer.put(bytesArr);
      destBuffer.flip();
    }

    return new ECChunk(destBuffer);
  }

  protected ECChunk allocateChunk() {
    ByteBuffer buffer = allocateBuffer();

    return new ECChunk(buffer);
  }

  protected ByteBuffer allocateBuffer() {
    ByteBuffer buffer = usingDirectBuffer ? ByteBuffer.allocateDirect(chunkSize) :
        ByteBuffer.allocate(chunkSize);

    return buffer;
  }

  protected ECChunk[] prepareOutputChunksForDecoding() {
    ECChunk[] chunks = new ECChunk[erasedIndexes.length];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk();
    }

    return chunks;
  }

  protected ECChunk[] prepareParityChunks() {
    ECChunk[] chunks = new ECChunk[numParityUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk();
    }

    return chunks;
  }

  protected ECChunk[] prepareSourceChunks() {
    ECChunk[] chunks = new ECChunk[numDataUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateDataChunk();
    }

    return chunks;
  }

  protected ECChunk generateDataChunk() {
    ByteBuffer buffer = allocateBuffer();
    for (int i = 0; i < chunkSize; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }
}

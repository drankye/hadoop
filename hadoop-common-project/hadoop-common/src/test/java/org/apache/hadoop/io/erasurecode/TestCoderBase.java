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
  // Indexes of erased data units. Will also support test of erasing
  // parity units
  protected int[] erasedDataIndexes = new int[] {0};
  protected boolean usingDirectBuffer = true;

  protected void compareAndVerify(ECChunk[] erasedChunks,
                                  ECChunk[] recoveredChunks) {
    byte[][] erased = ECChunk.toArray(erasedChunks);
    byte[][] recovered = ECChunk.toArray(recoveredChunks);
    for (int i = 0; i < erasedChunks.length; ++i) {
      assertArrayEquals("Decoding and comparing failed.", erased[i],
          recovered[i]);
    }
  }

  protected int[] getErasedIndexesForDecoding() {
    int[] erasedIndexesForDecoding = new int[erasedDataIndexes.length];
    for (int i = 0; i < erasedDataIndexes.length; ++i) {
      erasedIndexesForDecoding[i] = erasedDataIndexes[i] + numParityUnits;
    }
    return erasedIndexesForDecoding;
  }

  protected ECChunk[] prepareInputChunksForDecoding(ECChunk[] dataChunks,
                                                  ECChunk[] parityChunks) {
    ECChunk[] inputChunks = new ECChunk[numParityUnits + numDataUnits];
    
    int idx = 0;
    for (int i = 0; i < numParityUnits; i++) {
      inputChunks[idx ++] = parityChunks[i];
    }
    for (int i = 0; i < numDataUnits; i++) {
      inputChunks[idx ++] = dataChunks[i];
    }
    
    return inputChunks;
  }

  protected ECChunk[] copyDataChunksToErase(ECChunk[] dataChunks) {
    ECChunk[] copiedChunks = new ECChunk[erasedDataIndexes.length];

    int j = 0;
    for (int i = 0; i < erasedDataIndexes.length; ++i) {
      copiedChunks[j ++] = cloneChunkWithData(dataChunks[erasedDataIndexes[i]]);
    }

    return copiedChunks;
  }

  protected void eraseSomeDataBlocks(ECChunk[] dataChunks) {
    for (int i = 0; i < erasedDataIndexes.length; ++i) {
      eraseDataFromChunk(dataChunks[erasedDataIndexes[i]]);
    }
  }

  protected void eraseDataFromChunks(ECChunk[] chunks) {
    for (int i = 0; i < chunks.length; ++i) {
      eraseDataFromChunk(chunks[i]);
    }
  }

  protected void eraseDataFromChunk(ECChunk chunk) {
    ByteBuffer chunkBuffer = chunk.getBuffer();
    // erase the data
    chunkBuffer.position(0);
    for (int i = 0; i < chunkSize; ++i) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();
  }

  protected static ECChunk[] cloneChunksWithData(ECChunk[] chunks) {
    ECChunk[] results = new ECChunk[chunks.length];
    for (int i = 0; i < chunks.length; ++i) {
      results[i] = cloneChunkWithData(chunks[i]);
    }

    return results;
  }

  /**
   * Clone exactly a chunk, avoiding affecting the original chunk.
   * @param chunk
   * @return a new chunk
   */
  protected static ECChunk cloneChunkWithData(ECChunk chunk) {
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

  protected ECChunk allocateOutputChunk() {
    ByteBuffer buffer = allocateOutputBuffer();

    return new ECChunk(buffer);
  }

  protected ByteBuffer allocateOutputBuffer() {
    ByteBuffer buffer = usingDirectBuffer ?
        ByteBuffer.allocateDirect(chunkSize) : ByteBuffer.allocate(chunkSize);

    return buffer;
  }

  protected ECChunk[] prepareOutputChunksForDecoding() {
    ECChunk[] chunks = new ECChunk[erasedDataIndexes.length];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateOutputChunk();
    }

    return chunks;
  }

  protected ECChunk[] prepareParityChunksForEncoding() {
    ECChunk[] chunks = new ECChunk[numParityUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateOutputChunk();
    }

    return chunks;
  }

  protected ECChunk[] prepareDataChunksForEncoding() {
    ECChunk[] chunks = new ECChunk[numDataUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateDataChunk();
    }

    return chunks;
  }

  protected ECChunk generateDataChunk() {
    ByteBuffer buffer = allocateOutputBuffer();
    for (int i = 0; i < chunkSize; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }
}

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
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test base of common utilities for tests not only raw coders but also block
 * coders.
 */
public abstract class TestCoderBase {
  protected static Random RAND = new Random();

  protected int numDataUnits;
  protected int numParityUnits;
  protected int chunkSize = 16 * 1024;

  // Indexes of erased data units.
  protected int[] erasedDataIndexes;

  // Indexes of erased parity units.
  protected int[] erasedParityIndexes;

  // Data buffers are either direct or on-heap, for performance the two cases
  // may go to different coding implementations.
  protected boolean usingDirectBuffer = true;

  protected void prepare(int numDataUnits, int numParityUnits,
                         int[] erasedDataIndexes, int[] erasedParityIndexes) {
    this.numDataUnits = numDataUnits;
    this.numParityUnits = numParityUnits;
    this.erasedDataIndexes = erasedDataIndexes != null ?
        erasedDataIndexes : new int[0];
    this.erasedParityIndexes = erasedParityIndexes != null ?
        erasedParityIndexes : new int[0];
  }

  /**
   * Compare and verify if erased chunks are equal to recovered chunks
   * @param erasedChunks
   * @param recoveredChunks
   */
  protected void compareAndVerify(ECChunk[] erasedChunks,
                                  ECChunk[] recoveredChunks) {
    byte[][] erased = ECChunk.toArray(erasedChunks);
    byte[][] recovered = ECChunk.toArray(recoveredChunks);
    boolean result = Arrays.deepEquals(erased, recovered);
    assertTrue("Decoding and comparing failed.", result);
  }

  protected ECChunk[] join(ECChunk[] chunks1, ECChunk[] chunks2) {
    ECChunk[] results = new ECChunk[chunks1.length + chunks2.length];
    if (chunks1.length > 0) {
      System.arraycopy(chunks1, 0, results, 0, chunks1.length);
      if (chunks2.length > 0) {
        System.arraycopy(chunks2, 0, results, chunks1.length, chunks2.length);
      }
    } else if (chunks2.length > 0) {
      System.arraycopy(chunks2, 0, results, 0, chunks2.length);
    }
    return results;
  }

  /**
   * Adjust and return erased indexes based on the array of the input chunks (
   * parity chunks + data chunks).
   * @return
   */
  protected int[] getErasedIndexesForDecoding() {
    int erasedCount = erasedDataIndexes.length + erasedParityIndexes.length;
    int[] erasedIndexesForDecoding = new int[erasedCount];
    for (int i = 0; i < erasedParityIndexes.length; i++) {
      erasedIndexesForDecoding[i] = erasedParityIndexes[i];
    }
    for (int i = 0; i < erasedDataIndexes.length; i++) {
      erasedIndexesForDecoding[i] = erasedDataIndexes[i] + numParityUnits;
    }
    return erasedIndexesForDecoding;
  }

  /**
   * Return input chunks for decoding, which is parityChunks + dataChunks.
   * @param dataChunks
   * @param parityChunks
   * @return
   */
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

  /**
   * Have a copy of the data chunks that's to be erased thereafter. The copy
   * will be used to compare and verify with the to be recovered chunks.
   * @param dataChunks
   * @return
   */
  protected ECChunk[] copyDataChunksToErase(ECChunk[] dataChunks) {
    ECChunk[] copiedChunks = new ECChunk[erasedDataIndexes.length];

    int j = 0;
    for (int i = 0; i < erasedDataIndexes.length; i++) {
      copiedChunks[j ++] = cloneChunkWithData(dataChunks[erasedDataIndexes[i]]);
    }

    return copiedChunks;
  }

  /**
   * Have a copy of the parity chunks that's to be erased thereafter. The copy
   * will be used to compare and verify with the to be recovered chunks.
   * @param parityChunks
   * @return
   */
  protected ECChunk[] copyParityChunksToErase(ECChunk[] parityChunks) {
    ECChunk[] copiedChunks = new ECChunk[erasedParityIndexes.length];

    int j = 0;
    for (int i = 0; i < erasedParityIndexes.length; i++) {
      copiedChunks[j ++] = cloneChunkWithData(
          parityChunks[erasedParityIndexes[i]]);
    }

    return copiedChunks;
  }

  /**
   * Erase some data chunks to test the recovering of them
   * @param dataChunks
   */
  protected void eraseSomeDataBlocks(ECChunk[] dataChunks) {
    for (int i = 0; i < erasedDataIndexes.length; i++) {
      eraseDataFromChunk(dataChunks[erasedDataIndexes[i]]);
    }
  }

  /**
   * Erase data from the specified chunks, putting ZERO bytes to the buffers.
   * @param chunks
   */
  protected void eraseDataFromChunks(ECChunk[] chunks) {
    for (int i = 0; i < chunks.length; i++) {
      eraseDataFromChunk(chunks[i]);
    }
  }

  /**
   * Erase data from the specified chunk, putting ZERO bytes to the buffer.
   * @param chunk
   */
  protected void eraseDataFromChunk(ECChunk chunk) {
    ByteBuffer chunkBuffer = chunk.getBuffer();
    // erase the data
    chunkBuffer.position(0);
    for (int i = 0; i < chunkSize; i++) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();
  }

  /**
   * Clone chunks along with copying the associated data. It respects how the
   * chunk buffer is allocated, direct or non-direct. It avoids affecting the
   * original chunk buffers.
   * @param chunks
   * @return
   */
  protected static ECChunk[] cloneChunksWithData(ECChunk[] chunks) {
    ECChunk[] results = new ECChunk[chunks.length];
    for (int i = 0; i < chunks.length; i++) {
      results[i] = cloneChunkWithData(chunks[i]);
    }

    return results;
  }

  /**
   * Clone chunk along with copying the associated data. It respects how the
   * chunk buffer is allocated, direct or non-direct. It avoids affecting the
   * original chunk.
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

  /**
   * Allocate a chunk for output or writing.
   * @return
   */
  protected ECChunk allocateOutputChunk() {
    ByteBuffer buffer = allocateOutputBuffer();

    return new ECChunk(buffer);
  }

  /**
   * Allocate a buffer for output or writing.
   * @return
   */
  protected ByteBuffer allocateOutputBuffer() {
    ByteBuffer buffer = usingDirectBuffer ?
        ByteBuffer.allocateDirect(chunkSize) : ByteBuffer.allocate(chunkSize);

    return buffer;
  }

  /**
   * Prepare data chunks for each data unit, by generating random data.
   * @return
   */
  protected ECChunk[] prepareDataChunksForEncoding() {
    ECChunk[] chunks = new ECChunk[numDataUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateDataChunk();
    }

    return chunks;
  }

  /**
   * Generate data chunk by making random data.
   * @return
   */
  protected ECChunk generateDataChunk() {
    ByteBuffer buffer = allocateOutputBuffer();
    for (int i = 0; i < chunkSize; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }

  /**
   * Prepare parity chunks for encoding, each chunk for each parity unit.
   * @return
   */
  protected ECChunk[] prepareParityChunksForEncoding() {
    ECChunk[] chunks = new ECChunk[numParityUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateOutputChunk();
    }

    return chunks;
  }

  /**
   * Prepare output chunks for decoding, each output chunk for each erased
   * chunk.
   * @return
   */
  protected ECChunk[] prepareOutputChunksForDecoding() {
    int erasedCount = erasedDataIndexes.length + erasedParityIndexes.length;
    ECChunk[] chunks = new ECChunk[erasedCount];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateOutputChunk();
    }

    return chunks;
  }

}

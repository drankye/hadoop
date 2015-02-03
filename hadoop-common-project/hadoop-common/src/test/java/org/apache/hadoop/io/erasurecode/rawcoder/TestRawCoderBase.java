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
  protected Class<? extends RawErasureEncoder> encoderClass;
  protected Class<? extends RawErasureDecoder> decoderClass;

  protected int numDataUnits;
  protected int numParityUnits;
  protected int chunkSize = 16 * 1024;
  protected int[] erasedIndexes = new int[] {0};

  /**
   * Generating source data, encoding, recovering and then verifying.
   * RawErasureCoder mainly uses ECChunk to pass input and output data buffers,
   * it supports two kinds of ByteBuffers, one is array backed, the other is
   * direct ByteBuffer. Have usingDirectBuffer to indicate which case to test.
   * @param usingDirectBuffer
   */
  protected void testCoding(boolean usingDirectBuffer) {
    // Generate data and encode
    ECChunk[] sourceChunks = prepareSourceChunks(usingDirectBuffer);
    ECChunk[] parityChunks = prepareParityChunks(usingDirectBuffer);
    RawErasureEncoder encoder = createEncoder();

    // Backup all the source chunks for later recovering because some coders
    // may affect the source data.
    ECChunk[] clonedSources = cloneChunks(sourceChunks);
    // Make a copy of a strip for later comparing
    byte[][] erasedSources = copyToBeErasedSources(clonedSources);

    encoder.encode(sourceChunks, parityChunks);
    // Erase the copied sources
    eraseSources(clonedSources);

    //Decode
    ECChunk[] inputChunks = prepareInputChunksForDecoding(clonedSources,
        parityChunks);
    ECChunk[] recoveredChunks =
        prepareOutputChunksForDecoding(usingDirectBuffer);
    RawErasureDecoder decoder = createDecoder();
    decoder.decode(inputChunks, getErasedIndexesForDecoding(), recoveredChunks);

    //Compare
    compareAndVerify(erasedSources, recoveredChunks);
  }

  private void compareAndVerify(byte[][] erasedSources,
                                ECChunk[] recoveredChunks) {
    byte[][] recoveredSources = ECChunk.toArray(recoveredChunks);
    for (int i = 0; i < erasedSources.length; ++i) {
      assertArrayEquals("Decoding and comparing failed.", erasedSources[i],
          recoveredSources[i]);
    }
  }

  private int[] getErasedIndexesForDecoding() {
    int[] erasedIndexesForDecoding = new int[erasedIndexes.length];
    for (int i = 0; i < erasedIndexes.length; ++i) {
      erasedIndexesForDecoding[i] = erasedIndexes[i] + numParityUnits;
    }
    return erasedIndexesForDecoding;
  }

  private ECChunk[] prepareInputChunksForDecoding(ECChunk[] sourceChunks,
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

  private byte[][] copyToBeErasedSources(ECChunk[] sourceChunks) {
    byte[][] copiedSources = new byte[erasedIndexes.length][];

    for (int i = 0; i < erasedIndexes.length; ++i) {
      copiedSources[i] = copyToBeErasedSource(sourceChunks, erasedIndexes[i]);
    }

    return copiedSources;
  }

  private void eraseSources(ECChunk[] sourceChunks) {
    for (int i = 0; i < erasedIndexes.length; ++i) {
      eraseSource(sourceChunks, erasedIndexes[i]);
    }
  }

  private void eraseSource(ECChunk[] sourceChunks, int erasedIndex) {
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // erase the data
    chunkBuffer.position(0);
    for (int i = 0; i < chunkSize; ++i) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();
  }

  private byte[] copyToBeErasedSource(ECChunk[] sourceChunks, int erasedIndex) {
    byte[] copiedData = new byte[chunkSize];
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // copy data out
    chunkBuffer.position(0);
    chunkBuffer.get(copiedData);
    chunkBuffer.position(0);

    return copiedData;
  }

  private static ECChunk[] cloneChunks(ECChunk[] sourceChunks) {
    ECChunk[] results = new ECChunk[sourceChunks.length];
    for (int i = 0; i < sourceChunks.length; ++i) {
      results[i] = cloneChunk(sourceChunks[i]);
    }

    return results;
  }

  /**
   * Clone exactly a chunk, avoiding affecting the original chunk.
   * @param chunk
   * @return a new chunk
   */
  private static ECChunk cloneChunk(ECChunk chunk) {
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
   * Create the raw erasure encoder to test
   * @return
   */
  protected RawErasureEncoder createEncoder() {
    RawErasureEncoder encoder;
    try {
      encoder = encoderClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create encoder", e);
    }

    encoder.initialize(numDataUnits, numParityUnits, chunkSize);
    return encoder;
  }

  /**
   * create the raw erasure decoder to test
   * @return
   */
  protected RawErasureDecoder createDecoder() {
    RawErasureDecoder decoder;
    try {
      decoder = decoderClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create decoder", e);
    }

    decoder.initialize(numDataUnits, numParityUnits, chunkSize);
    return decoder;
  }

  protected ECChunk allocateChunk(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(length, usingDirectBuffer);

    return new ECChunk(buffer);
  }

  protected ByteBuffer allocateBuffer(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = usingDirectBuffer ? ByteBuffer.allocateDirect(length) :
        ByteBuffer.allocate(length);

    return buffer;
  }

  protected ECChunk[] prepareOutputChunksForDecoding(
      boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[erasedIndexes.length];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(chunkSize, usingDirectBuffer);
    }

    return chunks;
  }

  protected ECChunk[] prepareParityChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[numParityUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(chunkSize, usingDirectBuffer);
    }

    return chunks;
  }

  protected ECChunk[] prepareSourceChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[numDataUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateSourceChunk(usingDirectBuffer);
    }

    return chunks;
  }

  protected ECChunk generateSourceChunk(boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(chunkSize, usingDirectBuffer);
    for (int i = 0; i < chunkSize; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }
}

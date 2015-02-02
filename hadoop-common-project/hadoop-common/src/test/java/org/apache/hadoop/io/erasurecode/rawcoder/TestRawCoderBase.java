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
    decoder.decode(inputChunks, erasedIndexes, recoveredChunks);

    //Compare
    compareAndVerify(erasedSources, recoveredChunks);
  }

  private void compareAndVerify(byte[][] erasedSources, ECChunk[] recoveredChunks) {
    byte[][] recoveredSources = ECChunk.toArray(recoveredChunks);
    for (int i = 0; i < erasedSources.length; ++i) {
      assertArrayEquals("Decoding and comparing failed.", erasedSources[i], recoveredSources[i]);
    }
  }

  private ECChunk[] prepareInputChunks(ECChunk[] sourceChunks, ECChunk[] parityChunks) {
    ECChunk[] inputChunks = new ECChunk[numDataUnits + numParityUnits];
    
    int idx = 0;
    for (int i = 0; i < numDataUnits; i++) {
      inputChunks[idx ++] = sourceChunks[i];
    }
    for (int i = 0; i < numParityUnits; i++) {
      inputChunks[idx ++] = parityChunks[i];
    }
    
    return inputChunks;
  }

  private byte[][] copyAndEraseSources(ECChunk[] sourceChunks) {
    byte[][] erasedSources = new byte[erasedIndexes.length][];

    for (int i = 0; i < erasedIndexes.length; ++i) {
      erasedSources[i] = copyAndEraseSource(sourceChunks, erasedIndexes[i]);
    }

    return erasedSources;
  }

  private byte[] copyAndEraseSource(ECChunk[] sourceChunks, int erasedIndex) {
    byte[] erasedData = new byte[chunkSize];
    ByteBuffer chunkBuffer = sourceChunks[erasedIndex].getBuffer();
    // copy data out
    chunkBuffer.position(0);
    chunkBuffer.get(erasedData);

    // erase the data
    chunkBuffer.clear();
    for (int i = 0; i < chunkSize; ++i) {
      chunkBuffer.put((byte) 0);
    }
    chunkBuffer.flip();

    return erasedData;
  }

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

  private ECChunk allocateChunk(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(length, usingDirectBuffer);

    return new ECChunk(buffer);
  }

  private ByteBuffer allocateBuffer(int length, boolean usingDirectBuffer) {
    ByteBuffer buffer = usingDirectBuffer ? ByteBuffer.allocateDirect(length) :
        ByteBuffer.allocate(length);

    return buffer;
  }

  private ECChunk[] prepareOutputChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[erasedIndexes.length];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(chunkSize, usingDirectBuffer);
    }

    return chunks;
  }

  private ECChunk[] prepareParityChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[numParityUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = allocateChunk(chunkSize, usingDirectBuffer);
    }

    return chunks;
  }

  private ECChunk[] prepareSourceChunks(boolean usingDirectBuffer) {
    ECChunk[] chunks = new ECChunk[numDataUnits];
    for (int i = 0; i < chunks.length; i++) {
      chunks[i] = generateSourceChunk(usingDirectBuffer);
    }

    return chunks;
  }

  private ECChunk generateSourceChunk(boolean usingDirectBuffer) {
    ByteBuffer buffer = allocateBuffer(chunkSize, usingDirectBuffer);
    for (int i = 0; i < chunkSize; i++) {
      buffer.put((byte) RAND.nextInt(256));
    }
    buffer.flip();

    return new ECChunk(buffer);
  }
}

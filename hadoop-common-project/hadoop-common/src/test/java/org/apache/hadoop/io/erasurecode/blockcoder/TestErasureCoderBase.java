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
package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;
import org.apache.hadoop.io.erasurecode.ECGroup;
import org.apache.hadoop.io.erasurecode.TestCoderBase;

/**
 * Erasure coder test base with utilities.
 */
public abstract class TestErasureCoderBase extends TestCoderBase {
  protected Class<? extends ErasureEncoder> encoderClass;
  protected Class<? extends ErasureDecoder> decoderClass;

  protected int numChunksInBlock = 16;

  protected static class TestBlock extends ECBlock {
    private ECChunk[] chunks;
    private int chkIdx = 0;

    // For simple, just assume the block have the chunks already ready.
    // In practice we need to read/or chunks from/to the block.
    public TestBlock(ECChunk[] chunks) {
      this.chunks = chunks;
    }

    // This is like reading/writing a chunk from/to a block until end.
    public ECChunk nextChunk() {
      if (hasNext()) {
        return chunks[chkIdx];
      }
      return null;
    }

    public boolean hasNext() {
      return chkIdx < chunks.length;
    }
  }

  /**
   * Generating source data, encoding, recovering and then verifying.
   * RawErasureCoder mainly uses ECChunk to pass input and output data buffers,
   * it supports two kinds of ByteBuffers, one is array backed, the other is
   * direct ByteBuffer. Have usingDirectBuffer to indicate which case to test.
   * @param usingDirectBuffer
   */
  protected void testCoding(boolean usingDirectBuffer) {
    this.usingDirectBuffer = usingDirectBuffer;

    ErasureEncoder encoder = createEncoder();
    // Generate data and encode
    ECGroup blockGroup = prepareBlockGroupForEncoding();
    // Backup all the source chunks for later recovering because some coders
    // may affect the source data.
    TestBlock[] clonedDataBlocks = cloneBlocksWithData((TestBlock[])
        blockGroup.getDataBlocks());
    // Make a copy of a strip for later comparing
    TestBlock[] toEraseBlocks = copyDataBlocksToErase(clonedDataBlocks);

    encoder.encode(blockGroup);
    // Erase the copied sources
    eraseSomeDataBlocks(clonedDataBlocks);

    //Decode
    blockGroup = new ECGroup(clonedDataBlocks, blockGroup.getParityBlocks());
    TestBlock[] recoveredBlocks = prepareOutputBlocksForDecoding();
    ErasureDecoder decoder = createDecoder();
    decoder.decode(blockGroup);

    //Compare
    compareAndVerify(toEraseBlocks, recoveredBlocks);
  }

  protected void compareAndVerify(TestBlock[] erasedBlocks,
                                  TestBlock[] recoveredBlocks) {
    for (int i = 0; i < erasedBlocks.length; ++i) {
      compareAndVerify(erasedBlocks[i].chunks, recoveredBlocks[i].chunks);
    }
  }

  private ErasureEncoder createEncoder() {
    ErasureEncoder encoder;
    try {
      encoder = encoderClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create encoder", e);
    }

    encoder.initialize(numDataUnits, numParityUnits, chunkSize);
    return encoder;
  }

  private ErasureDecoder createDecoder() {
    ErasureDecoder decoder;
    try {
      decoder = decoderClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create decoder", e);
    }

    decoder.initialize(numDataUnits, numParityUnits, chunkSize);
    return decoder;
  }

  protected ECGroup prepareBlockGroupForEncoding() {
    ECBlock[] dataBlocks = new ECBlock[numDataUnits];
    ECBlock[] parityBlocks = new ECBlock[numParityUnits];

    for (int i = 0; i < numDataUnits; i++) {
      dataBlocks[i] = generateDataBlock();
    }

    return new ECGroup(dataBlocks, parityBlocks);
  }

  protected TestBlock[] prepareOutputBlocksForDecoding() {
    TestBlock[] blocks = new TestBlock[erasedIndexes.length];
    for (int i = 0; i < blocks.length; i++) {
      blocks[i] = allocateOutputBlock();
    }

    return blocks;
  }

  protected ECBlock generateDataBlock() {
    ECChunk[] chunks = new ECChunk[numChunksInBlock];

    for (int i = 0; i < numChunksInBlock; ++i) {
      chunks[i] = generateDataChunk();
    }

    return new TestBlock(chunks);
  }

  protected TestBlock[] copyDataBlocksToErase(TestBlock[] dataBlocks) {
    TestBlock[] copiedBlocks = new TestBlock[erasedIndexes.length];

    for (int i = 0; i < erasedIndexes.length; ++i) {
      copiedBlocks[i] = cloneBlockWithData(dataBlocks[erasedIndexes[i]]);
    }

    return copiedBlocks;
  }

  protected TestBlock allocateOutputBlock() {
    ECChunk[] chunks = new ECChunk[numChunksInBlock];

    for (int i = 0; i < numChunksInBlock; ++i) {
      chunks[i] = allocateOutputChunk();
    }

    return new TestBlock(chunks);
  }

  protected static TestBlock[] cloneBlocksWithData(TestBlock[] blocks) {
    TestBlock[] results = new TestBlock[blocks.length];
    for (int i = 0; i < blocks.length; ++i) {
      results[i] = cloneBlockWithData(blocks[i]);
    }

    return results;
  }

  /**
   * Clone exactly a block, avoiding affecting the original block.
   * @param block
   * @return a new block
   */
  protected static TestBlock cloneBlockWithData(TestBlock block) {
    ECChunk[] newChunks = cloneChunksWithData(block.chunks);

    return new TestBlock(newChunks);
  }

  protected void eraseSomeDataBlocks(TestBlock[] dataBlocks) {
    for (int i = 0; i < erasedIndexes.length; ++i) {
      eraseDataFromBlock(dataBlocks, erasedIndexes[i]);
    }
  }

  protected void eraseDataFromBlock(TestBlock[] blocks, int erasedIndex) {
    TestBlock theBlock = blocks[erasedIndex];
    eraseDataFromChunks(theBlock.chunks);
    theBlock.setMissing(true);
  }
}

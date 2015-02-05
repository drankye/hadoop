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
import org.apache.hadoop.io.erasurecode.ECBlockGroup;
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

    // For simple, just assume the block have the chunks already ready.
    // In practice we need to read/or chunks from/to the block.
    public TestBlock(ECChunk[] chunks) {
      this.chunks = chunks;
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
    ECBlockGroup blockGroup = prepareBlockGroupForEncoding();
    // Backup all the source chunks for later recovering because some coders
    // may affect the source data.
    TestBlock[] clonedDataBlocks = cloneBlocksWithData((TestBlock[])
        blockGroup.getDataBlocks());
    // Make a copy of a strip for later comparing
    TestBlock[] toEraseBlocks = copyDataBlocksToErase(clonedDataBlocks);

    ErasureCodingStep codingStep = encoder.encode(blockGroup);
    performEncodingStep(codingStep);
    // Erase the copied sources
    eraseSomeDataBlocks(clonedDataBlocks);

    //Decode
    blockGroup = new ECBlockGroup(clonedDataBlocks, blockGroup.getParityBlocks());
    ErasureDecoder decoder = createDecoder();
    codingStep = decoder.decode(blockGroup);
    TestBlock[] recoveredBlocks = performDecodingStep(codingStep);

    //Compare
    compareAndVerify(toEraseBlocks, recoveredBlocks);
  }

  private void performEncodingStep(ErasureCodingStep codingStep) {
    ECBlock[] inputBlocks = codingStep.getInputBlocks();
    ECBlock[] outputBlocks = codingStep.getOutputBlocks();
    ECChunk[] inputChunks = new ECChunk[inputBlocks.length];
    ECChunk[] outputChunks = new ECChunk[outputBlocks.length];

    for (int i = 0; i < numChunksInBlock; ++i) {
      for (int j = 0; j < inputBlocks.length; ++j) {
        inputChunks[j] = ((TestBlock) inputBlocks[j]).chunks[i];
      }

      for (int j = 0; j < outputBlocks.length; ++j) {
        outputChunks[j] = ((TestBlock) outputBlocks[j]).chunks[i];
      }

      codingStep.performCoding(inputChunks, outputChunks);
    }

    codingStep.finish();
  }

  private TestBlock[] performDecodingStep(ErasureCodingStep codingStep) {
    ECBlock[] inputBlocks = codingStep.getInputBlocks();
    ECBlock[] outputBlocks = codingStep.getOutputBlocks();

    ECChunk[] inputChunks = new ECChunk[inputBlocks.length];
    ECChunk[] outputChunks = new ECChunk[outputBlocks.length];

    TestBlock[] recoveredBlocks = new TestBlock[outputBlocks.length];
    for (int i = 0; i < recoveredBlocks.length; ++i) {
      recoveredBlocks[i] = new TestBlock(new ECChunk[numChunksInBlock]);
    }

    for (int i = 0; i < numChunksInBlock; ++i) {
      for (int j = 0; j < inputBlocks.length; ++j) {
        inputChunks[j] = ((TestBlock) inputBlocks[j]).chunks[i];
      }

      for (int j = 0; j < outputBlocks.length; ++j) {
        outputChunks[j] = allocateOutputChunk();
        recoveredBlocks[j].chunks[i] = outputChunks[j];
      }

      codingStep.performCoding(inputChunks, outputChunks);
    }

    codingStep.finish();

    return recoveredBlocks;
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

  protected ECBlockGroup prepareBlockGroupForEncoding() {
    ECBlock[] dataBlocks = new TestBlock[numDataUnits];
    ECBlock[] parityBlocks = new TestBlock[numParityUnits];

    for (int i = 0; i < numDataUnits; i++) {
      dataBlocks[i] = generateDataBlock();
    }

    for (int i = 0; i < numParityUnits; i++) {
      parityBlocks[i] = allocateOutputBlock();
    }

    return new ECBlockGroup(dataBlocks, parityBlocks);
  }

  protected TestBlock[] prepareOutputBlocksForDecoding() {
    TestBlock[] blocks = new TestBlock[erasedDataIndexes.length];
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
    TestBlock[] copiedBlocks = new TestBlock[erasedDataIndexes.length];

    for (int i = 0; i < erasedDataIndexes.length; ++i) {
      copiedBlocks[i] = cloneBlockWithData(dataBlocks[erasedDataIndexes[i]]);
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
    for (int i = 0; i < erasedDataIndexes.length; ++i) {
      eraseDataFromBlock(dataBlocks, erasedDataIndexes[i]);
    }
  }

  protected void eraseDataFromBlock(TestBlock[] blocks, int erasedIndex) {
    TestBlock theBlock = blocks[erasedIndex];
    eraseDataFromChunks(theBlock.chunks);
    theBlock.setErased(true);
  }
}

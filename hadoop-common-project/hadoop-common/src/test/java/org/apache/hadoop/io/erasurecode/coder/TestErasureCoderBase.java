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
package org.apache.hadoop.io.erasurecode.coder;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;
import org.apache.hadoop.io.erasurecode.blockcoder.AbstractErasureCoderCallback;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureCoderCallback;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureDecoder;
import org.apache.hadoop.io.erasurecode.blockcoder.ErasureEncoder;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public abstract class TestErasureCoderBase {
  protected static final String TEST_DIR =
      new File(System.getProperty("test.build.data", "/tmp")).getAbsolutePath();
  protected final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs").getAbsolutePath();
  
  protected static final String EC_CONF_PREFIX = "hadoop.io.ec.erasurecodec.codec.";
  protected int BLOCK_SIZE = 1024 * 1024;
  protected int CHUNK_SIZE;
  protected int BLOCK_CHUNK_SIZE_MULIPLE;


  protected Configuration conf;
  protected String codecName = "codec_for_test";
  protected String schemaName = "schema_for_test";

  protected byte[][] message;
  private BlockDataManager dataManager;

  protected abstract String getCodecClass();

  @Before
  public void init() throws IOException {
    int symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
    symbolMax = (int) Math.pow(2, symbolSize);

    initConf();
    conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
    conf.set(EC_CONF_PREFIX + codecName, getCodecClass());
  }

  protected void initConf() {
    conf = new Configuration();
  }

  protected void doTest() throws Exception {
    ECSchema schema = loadSchema(schemaName);
    assert(schema != null);
    CHUNK_SIZE = schema.getChunkSize();
    BLOCK_CHUNK_SIZE_MULIPLE = BLOCK_SIZE / CHUNK_SIZE;

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    BlockGrouper blockGrouper = codec.createBlockGrouper();
    int numDataBlocks = blockGrouper.getDataBlocks();
    int numParityBlocks = blockGrouper.getParityBlocks();

    dataManager = new BlockDataManager(numDataBlocks, numParityBlocks);
    message = new byte[numDataBlocks][];

    List<? extends ECBlockId> dataBlocks = prepareDataBlocks(numDataBlocks);
    List<? extends ECBlockId> parityBlocks = prepareParityBlocks(numParityBlocks);
    BlockGroup blockGroup = blockGrouper.makeBlockGroup(dataBlocks, parityBlocks);

    encode(schema, blockGroup);

    /**
     * Intently make some block 'missing'
     */
    int erasedLocation = RAND.nextInt(numDataBlocks);
    blockGroup.getSubGroups().get(0).getDataBlocks()[erasedLocation].setMissing(true);
    dataManager.eraseData(erasedLocation);

    boolean canRecovery = blockGrouper.anyRecoverable(blockGroup);
    if (!canRecovery) {
      assertTrue(false);
    }
    BlockGroup groupUsedToRecovery = blockGrouper.makeRecoverableGroup(blockGroup);

    decode(schema, groupUsedToRecovery);

    /**
     * Post check and see if it's right back
     */
    verifyData(erasedLocation, groupUsedToRecovery);
  }

  protected List<? extends ECBlockId> prepareDataBlocks(int numDataBlocks) throws Exception{
    List<ECBlockIdForTest> idLists = new ArrayList<ECBlockIdForTest>();
    for (int i = 0; i < numDataBlocks; i++) {
      byte[] byteArray = generateData(BLOCK_SIZE);
      message[i] = byteArray;
      dataManager.fillData(i, Arrays.copyOf(byteArray, byteArray.length));
      idLists.add(new ECBlockIdForTest(i));
    }
    return idLists;
  }

  protected byte[] generateData(int length) {
    byte[] byteArray = new byte[length];
    for (int j = 0; j < length; j++) {
      byteArray[j] = (byte) RAND.nextInt(symbolMax);
    }
    return byteArray;
  }

  protected List<? extends ECBlockId> prepareParityBlocks(int numParityBlocks) throws Exception{
    List<ECBlockIdForTest> ids = new ArrayList<ECBlockIdForTest>();
    for (int i = 0;i < numParityBlocks; i++) {
      ids.add(new ECBlockIdForTest(dataManager.parityBeginIndex() + i));
    }
    return ids;
  }

  protected void verifyData(int erasedLocation, BlockGroup blockGroup) throws Exception {
    byte[] rightData = message[erasedLocation];

    SubBlockGroup subBlockGroup = blockGroup.getSubGroups().iterator().next();
    ECBlock recoveryBlock = subBlockGroup.getDataBlocks()[erasedLocation];

    byte[] actualData = getBlockData(recoveryBlock);
    assertTrue(Arrays.equals(rightData, actualData));
  }

  protected byte[] getBlockData(ECBlock ecBlock) throws Exception {
    return dataManager.getData(ecBlock);
  }

  protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    encode(schema, blockGroup, new CallbackForTest());
  }

  protected void decode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    decode(schema, blockGroup, new CallbackForTest());
  }

  protected void encode(ECSchema schema, BlockGroup blockGroup, ErasureCoderCallback callback) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureEncoder encoder = codec.createEncoder();

    encoder.setCallback(callback);
    encoder.encode(blockGroup);
  }

  protected void decode(ECSchema schema, BlockGroup blockGroup, ErasureCoderCallback callback) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureDecoder decoder = codec.createDecoder();
    decoder.setCallback(callback);
    decoder.decode(blockGroup);
  }

  private class CallbackForTest extends AbstractErasureCoderCallback {
    private ECChunk[][] inputChunks;
    private ECChunk[][] outputChunks;
    private int readInputIndex;
    private int readOutputIndex;

    @Override
    public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
      /**
        * For simple, we prepare for and load all the chunks at this phase.
        * Actually chunks should be read one by one only when needed while encoding/decoding.
        */
      inputChunks = new ECChunk[BLOCK_CHUNK_SIZE_MULIPLE][];
      outputChunks = new ECChunk[BLOCK_CHUNK_SIZE_MULIPLE][];

      for (int i = 0; i < BLOCK_CHUNK_SIZE_MULIPLE; i++) {
        inputChunks[i] = getChunks(inputBlocks, i);
        outputChunks[i] = getChunks(outputBlocks, i);
      }
    }

    @Override
    public boolean hasNextInputs() {
      return readInputIndex < inputChunks.length;
    }

    @Override
    public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) {
      ECChunk[] readInputChunks = inputChunks[readInputIndex];
      readInputIndex++;
      return readInputChunks;
    }

    @Override
    public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks) {
      ECChunk[] readOutputChunks = outputChunks[readOutputIndex];
      readOutputIndex++;
      return readOutputChunks;
    }

    @Override
    public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
      for (int chunkIndex = 0; chunkIndex < outputChunks.length; chunkIndex++) {
        for (int i = 0; i < outputChunks[chunkIndex].length; i++) {
          ByteBuffer byteBuffer = outputChunks[chunkIndex][i].getChunkBuffer();
          byte[] buffer = new byte[CHUNK_SIZE];
          byteBuffer.get(buffer);
          ECBlock ecBlock = outputBlocks[i];
          dataManager.fillDataSegment(buffer, ecBlock, chunkIndex);
        }
      }
    }

    private ECChunk[] getChunks(ECBlock[] dataEcBlocks, int segmentIndex) {
      ECChunk[] chunks = new ECChunk[dataEcBlocks.length];
      for (int i = 0; i < dataEcBlocks.length; i++) {
        ECBlock ecBlock = dataEcBlocks[i];
        ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_SIZE);
        if (ecBlock.isMissing()) {
          //fill zero datas
          buffer.put(new byte[CHUNK_SIZE]);
          buffer.flip();
          chunks[i] = new ECChunk(buffer);
        } else {
          byte[] segmentData = dataManager.getDataSegment(ecBlock.getBlockId(), segmentIndex);
          assert(segmentData.length == CHUNK_SIZE);
          buffer.put(segmentData);
          buffer.flip();
          chunks[i] = new ECChunk(buffer);
        }
      }
      return chunks;
    }
  }

  private class BlockDataManager {
    private byte[][] data;//include data and parity
    private int numDataBlocks;

    public BlockDataManager(int numDataBlocks, int numParityBlocks) {
      this.numDataBlocks = numDataBlocks;
      data = new byte[numDataBlocks + numParityBlocks][BLOCK_SIZE];
    }

    public void fillData(int i, byte[] bytes) {
      data[i] = bytes;
    }

    public int parityBeginIndex() {
      return numDataBlocks;
    }

    public byte[] getData(ECBlock ecBlock) {
      ECBlockIdForTest idForTest = (ECBlockIdForTest) ecBlock.getBlockId();
      return data[idForTest.getId()];
    }

    public byte[] getDataSegment(ECBlockId id, int segmentIndex) {
      ECBlockIdForTest idForTest = (ECBlockIdForTest)id;
      return Arrays.copyOfRange(data[idForTest.getId()], segmentIndex * CHUNK_SIZE, (segmentIndex + 1) * CHUNK_SIZE);
    }

    public void fillDataSegment(byte[] fillData, ECBlock block, int segmentIndex) {
      assert(fillData.length == CHUNK_SIZE);
      ECBlockIdForTest idForTest = (ECBlockIdForTest) block.getBlockId();
      for (int i = 0; i < fillData.length; i++) {
        data[idForTest.getId()][i + segmentIndex * CHUNK_SIZE] = fillData[i];
      }
    }

    public void eraseData(int erasedLocation) {
      data[erasedLocation] = new byte[BLOCK_SIZE];
    }
  }

}

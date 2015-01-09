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
package org.apache.hadoop.io.ec.codec;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.ec.*;
import org.apache.hadoop.io.ec.coder.AbstractErasureCoderCallback;
import org.apache.hadoop.io.ec.coder.ErasureCoderCallback;
import org.apache.hadoop.io.ec.coder.ErasureDecoder;
import org.apache.hadoop.io.ec.coder.ErasureEncoder;
import org.apache.hadoop.io.ec.grouper.BlockGrouper;
import org.apache.hadoop.io.ec.rawcoder.util.GaloisField;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class TestErasureCodecBase {
  private static final String TEST_DIR =
      new File(System.getProperty("test.build.data", "/tmp")).getAbsolutePath();
  public final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs").getAbsolutePath();

  private final Random RAND = new Random();
  private GaloisField GF = GaloisField.getInstance();
  private int symbolMax = 0;

  public static final String EC_CONF_PREFIX = "hadoop.io.ec.erasurecodec.codec.";
  public static final int BLOCK_SIZE = 1024 * 1024;
  public int CHUNK_SIZE;
  public int BLOCK_CHUNK_SIZE_MULIPLE;


  protected Configuration conf;
  protected String codecName = "codec_for_test";
  protected String schemaName = "schema_for_test";

  private BlockDataManager dataManager;
  private byte[][] message;

  protected abstract String getCodecClass();

  @Before
  public void init() throws IOException {
    int symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
    symbolMax = (int) Math.pow(2, symbolSize);

    conf = new Configuration();
    conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
    conf.set(EC_CONF_PREFIX + codecName, getCodecClass());
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

    List<ECBlockIdForTest> dataBlocks = prepareDataBlocks(numDataBlocks);
    List<ECBlockIdForTest> parityBlocks = prepareParityBlocks(numParityBlocks);
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

  private List<ECBlockIdForTest> prepareDataBlocks(int numDataBlocks) {
    List<ECBlockIdForTest> idLists = new ArrayList<ECBlockIdForTest>();
    for (int i = 0; i < numDataBlocks; i++) {
      byte[] byteArray = new byte[BLOCK_SIZE];
      for (int j = 0; j < BLOCK_SIZE; j++) {
        byteArray[j] = (byte) RAND.nextInt(symbolMax);
      }
      message[i] = byteArray;
      dataManager.fillData(i, Arrays.copyOf(byteArray, byteArray.length));
      idLists.add(new ECBlockIdForTest(i));
    }
    return idLists;
  }

  private List<ECBlockIdForTest> prepareParityBlocks(int numParityBlocks) {
    List<ECBlockIdForTest> ids = new ArrayList<ECBlockIdForTest>();
    for (int i = 0;i < numParityBlocks; i++) {
      ids.add(new ECBlockIdForTest(dataManager.parityBeginIndex() + i));
    }
    return ids;
  }

  private void verifyData(int erasedLocation, BlockGroup blockGroup) throws IOException {
    byte[] rightData = message[erasedLocation];

    SubBlockGroup subBlockGroup = blockGroup.getSubGroups().iterator().next();
    ECBlock recoveryBlock = subBlockGroup.getDataBlocks()[erasedLocation];
    byte[] actualData = dataManager.getData(recoveryBlock);

    assertTrue(Arrays.equals(rightData, actualData));
  }

  protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureEncoder encoder = codec.createEncoder();

    ErasureCoderCallback encodeCallback = new CallbackForTest();
    encoder.setCallback(encodeCallback);
    encoder.encode(blockGroup);
  }

  protected void decode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureDecoder decoder = codec.createDecoder();
    decoder.setCallback(new CallbackForTest());
    decoder.decode(blockGroup);
  }

  protected ECSchema loadSchema(String schemaName) throws Exception {
    SchemaLoader schemaLoader = new SchemaLoader();
    List<ECSchema> schemas = schemaLoader.loadSchema(conf);
    assertEquals(1, schemas.size());

    for (ECSchema schema : schemas) {
      if (schema.getSchemaName().equals(schemaName)) {
        return schema;
      }
    }
    return null;
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

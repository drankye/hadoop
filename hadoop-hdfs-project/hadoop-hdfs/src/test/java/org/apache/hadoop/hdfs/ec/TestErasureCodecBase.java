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
package org.apache.hadoop.hdfs.ec;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.*;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.AbstractErasureCoderCallback;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoderCallback;
import org.apache.hadoop.hdfs.ec.coder.ErasureDecoder;
import org.apache.hadoop.hdfs.ec.coder.ErasureEncoder;
import org.apache.hadoop.hdfs.ec.rawcoder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.grouper.BlockGrouper;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.DataNodeTestUtils;
import org.apache.hadoop.io.IOUtils;
import org.junit.Before;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class TestErasureCodecBase {
  private static final String TEST_DIR =
      new File(System.getProperty("test.build.data", "/tmp")).getAbsolutePath();
  private final static String DATA_FILE = new File(TEST_DIR, "data").getAbsolutePath();
  private final static String PARITY_FILE = new File(TEST_DIR, "parity").getAbsolutePath();
  public final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs").getAbsolutePath();

  private final Random RAND = new Random();
  private GaloisField GF = GaloisField.getInstance();
  private int symbolMax = 0;

  private FileSystem fileSys;
  private DataNode dataNode;

  public static final String EC_CONF_PREFIX = "hadoop.hdfs.ec.erasurecodec.codec.";
  public static final int BLOCK_SIZE = 512;
  public static final int CHUNK_SIZE = BLOCK_SIZE;

  protected Configuration conf;
  protected String codecName = "JavaRScodec";
  protected String schemaName = "JavaRS_10_4";

  private ByteBuffer[] message;

  protected abstract String getCodecClass();

  @Before
  public void init() throws IOException {
    int symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
    symbolMax = (int) Math.pow(2, symbolSize);

    int numDataNodes = 1;
    conf = new HdfsConfiguration();
    conf.setInt(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, BLOCK_SIZE);
    conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
    conf.set(EC_CONF_PREFIX + codecName, getCodecClass());
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDataNodes).build();
    cluster.waitActive();
    fileSys = cluster.getFileSystem();
    dataNode = cluster.getDataNodes().get(0);
  }

  protected void doTest() throws Exception {
    ECSchema schema = loadSchema(schemaName);

    /**
     * Both ECManager and ECWorker need to do below
     */
    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    BlockGrouper blockGrouper = codec.createBlockGrouper();
    int numDataBlocks = blockGrouper.getDataBlocks();
    int numParityBlocks = blockGrouper.getParityBlocks();

    /**
     * Below steps are to be done by NameNode/ECManager
     */
    List<ExtendedBlockId> dataBlocks = prepareDataBlocks(numDataBlocks);
    List<ExtendedBlockId> parityBlocks = prepareParityBlocks(numParityBlocks);
    BlockGroup blockGroup = blockGrouper.makeBlockGroup(dataBlocks, parityBlocks);

    /**
     * Below steps are to be done by DataNode/ECWorker
     */
    encode(schema, blockGroup);

    /**
     * Intently make some block 'missing'
     */
    int erasedLocation = RAND.nextInt(numDataBlocks);
    blockGroup.getSubGroups().get(0).getDataBlocks()[erasedLocation].setMissing(true);

    boolean canRecovery = blockGrouper.anyRecoverable(blockGroup);
    if (!canRecovery) {
      assertTrue(false);
    }
    BlockGroup groupUsedToRecovery = blockGrouper.makeRecoverableGroup(blockGroup);

    /**
     * Below steps are to be done by DataNode/ECWorker
     */
    decode(schema, groupUsedToRecovery);

    /**
     * Post check and see if it's right back
     */
    verifyData(erasedLocation, groupUsedToRecovery);
  }

  private List<ExtendedBlockId> prepareDataBlocks(int numDataBlocks) throws IOException {
    DFSTestUtil.createFile(fileSys, new Path(DATA_FILE), 0, (short) 1, 0);
    message = new ByteBuffer[numDataBlocks];
    for (int i = 0; i < numDataBlocks; i++) {
      byte[] byteArray = new byte[BLOCK_SIZE];
      for (int j = 0; j < BLOCK_SIZE; j++) {
        byteArray[j] = (byte) RAND.nextInt(symbolMax);
      }
      message[i] = ByteBuffer.wrap(byteArray);
      DFSTestUtil.appendFile(fileSys, new Path(DATA_FILE), byteArray);
    }
    List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(DATA_FILE));

    assertTrue(numDataBlocks == blocks.size());
    return getBlockIds(blocks);
  }

  private List<ExtendedBlockId> prepareParityBlocks(int numParityBlocks) throws IOException {
    DFSTestUtil.createFile(fileSys, new Path(PARITY_FILE), numParityBlocks * BLOCK_SIZE, (short) 1, 0);
    List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(PARITY_FILE));
    assertTrue(numParityBlocks == blocks.size());
    return getBlockIds(blocks);
  }

  private List<ExtendedBlockId> getBlockIds(List<LocatedBlock> dataBlocks) {
    List<ExtendedBlockId> ids = new ArrayList<ExtendedBlockId>();
    for (LocatedBlock block : dataBlocks) {
      ids.add(ExtendedBlockId.fromExtendedBlock(block.getBlock()));
    }
    return ids;
  }

  private void verifyData(int erasedLocation, BlockGroup blockGroup) throws IOException {
    ByteBuffer copyBuffer = message[erasedLocation];
    byte[] copy = new byte[BLOCK_SIZE];
    copyBuffer.get(copy);

    SubBlockGroup subBlockGroup = blockGroup.getSubGroups().iterator().next();
    ECBlock recoveryBlock = subBlockGroup.getDataBlocks()[erasedLocation];
    File file = getBlockFile(recoveryBlock);
    byte[] buffer = new byte[BLOCK_SIZE];
    IOUtils.readFully(new FileInputStream(file), buffer, 0, BLOCK_SIZE);

    assertTrue(Arrays.equals(copy, buffer));
  }

  /**
   * I'm ECWorker, now do encoding
   * ECWorker can get schema from namenode by schema name
   *
   * @param schema
   */
  protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureEncoder encoder = codec.createEncoder();

    ErasureCoderCallback encodeCallback = new CallbackForTest();
    encoder.setCallback(encodeCallback);
    encoder.encode(blockGroup);
  }

  /**
   * I'm ECWorker, now do decoding
   *
   * @param schema
   */
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

    return schemas.get(0);
  }

  private class CallbackForTest extends AbstractErasureCoderCallback {
    private ECChunk[][] inputChunks;
    private ECChunk[][] outputChunks;
    private int readInputIndex;
    private int readOutputIndex;

    @Override
    public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
      try {
        /**
         * For simple, we prepare for and load all the chunks at this phase.
         * Actually chunks should be read one by one only when needed while encoding/decoding.
         */
        inputChunks = new ECChunk[1][];
        inputChunks[0] = getChunks(inputBlocks);
        outputChunks = new ECChunk[1][];
        outputChunks[0] = getChunks(outputBlocks);
      } catch (IOException e) {
        e.printStackTrace();
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
      /**
       * For simple, a block has only one chunk. Actually a block can contain
       * multiple chunks.
       */
      try {
        for (int i = 0; i < outputChunks[0].length; i++) {
          ECBlock ecBlock = outputBlocks[i];
          File blockFile = getBlockFile(ecBlock);
          ByteBuffer byteBuffer = outputChunks[0][i].getChunkBuffer();

          RandomAccessFile raf = new RandomAccessFile(blockFile, "rw");
          FileChannel fc = raf.getChannel();
          IOUtils.writeFully(fc, byteBuffer);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private ECChunk[] getChunks(ECBlock[] dataEcBlocks) throws IOException {
    ECChunk[] chunks = new ECChunk[dataEcBlocks.length];
    for (int i = 0; i < dataEcBlocks.length; i++) {
      ECBlock ecBlock = dataEcBlocks[i];
      if (ecBlock.isMissing()) {
        chunks[i] = new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]));
      } else {
        File blockFile = getBlockFile(ecBlock);
        byte[] buffer = new byte[BLOCK_SIZE];
        IOUtils.readFully(new FileInputStream(blockFile), buffer, 0, CHUNK_SIZE);
        chunks[i] = new ECChunk(ByteBuffer.wrap(buffer));
      }
    }
    return chunks;
  }

  private File getBlockFile(ECBlock ecBlock) throws IOException {
    Block block = DataNodeTestUtils.getFSDataset(dataNode).getStoredBlock(ecBlock.getBlockPoolId(), ecBlock.getBlockId());
    File blockFile = DataNodeTestUtils.getBlockFile(dataNode, ecBlock.getBlockPoolId(), block);
    return blockFile;
  }
}

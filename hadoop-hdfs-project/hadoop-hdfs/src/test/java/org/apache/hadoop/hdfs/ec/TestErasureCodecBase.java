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
import org.apache.hadoop.hdfs.ec.coder.ErasureDecoder;
import org.apache.hadoop.hdfs.ec.coder.ErasureEncoder;
import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;
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
    ECChunk outputChunk = decode(schema, groupUsedToRecovery);

    /**
     * Post check and see if it's right back
     */
    verifyData(erasedLocation, outputChunk);
  }

  private List<ExtendedBlockId> prepareDataBlocks(int numDataBlocks) throws IOException {
    DFSTestUtil.createFile(fileSys, new Path(DATA_FILE), 0, (short)1, 0);
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
    DFSTestUtil.createFile(fileSys, new Path(PARITY_FILE), numParityBlocks * BLOCK_SIZE, (short)1, 0);
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

  private void verifyData(int erasedLocation, ECChunk outputChunk) {
    ByteBuffer copy = message[erasedLocation];
    assertTrue(copy.equals(outputChunk.getChunkBuffer()));
  }

  /**
   * I'm ECWorker, now do encoding
   * ECWorker can get schema from namenode by schema name
   * @param schema
   */
  protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureEncoder encoder = codec.createEncoder();
    BlockGrouper grouper = codec.createBlockGrouper();

    ECBlock[] dataEcBlocks = blockGroup.getSubGroups().get(0).getDataBlocks();
    ECChunk[] dataChunks = getChunks(dataEcBlocks);
    ECChunk[] parityChunks = new ECChunk[grouper.getParityBlocks()];
    for (int i = 0; i < parityChunks.length; i++) {
      parityChunks[i] = new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]));
    }

    encoder.encode(dataChunks, parityChunks);

    //write
    ECBlock[] parityBlocks = blockGroup.getSubGroups().get(0).getParityBlocks();
    for (int i = 0; i < parityChunks.length; i++) {
      ECBlock ecBlock = parityBlocks[i];
      File blockFile = getBlockFile(ecBlock);
      ByteBuffer byteBuffer = parityChunks[i].getChunkBuffer();

      RandomAccessFile raf = new RandomAccessFile(blockFile, "rw");
      FileChannel fc = raf.getChannel();
      IOUtils.writeFully(fc, byteBuffer);
    }
  }

  /**
   * I'm ECWorker, now do decoding
   * @param schema
   */
  protected ECChunk decode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    ECBlock[] dataEcBlocks = blockGroup.getSubGroups().get(0).getDataBlocks();
    ECBlock[] parityEcBlocks = blockGroup.getSubGroups().get(0).getParityBlocks();
    ECChunk[] dataChunks = getChunks(dataEcBlocks);
    ECChunk[] parityChunks = getChunks(parityEcBlocks);
    ECChunk outputChunk = new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]));

    ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
    ErasureDecoder decoder = codec.createDecoder();
    decoder.decode(dataChunks, parityChunks, outputChunk);
    return outputChunk;
  }

  private ECChunk[] getChunks(ECBlock[] dataEcBlocks) throws IOException {
    ECChunk[] chunks = new ECChunk[dataEcBlocks.length];
    for (int i = 0; i < dataEcBlocks.length; i++) {
      ECBlock ecBlock = dataEcBlocks[i];
      File blockFile = getBlockFile(ecBlock);
      byte[] buffer = new byte[BLOCK_SIZE];
      IOUtils.readFully(new FileInputStream(blockFile), buffer, 0, CHUNK_SIZE);
      chunks[i] = new ECChunk(ByteBuffer.wrap(buffer), ecBlock.isMissing());
    }
    return chunks;
  }

  private File getBlockFile(ECBlock ecBlock) throws IOException {
    Block block = DataNodeTestUtils.getFSDataset(dataNode).getStoredBlock(ecBlock.getBlockPoolId(), ecBlock.getBlockId());
    File blockFile = DataNodeTestUtils.getBlockFile(dataNode, ecBlock.getBlockPoolId(), block);
    return blockFile;
  }

  protected ECSchema loadSchema(String schemaName) throws Exception{
    SchemaLoader schemaLoader = new SchemaLoader();
    List<ECSchema> schemas = schemaLoader.loadSchema(conf);
    assertEquals(1, schemas.size());

    return schemas.get(0);
  }
}

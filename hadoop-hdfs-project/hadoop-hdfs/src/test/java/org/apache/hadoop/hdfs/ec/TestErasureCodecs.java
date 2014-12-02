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
import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.grouper.BlockGrouper;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public abstract class TestErasureCodecs {
  private static final String TEST_DIR = new File(System.getProperty(
      "test.build.data", "/tmp")).getAbsolutePath();
  private final static String DATA_FILE = new File(TEST_DIR, "data").getAbsolutePath();
  private final static String PARITY_FILE = new File(TEST_DIR, "parity").getAbsolutePath();
  public final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs").getAbsolutePath();

  private final Random RAND = new Random();
  private GaloisField GF = GaloisField.getInstance();
  private int symbolMax = 0;
  private Configuration conf;

  private FileSystem fileSys;
  private DataNode dataNode;

  public static final String EC_CONF_PREFIX = "hadoop.hdfs.ec.erasurecodec.codec.";
  public static final int BLOCK_SIZE = 512;
  public static final int CHUNK_SIZE = BLOCK_SIZE;

  private ByteBuffer[] message;

  @Before
  public void init() throws IOException {
    int symbolSize = (int) Math.round(Math.log(GF.getFieldSize()) / Math.log(2));
    symbolMax = (int) Math.pow(2, symbolSize);

    int numDataNodes = 1;
    conf = new HdfsConfiguration();
    conf.setInt(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, BLOCK_SIZE);
    conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDataNodes).build();
    cluster.waitActive();
    fileSys = cluster.getFileSystem();
    dataNode = cluster.getDataNodes().get(0);
  }

  protected Configuration getConf() {
    return conf;
  }

  protected DataNode getDataNode() {
    return dataNode;
  }

  public void testCodec(ECSchema schema) throws Exception {
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
     * Intended make some block missing
     */
    int erasedLocation = RAND.nextInt(numDataBlocks);
    blockGroup.getSubGroups().get(0).getDataBlocks()[erasedLocation].setMissing(true);

    boolean canRecovery = blockGrouper.anyRecoverable(blockGroup);
    if (!canRecovery) {
      assertTrue(false);
    }
    BlockGroup groupUseToRecovery = blockGrouper.makeRecoverableGroup(blockGroup);

    /**
     * Below steps are to be done by DataNode/ECWorker
     */
    ECChunk outputChunk = decode(schema, groupUseToRecovery);

    /**
     * Post check and see if it's right back
     */
    verifyData(erasedLocation, outputChunk);
  }

  private List<ExtendedBlockId> prepareDataBlocks(int numDataBlocks) throws IOException {
    //create
    DFSTestUtil.createFile(fileSys, new Path(DATA_FILE), 0, (short)1, 0);
    //write
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
    //create
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
  protected abstract void encode(ECSchema schema, BlockGroup blockGroup) throws Exception;

  /**
   * I'm ECWorker, now do decoding
   * @param schema
   */
  protected abstract ECChunk decode(ECSchema schema, BlockGroup blockGroup) throws Exception;


}

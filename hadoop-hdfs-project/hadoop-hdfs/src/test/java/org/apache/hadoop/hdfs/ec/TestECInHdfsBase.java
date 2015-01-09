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
import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.DataNodeTestUtils;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.ec.codec.ErasureCodec;
import org.apache.hadoop.io.ec.codec.TestErasureCodecBase;
import org.apache.hadoop.io.ec.coder.AbstractErasureCoderCallback;
import org.apache.hadoop.io.ec.coder.ErasureCoderCallback;
import org.apache.hadoop.io.ec.coder.ErasureDecoder;
import org.apache.hadoop.io.ec.coder.ErasureEncoder;
import org.apache.hadoop.io.ec.grouper.BlockGrouper;
import org.apache.hadoop.io.ec.BlockGroup;
import org.apache.hadoop.io.ec.SubBlockGroup;
import org.apache.hadoop.io.ec.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;
import org.apache.hadoop.io.ec.ECSchema;
import org.apache.hadoop.io.ec.SchemaLoader;
import org.apache.hadoop.io.ec.ECConfiguration;
import org.apache.hadoop.io.ec.rawcoder.util.GaloisField;
import org.bouncycastle.jce.provider.symmetric.AES;
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

public abstract class TestECInHdfsBase extends TestErasureCodecBase{
  private final static String DATA_FILE = new File(TEST_DIR, "data").getAbsolutePath();
  private final static String PARITY_FILE = new File(TEST_DIR, "parity").getAbsolutePath();

  private FileSystem fileSys;
  private DataNode dataNode;

  protected abstract String getCodecClass();

  @Override
  public void init() throws IOException {
    super.init();

    int numDataNodes = 1;
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDataNodes).build();
    cluster.waitActive();
    fileSys = cluster.getFileSystem();
    dataNode = cluster.getDataNodes().get(0);
  }

  @Override
  protected void initConf() {
    conf = new HdfsConfiguration();
    conf.setInt(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, BLOCK_SIZE);
  }

  @Override
  protected List<ExtendedBlockId> prepareDataBlocks(int numDataBlocks) throws IOException {
    DFSTestUtil.createFile(fileSys, new Path(DATA_FILE), 0, (short) 1, 0);
    for (int i = 0; i < numDataBlocks; i++) {
      byte[] byteArray = generateData(BLOCK_SIZE);
      message[i] = byteArray;
      DFSTestUtil.appendFile(fileSys, new Path(DATA_FILE), byteArray);
    }
    List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(DATA_FILE));

    assertTrue(numDataBlocks == blocks.size());
    return getBlockIds(blocks);
  }

  @Override
  protected List<ExtendedBlockId> prepareParityBlocks(int numParityBlocks) throws IOException {
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

  @Override
  protected byte[] getBlockData(ECBlock ecBlock) throws IOException {
    File file = getBlockFile(ecBlock);
    byte[] buffer = new byte[BLOCK_SIZE];
    IOUtils.readFully(new FileInputStream(file), buffer, 0, BLOCK_SIZE);
    return buffer;
  }

  private File getBlockFile(ECBlock ecBlock) throws IOException {
    ExtendedBlockId extendedBlockId = (ExtendedBlockId) ecBlock.getBlockId();
    Block block = DataNodeTestUtils.getFSDataset(dataNode).getStoredBlock(extendedBlockId.getBlockPoolId(), extendedBlockId.getBlockId());
    File blockFile = DataNodeTestUtils.getBlockFile(dataNode, extendedBlockId.getBlockPoolId(), block);
    return blockFile;
  }

  /**
   * I'm ECWorker, now do encoding
   * ECWorker can get schema from namenode by schema name
   *
   * @param schema
   */
  @Override
  protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    super.encode(schema, blockGroup, new CallbackForHdfs());
  }

  /**
   * I'm ECWorker, now do decoding
   *
   * @param schema
   */
  @Override
  protected void decode(ECSchema schema, BlockGroup blockGroup) throws Exception {
    super.decode(schema, blockGroup, new CallbackForHdfs());
  }

  private class CallbackForHdfs extends AbstractErasureCoderCallback {
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
  }


}

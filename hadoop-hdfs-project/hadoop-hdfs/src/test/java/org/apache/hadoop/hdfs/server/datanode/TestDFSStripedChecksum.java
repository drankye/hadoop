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
package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileChecksum;
import org.apache.hadoop.fs.HdfsBlockLocation;
import org.apache.hadoop.fs.MD5MD5CRC32CastagnoliFileChecksum;
import org.apache.hadoop.fs.MD5MD5CRC32GzipFileChecksum;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.StripedFileTestUtil;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedStripedBlock;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.LengthInputStream;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.MD5Hash;
import org.apache.hadoop.util.DataChecksum;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * This test serves a prototype to demo the idea proposed so far. It creates two
 * files using the same data, one is in replica mode, the other is in stripped
 * layout. For simple, it assumes 6 data blocks in both files and the block size
 * are the same.
 */
public class TestDFSStripedChecksum {
  public static final Log LOG = LogFactory.getLog(
      TestDFSStripedChecksum.class);

  private int dataBlocks = StripedFileTestUtil.NUM_DATA_BLOCKS;
  private int parityBlocks = StripedFileTestUtil.NUM_PARITY_BLOCKS;

  private MiniDFSCluster cluster;
  private DistributedFileSystem fs;
  private Configuration conf;
  private final int cellSize = StripedFileTestUtil.BLOCK_STRIPED_CELL_SIZE;
  private final int stripesPerBlock = 6;
  private final int blockSize = cellSize * stripesPerBlock;
  private final String ecDir = "/striped";

  @Before
  public void setup() throws IOException {
    int numDNs = dataBlocks + parityBlocks + 2;
    conf = new Configuration();
    conf.setLong(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, blockSize);
    conf.setBoolean(DFSConfigKeys.DFS_NAMENODE_REPLICATION_CONSIDERLOAD_KEY,
        false);
    conf.setInt(DFSConfigKeys.DFS_NAMENODE_REPLICATION_MAX_STREAMS_KEY, 0);
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDNs).build();
    Path ecPath = new Path(ecDir);
    cluster.getFileSystem().mkdir(ecPath, FsPermission.getDirDefault());
    cluster.getFileSystem().getClient().setErasureCodingPolicy(ecDir, null);
    fs = cluster.getFileSystem();
  }

  @After
  public void tearDown() {
    if (cluster != null) {
      cluster.shutdown();
      cluster = null;
    }
  }

  @Test
  public void testFileChecksum() throws Exception {
    // One block group
    int fileSize = stripesPerBlock * cellSize * dataBlocks;
    byte[] fileData = StripedFileTestUtil.generateBytes(fileSize);
    FileChecksum stripedFileChecksum = calcStripedChecksum(fileData);

    FileChecksum replicaFileChecksum = getReplicaFileChecksum(fileData);
    Assert.assertTrue(replicaFileChecksum.equals(stripedFileChecksum));
  }

  private FileChecksum getReplicaFileChecksum(byte[] fileData) throws Exception {
    String file = "/replicatedFileChecksum";
    Path testPath = new Path(file);

    DFSTestUtil.writeFile(fs, testPath, new String(fileData));

    FileChecksum fc = fs.getFileChecksum(testPath);
    return fc;
  }

  private FileChecksum calcStripedChecksum(byte[] fileData) throws Exception {
    String file = ecDir + "/stripedFileChecksum";
    Path testPath = new Path(file);
    int fileSize = fileData.length;

    DFSTestUtil.writeFile(fs, testPath, new String(fileData));
    StripedFileTestUtil.waitBlockGroupsReported(fs, file);

    BlockLocation[] locs = fs.getFileBlockLocations(testPath, 0, fileSize);
    LocatedStripedBlock blockGroup =
        (LocatedStripedBlock) ((HdfsBlockLocation) locs[0]).getLocatedBlock();
    LocatedBlock[] stripBlocks = StripedBlockUtil.parseStripedBlockGroup(
        blockGroup, cellSize, dataBlocks, parityBlocks);

    BlockCrcInfo[] blockCrcs = new BlockCrcInfo[stripBlocks.length];
    for (int i = 0; i < stripBlocks.length; i++) {
      LocatedBlock block = stripBlocks[i];
      DataNode dataNode = getDataNode(block);
      blockCrcs[i] = getBlockCrcChecksum(block, dataNode);
    }

    int numBlocksInReplicationForm = dataBlocks;
    int numCellsInOneBlock = dataBlocks;
    byte[][] cellCrcsInOneBlock = new byte[numCellsInOneBlock][];
    for (int j = 0; j < numCellsInOneBlock; j++) {
      cellCrcsInOneBlock[j] = blockCrcs[j].checksumData;
    }
    int lenOfCrcForOneCell = (cellSize/512) * 4;
    DataOutputBuffer md5out = new DataOutputBuffer();
    MD5Hash md5;
    for (int i = 0; i < numBlocksInReplicationForm; i++) {
      md5 = MD5Hash.digest(cellCrcsInOneBlock, i * lenOfCrcForOneCell, lenOfCrcForOneCell);
      md5out.write(md5.getDigest());
    }

    //compute file MD5
    DataChecksum.Type crcType = blockCrcs[0].checksum.getChecksumType();
    int bytesPerCRC = blockCrcs[0].checksum.getBytesPerChecksum();
    int crcPerBlock = blockCrcs[0].crcPerBlock;
    MD5Hash fileMD5 = MD5Hash.digest(md5out.getData());
    FileChecksum fileChecksum = null;
    switch (crcType) {
      case CRC32:
        fileChecksum = new MD5MD5CRC32GzipFileChecksum(bytesPerCRC,
            crcPerBlock, fileMD5);
        break;
      case CRC32C:
        fileChecksum = new MD5MD5CRC32CastagnoliFileChecksum(bytesPerCRC,
            crcPerBlock, fileMD5);
    }

    return fileChecksum;
  }

  static class BlockCrcInfo {
    DataChecksum checksum;
    byte[] checksumData;
    int crcPerBlock;
  }

  private BlockCrcInfo getBlockCrcChecksum(LocatedBlock block,
                                          DataNode dataNode) throws IOException {
    LengthInputStream metadataIn = dataNode.data
        .getMetaDataInputStream(block.getBlock());
    int ioFileBufferSize = DFSUtilClient.getIoFileBufferSize(conf);
    DataInputStream checksumIn = new DataInputStream(
        new BufferedInputStream(metadataIn, ioFileBufferSize));
    BlockMetadataHeader header = BlockMetadataHeader.readHeader(checksumIn);
    final DataChecksum checksum = header.getChecksum();

    BlockCrcInfo crcInfo = new BlockCrcInfo();
    crcInfo.checksum = checksum;
    int sumDataSize = checksum.getChecksumSize(blockSize);
    byte[] sumData = new byte[sumDataSize];
    checksumIn.readFully(sumData);
    crcInfo.checksumData = sumData;
    crcInfo.crcPerBlock = sumDataSize / checksum.getChecksumSize();
    return crcInfo;
  }

  private DataNode getDataNode(LocatedBlock block) throws IOException {
    DatanodeInfo dnInfo = block.getLocations()[0];
    for (DataNode dn : cluster.getDataNodes()) {
      if (dn.getInfoPort() == dnInfo.getInfoPort()) {
        return dn;
      }
    }
    return null;
  }
}

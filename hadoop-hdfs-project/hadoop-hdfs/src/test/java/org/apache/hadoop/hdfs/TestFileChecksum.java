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
package org.apache.hadoop.hdfs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileChecksum;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * This test serves a prototype to demo the idea proposed so far. It creates two
 * files using the same data, one is in replica mode, the other is in stripped
 * layout. For simple, it assumes 6 data blocks in both files and the block size
 * are the same.
 */
public class TestFileChecksum {
  public static final Log LOG = LogFactory.getLog(
      TestFileChecksum.class);

  private int dataBlocks = StripedFileTestUtil.NUM_DATA_BLOCKS;
  private int parityBlocks = StripedFileTestUtil.NUM_PARITY_BLOCKS;

  private MiniDFSCluster cluster;
  private DistributedFileSystem fs;
  private Configuration conf;
  private int cellSize = StripedFileTestUtil.BLOCK_STRIPED_CELL_SIZE;
  private int stripesPerBlock = 6;
  private int blockSize = cellSize * stripesPerBlock;
  private int numBlockGroups = 10;
  private int stripSize = cellSize * dataBlocks;
  private int blockGroupSize = stripesPerBlock * stripSize;
  private int fileSize = numBlockGroups * blockGroupSize;

  private String ecDir = "/striped";
  private String stripedFile1 = ecDir + "/stripedFileChecksum1";
  private String stripedFile2 = ecDir + "/stripedFileChecksum2";
  private String replicatedFile = "/replicatedFileChecksum";

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

    prepareTestFiles();
  }

  @After
  public void tearDown() {
    if (cluster != null) {
      cluster.shutdown();
      cluster = null;
    }
  }

  @Test
  public void testStripedFileChecksum1() throws Exception {
    int length = 0;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum2() throws Exception {
    int length = stripSize - 1;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum3() throws Exception {
    int length = stripSize;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum4() throws Exception {
    int length = stripSize + cellSize * 2;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum5() throws Exception {
    int length = blockGroupSize;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum6() throws Exception {
    int length = blockGroupSize + blockSize;
    testStripedFileChecksum(length);
  }

  @Test
  public void testStripedFileChecksum7() throws Exception {
    int length = -1; // whole file
    testStripedFileChecksum(length);
  }

  void testStripedFileChecksum(int range) throws Exception {
    FileChecksum stripedFileChecksum1 = getFileChecksum(stripedFile1, range);
    FileChecksum stripedFileChecksum2 = getFileChecksum(stripedFile2, range);

    Assert.assertTrue(stripedFileChecksum1.equals(stripedFileChecksum2));
  }

  @Test
  public void testStripedAdnReplicatedFileChecksum() throws Exception {
    FileChecksum stripedFileChecksum1 = getFileChecksum(stripedFile1, 10);
    FileChecksum replicatedFileChecksum = getFileChecksum(replicatedFile, 10);

    Assert.assertFalse(stripedFileChecksum1.equals(replicatedFileChecksum));
  }

  private FileChecksum getFileChecksum(String filePath,
                                       int range) throws Exception {
    Path testPath = new Path(filePath);
    FileChecksum fc = null;

    if (range >= 0) {
      fc = fs.getFileChecksum(testPath, range);
    } else {
      fc = fs.getFileChecksum(testPath);
    }
    
    return fc;
  }

  void prepareTestFiles() throws IOException {
    byte[] fileData = StripedFileTestUtil.generateBytes(fileSize);

    String[] filePaths = new String[] {
        stripedFile1, stripedFile2, replicatedFile
    };

    for (String filePath : filePaths) {
      Path testPath = new Path(filePath);
      DFSTestUtil.writeFile(fs, testPath, fileData);
    }
  }
}

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
  public void testStripedFileChecksum() throws Exception {
    // One block group
    int fileSize = 10 * stripesPerBlock * cellSize * dataBlocks;
    byte[] fileData = StripedFileTestUtil.generateBytes(fileSize);

    String file1 = ecDir + "/stripedFileChecksum1";
    FileChecksum stripedFileChecksum1 = getFileChecksum(file1, fileData);

    String file2 = ecDir + "/stripedFileChecksum2";
    FileChecksum stripedFileChecksum2 = getFileChecksum(file2, fileData);

    Assert.assertTrue(stripedFileChecksum1.equals(stripedFileChecksum2));
  }

  @Test
  public void testStripedAdnReplicatedFileChecksum() throws Exception {
    // One block group
    int fileSize = 10 * stripesPerBlock * cellSize * dataBlocks;
    byte[] fileData = StripedFileTestUtil.generateBytes(fileSize);

    String file1 = ecDir + "/stripedFileChecksum1";
    FileChecksum stripedFileChecksum1 = getFileChecksum(file1, fileData);

    String file2 = "/replicatedFileChecksum";
    FileChecksum stripedFileChecksum2 = getFileChecksum(file2, fileData);

    Assert.assertFalse(stripedFileChecksum1.equals(stripedFileChecksum2));
  }

  private FileChecksum getFileChecksum(String filePath,
                                       byte[] fileData) throws Exception {
    Path testPath = new Path(filePath);

    DFSTestUtil.writeFile(fs, testPath, new String(fileData));

    FileChecksum fc = fs.getFileChecksum(testPath);
    return fc;
  }
}

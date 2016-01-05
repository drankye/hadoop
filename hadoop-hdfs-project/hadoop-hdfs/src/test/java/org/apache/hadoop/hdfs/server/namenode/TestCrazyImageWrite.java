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

package org.apache.hadoop.hdfs.server.namenode;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.util.Time;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * A JUnit test for checking if restarting DFS preserves integrity.
 * Specifically with FSImage being written in parallel
 */
public class TestCrazyImageWrite {
  private static final int NUM_DATANODES = 4;
  private static final int NUM_FILES = 1 * 20;

  /** check if DFS remains in proper condition after a restart */
  @Test
  public void testLoadingImage() throws Exception {
    final Configuration conf = new HdfsConfiguration();
    MiniDFSCluster cluster = null;

    DFSTestUtil files = new DFSTestUtil.Builder().setName("TestCrazyImageWrite").
        setNumFiles(NUM_FILES).build();

    final String dir = "/crazyfolder";

    try {
      cluster = new MiniDFSCluster.Builder(conf).format(true)
          .numDataNodes(NUM_DATANODES).build();
      FileSystem fs = cluster.getFileSystem();
      files.createFiles(fs, dir);

    } finally {
      if (cluster != null) { cluster.shutdown(); }
    }

    try {
      // Force the NN to save its images on startup so long as
      // there are any uncheckpointed txns
      conf.setInt(DFSConfigKeys.DFS_NAMENODE_CHECKPOINT_TXNS_KEY, 1);

      // Here we restart the MiniDFScluster without formatting namenode
      cluster = new MiniDFSCluster.Builder(conf).format(false)
          .numDataNodes(NUM_DATANODES).build();

      FileSystem fs = cluster.getFileSystem();
      assertTrue("Filesystem corrupted after restart.",
                 files.checkFiles(fs, dir));
    } finally {
      if (cluster != null) { cluster.shutdown(); }
    }

    long start = 0, end = 0;
    try {

      start = Time.monotonicNow();
      // Here we restart the MiniDFScluster without formatting namenode
      cluster = new MiniDFSCluster.Builder(conf).format(false)
          .numDataNodes(NUM_DATANODES).build();
      FileSystem fs = cluster.getFileSystem();
      end = Time.monotonicNow();

      assertTrue("Filesystem corrupted after restart.",
          files.checkFiles(fs, dir));

      files.cleanup(fs, dir);
    } finally {
      if (cluster != null) { cluster.shutdown(); }
    }

    System.out.println("It uses " + (end - start));
  }
}


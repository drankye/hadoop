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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;

import java.io.IOException;
import java.util.Random;

public class TestWriteReadStrippedFileBase {
  protected static int dataBlocks = HdfsConstants.NUM_DATA_BLOCKS;
  protected static int parityBlocks = HdfsConstants.NUM_PARITY_BLOCKS;

  protected final static int cellSize = HdfsConstants.BLOCK_STRIPED_CELL_SIZE;
  protected final static int stripesPerBlock = 4;
  protected final static int blockSize = cellSize * stripesPerBlock;
  protected final static int numDNs = dataBlocks + parityBlocks + 2;

  protected static MiniDFSCluster cluster;
  protected static Configuration conf;
  protected static FileSystem fs;

  protected static Random r= new Random();

  protected static void performSetup() throws IOException {
    conf = new Configuration();
    conf.setLong(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, blockSize);
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDNs).build();
    cluster.getFileSystem().getClient().createErasureCodingZone("/",
        null, cellSize);
    fs = cluster.getFileSystem();
  }

  protected static void performTearDown() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  protected byte[] generateBytes(int cnt) {
    byte[] bytes = new byte[cnt];
    for (int i = 0; i < cnt; i++) {
      bytes[i] = getByte(i);
    }
    return bytes;
  }

  protected int readAll(FSDataInputStream in, byte[] buf) throws IOException {
    int readLen = 0;
    int ret;
    do {
      ret = in.read(buf, readLen, buf.length - readLen);
      if (ret > 0) {
        readLen += ret;
      }
    } while (ret >= 0 && readLen < buf.length);
    return readLen;
  }

  protected byte getByte(long pos) {
    final int mod = 29;
    return (byte) (pos % mod + 1);
  }
}

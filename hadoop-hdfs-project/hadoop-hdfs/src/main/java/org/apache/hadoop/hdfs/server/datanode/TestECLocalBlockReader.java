
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


import org.apache.hadoop.fs.ChecksumException;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.client.HdfsDataInputStream;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.server.datanode.CachingStrategy;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.ECLocalBlockReader;
import org.apache.hadoop.hdfs.shortcircuit.ShortCircuitCache;
import org.apache.hadoop.hdfs.shortcircuit.ShortCircuitReplica;
import org.apache.hadoop.hdfs.shortcircuit.ShortCircuitShm;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.unix.DomainSocket;
import org.apache.hadoop.net.unix.TemporarySocketDirectory;
import org.apache.hadoop.util.Time;
import org.junit.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.equalTo;

public class TestECLocalBlockReader {



  @Test
  public void runBlockReaderLocalTest() throws IOException {



    int fileLength = 12345;
    final long RANDOM_SEED = 4567L;
    ByteBuffer dfsRead = ByteBuffer.allocate(fileLength);
    ByteBuffer ecRead = ByteBuffer.allocateDirect(fileLength);

    Assume.assumeThat(DomainSocket.getLoadingFailureReason(), equalTo(null));
    MiniDFSCluster cluster = null;
    HdfsConfiguration conf = new HdfsConfiguration();

    FileInputStream dataIn = null, metaIn = null;
    final Path TEST_PATH = new Path("/ECReaderTest");
    FSDataInputStream fsIn = null;

    FileSystem fs = null;
    try {
      cluster = new MiniDFSCluster.Builder(conf).numDataNodes(1).build();
      cluster.waitActive();
      fs = cluster.getFileSystem();
      DFSTestUtil.createFile(fs, TEST_PATH, fileLength, (short) 1, RANDOM_SEED);
      try {
        DFSTestUtil.waitReplication(fs, TEST_PATH, (short) 1);
      } catch (InterruptedException e) {
        Assert.fail("unexpected InterruptedException during " +
            "waitReplication: " + e);
      } catch (TimeoutException e) {
        Assert.fail("unexpected TimeoutException during " +
            "waitReplication: " + e);
      }
      fsIn = fs.open(TEST_PATH);
      IOUtils.readFully(fsIn, dfsRead.array(), 0, fileLength);
      dfsRead.limit(fileLength);
      fsIn.close();

      ExtendedBlock block = DFSTestUtil.getFirstBlock(fs, TEST_PATH);
      DataNode dn = cluster.getDataNodes().get(0);
      ECLocalBlockReader ecReader = new ECLocalBlockReader(dn,block,
                                     0, fileLength, true);
      //connect first
      ecReader.connect();
      ecReader.readAll(ecRead);
      ecRead.position(0);


      Assert.assertEquals(true, compareBytes(ecRead, dfsRead));


      File dataFile = MiniDFSCluster.getBlockFile(0, block);
      File metaFile = MiniDFSCluster.getBlockMetadataFile(0, block);
      FileInputStream streams[] = {
          new FileInputStream(dataFile),
          new FileInputStream(metaFile)
      };


      // BlockReaderLocal should not alter the file position.
      Assert.assertEquals(0, streams[0].getChannel().position());
      Assert.assertEquals(0, streams[1].getChannel().position());
    } finally {
      if (fsIn != null) fsIn.close();
      if (fs != null) fs.close();
      if (cluster != null) cluster.shutdown();
      if (dataIn != null) dataIn.close();
      if (metaIn != null) metaIn.close();

    }
  }

  public static boolean compareBytes(ByteBuffer buf1, ByteBuffer buf2){
    int len = buf1.remaining();
    if( len != buf2.remaining())
      return false;

    for(int i = 0; i < len; i++){
      if(buf1.get() != buf2.get())
        return false;
    }
    return true;
  }


}
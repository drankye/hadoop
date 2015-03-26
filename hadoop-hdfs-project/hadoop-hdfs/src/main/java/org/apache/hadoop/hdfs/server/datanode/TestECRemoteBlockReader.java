package org.apache.hadoop.hdfs;

import io.netty.buffer.ByteBuf;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.ECLocalBlockReader;
import org.apache.hadoop.hdfs.server.datanode.ECRemoteBlockReader;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.unix.DomainSocket;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.equalTo;


public class TestECRemoteBlockReader {


  @Test
  public void runBlockReaderRemoteTest() throws IOException {


    int fileLength = 12345+1024*1024*89;
    final long RANDOM_SEED = 4567L;

    Assume.assumeThat(DomainSocket.getLoadingFailureReason(), equalTo(null));
    MiniDFSCluster cluster = null;
    HdfsConfiguration conf = new HdfsConfiguration();

    FileInputStream dataIn = null, metaIn = null;
    final Path TEST_PATH = new Path("/ECReaderTest");
    FSDataInputStream fsIn = null;

    FileSystem fs = null;
    try {
      cluster = new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
      cluster.waitActive();
      fs = cluster.getFileSystem();
      DFSTestUtil.createFile(fs, TEST_PATH, fileLength, (short) 3, RANDOM_SEED);
       try {
        DFSTestUtil.waitReplication(fs, TEST_PATH, (short) 3);
      } catch (InterruptedException e) {
        Assert.fail("unexpected InterruptedException during " +
            "waitReplication: " + e);
      } catch (TimeoutException e) {
        Assert.fail("unexpected TimeoutException during " +
            "waitReplication: " + e);
      }
      fsIn = fs.open(TEST_PATH);


      List<LocatedBlock> locatedBlocks = DFSTestUtil.getAllBlocks(fs, TEST_PATH);


      ByteBuffer dfsRead = null , ecRead =null;

      DataNode dn = cluster.getDataNodes().get(0); // this datanode is where we run ECRemoteReader
      int i=0;
      for(LocatedBlock block : locatedBlocks){
        ExtendedBlock exBlock= block.getBlock();
        long blockLen = exBlock.getNumBytes();

        if( i++ == 0) {
          dfsRead = ByteBuffer.allocate((int) blockLen);
          ecRead = ByteBuffer.allocateDirect((int) blockLen);
        }
        dfsRead.clear();


        //using FSDataInputStream to read the data
        fsIn.readFully(dfsRead.array(), 0, (int) blockLen);
        dfsRead.limit((int) blockLen);

        DatanodeInfo[] infos = block.getLocations();
        for(DatanodeInfo info : infos){
          String id = info.getDatanodeUuid();
          if(id.equals(dn.getDatanodeUuid()))
            continue;

          dfsRead.position(0);

          ECRemoteBlockReader ecReader = new ECRemoteBlockReader(dn,
              exBlock, info, 0, blockLen, false);
          ecReader.connect();
          ecRead.clear();
          ecReader.readAll(ecRead);
          ecRead.position(0);
//          ecRead.limit((int)blockLen);

          Assert.assertEquals(true, compareBytes(dfsRead, ecRead));
        }

      }


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

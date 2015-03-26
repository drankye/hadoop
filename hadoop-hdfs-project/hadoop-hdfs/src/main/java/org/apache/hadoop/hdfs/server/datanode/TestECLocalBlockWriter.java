package org.apache.hadoop.hdfs;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.*;
import org.apache.hadoop.util.DataChecksum;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class TestECLocalBlockWriter {

  @Test
  public void TestLocalBlockWriter() throws IOException {

    int mod = 47;

    //create miniCluster and a file
    int fileLength = 1;
    final long RANDOM_SEED = 4567L;
    final Path TEST_PATH = new Path("/ECOutputTest");
    MiniDFSCluster cluster = null;
    HdfsConfiguration conf = new HdfsConfiguration();
    FileSystem fs = null;
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(2).build();
    cluster.waitActive();
    fs = cluster.getFileSystem();
    DFSTestUtil.createFile(fs, TEST_PATH, fileLength, (short) 3, RANDOM_SEED);

    int bytesWritten = 1234+178*1024*1024;

    byte[] buf = new byte[bytesWritten];
    for(int i = 0; i < buf.length; i++){
      buf[i] = (byte)(i % mod);
    }
    long newBlockId = 0;
    String poolId;

    try {
      List<LocatedBlock> locatedBlocks = DFSTestUtil.getAllBlocks(fs, TEST_PATH);
      ExtendedBlock block0 = locatedBlocks.get(0).getBlock();
      DataNode dn = cluster.getDataNodes().get(0);


      newBlockId = block0.getBlockId() + 43532665L;
      poolId = block0.getBlockPoolId();
      ExtendedBlock block = new ExtendedBlock(poolId, newBlockId);

      //create ECRemoteBlockWriter
      ECLocalBlockWriter writer = ECLocalBlockWriter.newInstance(block, StorageType.DEFAULT, dn);
      writer.write(buf, 0, buf.length);

      writer.close();


      block = null;
      List<FinalizedReplica> replicas = dn.getFSDataset().getFinalizedBlocks(poolId);
      locatedBlocks = DFSTestUtil.getAllBlocks(fs, TEST_PATH);
      for(FinalizedReplica replica : replicas){

          if(replica.getBlockId() == newBlockId)
          {
              block = new ExtendedBlock(poolId,replica);
              break;
          }
      }

      assert block != null : "fail to find newly written block";

      int blockLen =(int)block.getNumBytes();
      assert blockLen == bytesWritten : "block length is not as expected";
      ECLocalBlockReader r = new ECLocalBlockReader(dn, block);
      r.connect();
      ByteBuffer rbuf = ByteBuffer.allocate(blockLen);
      rbuf.clear();
      r.readAll(rbuf);
      rbuf.position(0);
      rbuf.limit(blockLen);

      assert rbuf.remaining() == blockLen;
      boolean correctRead = true;
      for(int i = 0; i < blockLen; i++){
        if(rbuf.get() != i % mod){
          correctRead = false;
          break;
        }
      }

        byte[] arr=rbuf.array();
        for(int i=0;i<(arr.length>=1000?1000:arr.length);i++)
            System.out.print(arr[i]+" ");

      assert correctRead == true : "Fail to read block.";



    } catch(Exception e){
      e.printStackTrace();

    } finally {

      if (fs != null) fs.close();
      if (cluster != null) cluster.shutdown();


    }
  }

}

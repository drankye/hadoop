package org.apache.hadoop.hdfs;

import org.apache.hadoop.ec.datanode.ECChunk;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocol.datatransfer.PacketReceiver;
import org.apache.hadoop.hdfs.server.datanode.*;
import org.apache.hadoop.util.DataChecksum;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class TestECRemoteBlockWriter {






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

    byte[] buf = new byte[75*1024+123];
    for(int i = 0; i < buf.length; i++){
      buf[i] = (byte)(i % mod);
    }


    try {
      List<LocatedBlock> locatedBlocks = DFSTestUtil.getAllBlocks(fs, TEST_PATH);
      ExtendedBlock block0 = locatedBlocks.get(0).getBlock();
      DatanodeInfo[] dnInfos = locatedBlocks.get(0).getLocations();
      DatanodeInfo remoteDnInfo = null;
      DataNode dn0 = cluster.getDataNodes().get(0);
      for(DatanodeInfo info : dnInfos){
        if(!info.getDatanodeUuid().equals(dn0.getDatanodeUuid())){
          remoteDnInfo = info;
          break;
        }
      }
      assert remoteDnInfo != null : "Fail to find a remote DatanodeInfo." ;

      long newBlockId = block0.getBlockId() + 43532665L;
      ExtendedBlock block = new ExtendedBlock(block0.getBlockPoolId(), newBlockId);

      //create ECRemoteBlockWriter

      ECRemoteBlockWriter writer = ECRemoteBlockWriter.newInstance(block, dn0, remoteDnInfo,
          StorageType.DEFAULT, DataChecksum.newDataChecksum(DataChecksum.Type.CRC32,512));
      writer.write(buf, 0, buf.length);

      writer.close();


      int blockLen = buf.length;
      ECRemoteBlockReader ecReader = new ECRemoteBlockReader(dn0, block,remoteDnInfo);
      ecReader.connect();
      ByteBuffer ecReadBuf = ByteBuffer.allocate(blockLen);
      ecReadBuf.clear();
      ecReader.readAll(ecReadBuf);
      ecReadBuf.position(0);
 //     ecReadBuf.limit(blockLen);
  

      assert ecReadBuf.remaining() == blockLen;
      boolean correctRead = true;
      for(int i = 0; i < blockLen; i++){

        if(ecReadBuf.get() != i % mod){
          correctRead = false;
          break;
        }
      }

      assert correctRead == true : "Fail to read block.";



    } catch(Exception e){
      e.printStackTrace();

    } finally {

      if (fs != null) fs.close();
      if (cluster != null) cluster.shutdown();


    }
  }

}

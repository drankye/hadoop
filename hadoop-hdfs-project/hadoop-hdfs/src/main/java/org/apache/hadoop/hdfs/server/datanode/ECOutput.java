package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.ec.BlockWriter;
import org.apache.hadoop.ec.datanode.ECChunk;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.util.DataChecksum;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;


public class ECOutput {

  public static final Log LOG = LogFactory.getLog(ECCoder.class);

  private Map<ECBlock,BlockWriter> blockWriterMap;

  private final int count;
  private final DataNode dn;
  private final DataChecksum checksum;

  public ECOutput(DataNode dataNode, ECBlock[] blocks) throws IOException{

    this.dn = dataNode;
    this.count = (blocks == null ? 0 : blocks.length);

    String checksumType = dn.getDnConf().getConf()
        .get(DFSConfigKeys.DFS_CHECKSUM_TYPE_KEY, DFSConfigKeys.DFS_CHECKSUM_TYPE_DEFAULT);
    int bytesPerChecksum = dn.getDnConf().getConf()
        .getInt(DFSConfigKeys.DFS_BYTES_PER_CHECKSUM_KEY, DFSConfigKeys.DFS_BYTES_PER_CHECKSUM_DEFAULT);
    this.checksum = DataChecksum.newDataChecksum(DataChecksum.Type.valueOf(checksumType), bytesPerChecksum);

    blockWriterMap = new LinkedHashMap<ECBlock, BlockWriter>();
     for (int i = 0; i < count; i++) {
      BlockWriter w = null;
      if(dn.getDatanodeId().equals(dataNode.getDatanodeId()))
        w = LocalDatanodeBlockWriter.newInstance(blocks[i].getExtendedBlock(),
            blocks[i].getStorageType(), dn);
      else
        w = RemoteDatanodeBlockWriter.newInstance(blocks[i].getExtendedBlock(), dn,
            blocks[i].getDatanodeInfo(), blocks[i].getStorageType(), checksum);

      blockWriterMap.put(blocks[i], w);
    }
  }

  public boolean put(ECChunk chunk, ECBlock ecBlock) throws IOException{
    BlockWriter w = blockWriterMap.get(ecBlock);
    if(null == w)
      return false;
    w.write(chunk.getChunkBuffer());
    return true;
  }

  public void close(){
    for(BlockWriter w : blockWriterMap.values()) {
      try {
        w.close();
        w = null;
      }catch (IOException e){
        LOG.info("closing block writer "+ w, e);
      }finally {
        if(w != null){
          try{
            w.close();
          }catch (IOException ee) {}
        }
      }
    }

  }



}

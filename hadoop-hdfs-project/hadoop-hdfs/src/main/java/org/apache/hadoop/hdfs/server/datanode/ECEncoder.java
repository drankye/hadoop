package org.apache.hadoop.hdfs.server.datanode;

import org.apache.hadoop.ec.ECBlockReader;
import org.apache.hadoop.ec.datanode.ECChunk;
import org.apache.hadoop.ec.datanode.ECChunkException;
import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.SubBlockGroup;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureEncoder;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ECEncoder extends ECCoder {
  public ECEncoder(BlockGroup blockGroup, DataNode dataNode,
                   ECSchema schema) throws IOException {
    super(blockGroup, dataNode, schema);
  }

  protected Map<ECBlock, ECInput> createInputs() throws IOException{
    Map<ECBlock, ECInput> map = new LinkedHashMap<ECBlock, ECInput>();
    for(SubBlockGroup sbg:blockGroup.getSubGroups()){
      for(ECBlock block: sbg.getDataBlocks()) {

        DatanodeInfo info = block.getDatanodeInfo();
        boolean localBlock = info.equals(dn.getDatanodeId());

        ECBlockReader blockReader = localBlock ? new LocalDatanodeBlockReader(dn, block.getExtendedBlock()) :
            new RemoteDatanodeBlockReader(dn, block.getExtendedBlock(), info);
        ECInput ecInput = new ECInput(blockReader);
        map.put(block, ecInput);
      }
    }
    return map;

  }

  protected ECOutput createOutput() throws IOException{
    List<ECBlock> list = new ArrayList<ECBlock>();
    for(SubBlockGroup sbg:blockGroup.getSubGroups())
      for(ECBlock b : sbg.getDataBlocks())
        list.add(b);

    ECOutput output = new ECOutput(dn, list.toArray(new ECBlock[list.size()]));

    return output;
  }

  //encode decode is not the same
  public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) throws ECChunkException {

    int cnt = inputBlocks.length;
    ECChunk[] chunks = new ECChunk[cnt];
    for (int i = 0; i < cnt; i++) {
      ECInput ei = inputMap.get(inputBlocks[i]);
      if(ei == null)
        throw new ECChunkException("Fail to find ECInput.");
      try {
        chunks[i] = ei.get();
      } catch(InterruptedException e){
        throw new ECChunkException("Fail to get an ECChunk from ECInput.");
      }
    }

    check(chunks);

    return chunks;
  }


  public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks){
    ECChunk[] parityChunks = ECChunk.getChunks(useDirect, ecChunkSize,
        outputBlocks.length);//handle if allocation fails
    return parityChunks;

  }

  @Override
  public void run() {
    runThreads();
    ErasureCodec codec = null;
    try{
      codec = ErasureCodec.createErasureCodec(schema);
      if(codec == null)
        throw new IOException("Fail to get Codec.");
      ErasureEncoder encoder = codec.createEncoder();
      encoder.setCallback(this);
      encoder.encode(blockGroup);
    }catch(Exception e){
      exception = e;
      LOG.info(e);
      return;
    } finally{
      output.close();
    }

    if(exception == null){
      LOG.info("Successfully encode BlockGroup " + blockGroup);
      //todo: acknowlege namenode that this task has been finished
    } else{
      LOG.info("Failed to encode BlockGroup " + blockGroup, exception);
      //todo: acknowlege namenode that this task has failed
    }
  }


}

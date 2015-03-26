package org.apache.hadoop.hdfs.server.datanode;

import org.apache.hadoop.ec.datanode.ECChunk;
import org.apache.hadoop.ec.datanode.ECChunkException;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.SubBlockGroup;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureDecoder;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ECDecoder extends ECCoder {

  //record ECChunk for missing blocks.
  private Map<ECBlock, ECChunk> missingMap = new LinkedHashMap<ECBlock, ECChunk>();

  public ECDecoder(BlockGroup blockGroup, DataNode dataNode,
                   ECSchema schema) throws IOException {
    super(blockGroup, dataNode, schema);
  }

  //create necessary ECInput for available
  protected Map<ECBlock, ECInput> createInputs() throws IOException{
    Map<ECBlock, ECInput> map = new LinkedHashMap<ECBlock, ECInput>();
    for(SubBlockGroup sbg:blockGroup.getSubGroups()){
      for(ECBlock block: sbg.getDataBlocks()) {
        if(block.isMissing()) //if missing
          continue;
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
    List<ECBlock> missingBlocks = new ArrayList<ECBlock>();
    for(SubBlockGroup sbg:blockGroup.getSubGroups()) {
      for (ECBlock block : sbg.getDataBlocks()) {
        if (block.isMissing()) //if missing, put it to ECOutput
          missingBlocks.add(block);
      }
    }

    ECBlock[] blocks = missingBlocks.toArray(new ECBlock[missingBlocks.size()]);

    ECOutput output = new ECOutput(dn, blocks);

    return output;
  }

  //encode is not the same with decode when retrieving next input chunks
  public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) throws ECChunkException {
    List<ECChunk> existChunks = new ArrayList<ECChunk>();
    int cnt = inputBlocks.length;
    ECChunk[] chunks = new ECChunk[cnt];
    for (int i = 0; i < cnt; i++) {
      ECInput ei = inputMap.get(inputBlocks[i]);
      if(ei == null){
        chunks[i] = ECChunk.getChunk(ECInput.useDirect, ECInput.ecChunkSize);
        missingMap.put(inputBlocks[i],chunks[i]);
      }else
      try {
        chunks[i] = ei.get();
      } catch(InterruptedException e){
        throw new ECChunkException("Fail to get an ECChunk from ECInput.");
      }
      existChunks.add(chunks[i]);
    }

    check(existChunks.toArray(new ECChunk[existChunks.size()]));

    return chunks;
  }

  public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks){
    ECChunk[] missing = new ECChunk[outputBlocks.length];
    for(int i = 0; i < outputBlocks.length; i++){
      ECChunk c = missingMap.get(outputBlocks[i]);
      missing[i] = c;
    }
    return missing;
  }

  @Override
  public void run() {
    runThreads();
    ErasureCodec codec = null;
    try{
      codec = ErasureCodec.createErasureCodec(schema);
      if(codec == null)
        throw new IOException("Fail to get Codec.");
      ErasureDecoder decoder = codec.createDecoder();
      decoder.setCallback(this);
      decoder.decode(blockGroup);
    }catch(Exception e){
      exception = e;
      LOG.info(e);
      return;
    } finally{
      output.close();
    }

    if(exception == null){
      LOG.info("Successfully decode BlockGroup " + blockGroup);
      //todo: acknowlege namenode that this task has been finished
    } else{
      LOG.info("Failed to decode BlockGroup " + blockGroup, exception);
      //todo: acknowlege namenode that this task has failed
    }
  }



}

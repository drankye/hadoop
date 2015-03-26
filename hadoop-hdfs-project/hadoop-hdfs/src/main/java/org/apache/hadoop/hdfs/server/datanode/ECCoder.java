package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.ec.datanode.ECChunk;
import org.apache.hadoop.ec.datanode.ECChunkException;
import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoderCallback;
import org.apache.hadoop.hdfs.ec.coder.ErasureEncoder;

import java.io.*;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;


public abstract class ECCoder implements Runnable, ErasureCoderCallback {

  public static final Log LOG = LogFactory.getLog(ECCoder.class);

  protected boolean useDirect = true;
  protected int ecChunkSize = 64 * 1024; //will get from config

  protected Map<ECBlock, ECInput> inputMap;
  protected ECOutput output;

  protected BlockGroup blockGroup;
  protected DataNode dn;
  protected ECSchema schema;
  protected int seqNo;
  private boolean lastChunk = false;

  private ECBlock[] outputBlocks;

  private ThreadGroup threadGroup;

  protected Exception exception;

  public ECCoder(BlockGroup blockGroup, DataNode dataNode, ECSchema schema) throws IOException {
    this.blockGroup = blockGroup;
    this.schema = schema;
    dn = dataNode;
    threadGroup = new ThreadGroup("ECInput-"+blockGroup);
    threadGroup.setDaemon(true);

    inputMap = createInputs();
    output = createOutput();
    seqNo = ECChunk.startSeqNo();

  }


  protected abstract Map<ECBlock, ECInput> createInputs() throws IOException;

  protected abstract ECOutput createOutput() throws IOException;

  public abstract ECChunk[] getNextInputChunks(ECBlock[] inputBlocks) throws Exception;

  public abstract  ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks);


  protected void runThreads() {
    //let all input threads run
    for(ECInput input: inputMap.values())
      new Thread(threadGroup, input).start();

  }

  @Override
  public abstract void run();

  public boolean hasNextInputs() {
    return !lastChunk;
  }




  public void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks) throws IOException{
    seqNo++;

    for (int k = 0; k < outputChunks.length; k++) {
      ECChunk rChunk = outputChunks[k];
      rChunk.setSeqNo(seqNo);
      if (lastChunk)
        rChunk.setLast(true);
      output.put(rChunk, outputBlocks[k]);
    }
  }

  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    this.outputBlocks = outputBlocks;

  }

  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    try {

    } catch (Exception e) {
      LOG.error(e);
    }


  }

  protected void check(ECChunk[] chunks) throws ECChunkException {
    int chunkSize = -1;  //each chunk must have the same size
    boolean lastChunk = false; //if one chunk is the last one, then all other chunks should be the last chunk
    for (int i = 0; i < chunks.length; i++) {
      if (i == 0) {
        chunkSize = chunks[i].used();
        lastChunk = chunks[i].isLast();
      } else {
        if (chunkSize != chunks[i].used()) {
          throw new ECChunkException("EC chunks' size don't equal.");
        }
        if (lastChunk != chunks[i].isLast()) {
          //stop all ECInput
          throw new ECChunkException("Some EC chunk is the last chunk but others are not.");
        }
      }

      if (seqNo != chunks[i].getSeqNo()) {
        throw new ECChunkException("EC chunks' sequence number don't equal.");
      }
    }//end of for
  }


}


package org.apache.hadoop.hdfs.server.datanode;

import org.apache.hadoop.hdfs.ec.ECSchema;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * When DataNode receives an encode/decode commands from NameNode,
 * it will call ecWorker.encodeTask() or ecWorker.decodeTask() to
 * handle encode/decode tasks.
 * Each ECWorker can accept multiple encode/decode tasks.
 */

public class ECWorker {

  ExecutorService pool = Executors.newSingleThreadExecutor();

  private DataNode dn;
  private ECSchema schema;

  public ECWorker(DataNode dataNode, ECSchema schema, int maxECWorkAtATime){
    this.dn = dataNode;
    this.schema = schema;
    this.pool =  Executors.newFixedThreadPool(maxECWorkAtATime < 1 ? 1: maxECWorkAtATime);
  }

  public void encodeTask(BlockGroup blockGroup) throws IOException{
    if(blockGroup == null)
      return;

    //create ECEncoder
    ECEncoder ecEncoder = new ECEncoder(blockGroup, dn, schema);
    pool.execute(ecEncoder);
  }

  public void decodeTask(BlockGroup blockGroup) throws IOException{
    if(blockGroup == null)
      return;

    //create ECDecoder
    ECDecoder ecDecoder = new ECDecoder(blockGroup, dn, schema);
    pool.execute(ecDecoder);
  }


}

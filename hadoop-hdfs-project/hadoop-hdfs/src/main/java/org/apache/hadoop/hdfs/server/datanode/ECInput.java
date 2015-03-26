package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.ec.ECBlockReader;
import org.apache.hadoop.ec.datanode.ECChunk;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class ECInput implements Runnable {

  public static final Log LOG = LogFactory.getLog(ECInput.class);

  public static final boolean useDirect = true;
  public static final int ecChunkSize = 64 * 1024;//get from config

  private ECBlockReader blockReader;

  private BlockingQueue<ECChunk> queue;

  public ECInput(ECBlockReader blockReader) {

    this.blockReader = blockReader;
    queue = new LinkedBlockingQueue<ECChunk>();
  }

  public ECChunk get() throws InterruptedException{
    ECChunk c  = queue.take();
    return c;

  }


  /**
   * read contents to create an ECChunk, add this ECChunk to the queue.
   *
   */
  @Override
  public void run() {

    try{
      blockReader.connect();
      boolean last = false;
      int seqNo = ECChunk.startSeqNo();
      long offsetInBlock = 0;

      do{
        ECChunk chunk = ECChunk.getChunk(useDirect, ecChunkSize);
        ByteBuffer buf = chunk.getChunkBuffer();
        int read = blockReader.readAll(buf);
        if(read <= 0 ) { //the last chunk will have no data but just a mark
          chunk.setLast(true);
          chunk.reAllocateBuffer(0);
          last = true;
        }
        chunk.setOffsetInBlock(offsetInBlock);
        chunk.setSeqNo(seqNo++);

        offsetInBlock += read;
        putChunkToQueue(queue, chunk);
        queue.put(chunk); //block if the queue is full

      }while(!last) ;

    } catch (IOException e){
      LOG.info(e);
    } catch(InterruptedException ie){
      LOG.info(ie);
    } finally{
      try{
        close();
      }catch (Exception e){};
    }


  }

  public void putChunkToQueue(BlockingQueue<ECChunk> queue, ECChunk ecChunk) throws InterruptedException{
    ecChunk.getChunkBuffer().flip();
    queue.put(ecChunk);
  }


  public void close(){
    try {
      blockReader.close();
    }catch(IOException e){
      LOG.info("closing block reader", e);
    }
  }

}

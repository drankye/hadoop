package org.apache.hadoop.ec.datanode;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class ECChunk {

  private ByteBuffer buf;
  private boolean isLast;
  private int seqNo;
  private long offsetInBlock;

  private ECChunk(ByteBuffer buffer) {
    this.buf = buffer;
    isLast = false;
    seqNo = startSeqNo();
  }

  public ByteBuffer getBuffer() {
    return buf;
  }

  public boolean isLast(){
    return isLast;
  }

  public void setLast(boolean isLast){
    this.isLast = isLast;
  }

  public void setOffsetInBlock( long offsetInBlock){
    this.offsetInBlock = offsetInBlock;
  }

  public long getOffsetInBlock(){
    return offsetInBlock;
  }

  public void setSeqNo(int seqNo){
    this.seqNo =seqNo;
  }
  public int getSeqNo(){
    return seqNo;
  }

  public int used(){
    return buf.position();
  }
  public int unUsed(){
    return buf.remaining();
  }


  public static ECChunk getChunk(boolean direct, int capacity){
    if(capacity <=0 )
      return null;
    ByteBuffer b = ECByteBufferPool.getBuffer(direct, capacity);
    return new ECChunk(b);
  }

  public static ECChunk[] getChunks(boolean direct, int capacity, int chunks){
    if(chunks < 0)
      return null;
    ECChunk[] ret = new ECChunk[chunks];
    for(int i = 0; i < chunks; i++){
      ret[i] = getChunk(direct, capacity);
    }
    return ret;
  }

  public static void returnChunk(ECChunk chunk){
    if(chunk == null)
      return;
    ECByteBufferPool.putBuffer(chunk.getBuffer());
  }

  public static void returnChunks(ECChunk[] chunkArray){
    if(chunkArray == null)
       return;
    for(ECChunk chunk: chunkArray){
      returnChunk(chunk);
    }
  }

  /**
   * the first sequence no that ECChunk starts
   * @return
   */
  public static int startSeqNo(){
    return 0;
  }



  public void reAllocateBuffer(int newcapacity){
    ECByteBufferPool.putBuffer(buf);
    buf = ECByteBufferPool.getBuffer(buf.isDirect(), newcapacity);
  }



}
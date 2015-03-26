package org.apache.hadoop.ec.datanode;


import io.netty.buffer.ByteBuf;
import org.apache.hadoop.io.ElasticByteBufferPool;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

public class ECByteBufferPool {  //use it as a member
  // private static final int ECChunkSize=1024*512;// get from config  or ECWorker

  private static final ByteBuffer emptyByteBuffer = ByteBuffer.allocate(0);
  private static final ByteBuffer emptyByteBufferDirect = ByteBuffer.allocateDirect(0);
  private static final ElasticByteBufferPool pool = new ElasticByteBufferPool();

  public static byte[] getBufferArray(int length) {
    ByteBuffer bb = getBuffer(false, length);
    return bb.array();
  }
  public static ByteBuffer getBuffer(boolean direct, int length) {
    if(length <= 0){
      if(direct)
        return emptyByteBufferDirect;
      return emptyByteBuffer;
    }
    ByteBuffer buf = pool.getBuffer(direct, length);
    if( buf != null)
      buf.clear();
    return buf;
  }

  public static void putBufferArray(byte[] buf){
    if(buf.length == 0)
      putBuffer(emptyByteBuffer);
    else{
      ByteBuffer bb = ByteBuffer.wrap(buf);
      putBuffer(bb);
    }
  }
  public static void putBuffer(ByteBuffer buffer) {
    if(buffer == emptyByteBuffer ||
        buffer == emptyByteBufferDirect )
      return;
    pool.putBuffer(buffer);
  }
}





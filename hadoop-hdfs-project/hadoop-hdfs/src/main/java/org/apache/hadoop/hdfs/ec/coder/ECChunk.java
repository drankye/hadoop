package org.apache.hadoop.hdfs.ec.coder;

import java.nio.ByteBuffer;

public class ECChunk {

  private ByteBuffer chunkBuffer;

  public ECChunk(ByteBuffer buffer) {
    this.chunkBuffer = buffer;
  }

  public ByteBuffer getChunkBuffer() {
    return chunkBuffer;
  }
}

package org.apache.hadoop.hdfs.ec.coder;

public class AbstractErasureCoder implements ErasureCoder {


  @Override
  public void encode(ECChunk[] dataChunks, ECChunk outputChunk) {

  }

  @Override
  public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {

  }

  @Override
  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk outputChunk) {

  }

  @Override
  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk[] outputChunks) {

  }
}

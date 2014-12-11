package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECChunk;

public abstract class AbstractErasureCoderCallback implements ErasureCoderCallback {

  @Override
  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {

  }

  @Override
  public void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks) {

  }

  @Override
  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {

  }
}

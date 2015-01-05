package org.apache.hadoop.io.ec.coder;

import org.apache.hadoop.io.ec.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;

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

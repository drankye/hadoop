package org.apache.hadoop.io.ec.coder;

import org.apache.hadoop.io.ec.ECBlock;
import org.apache.hadoop.io.ec.ECChunk;

/**
 * An abstract erasure coder callback to help the implementing.
 */
public abstract class AbstractErasureCoderCallback implements ErasureCoderCallback {

  @Override
  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    // Noop by default
  }

  @Override
  public void withCoded(ECChunk[] inputChunks, ECChunk[] outputChunks) {
    // Noop by default
  }

  @Override
  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    // Noop by default
  }
}

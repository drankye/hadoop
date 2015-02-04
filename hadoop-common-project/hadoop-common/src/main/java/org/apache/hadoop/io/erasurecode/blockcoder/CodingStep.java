package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;

/**
 * Erasure coding step
 */
public interface CodingStep {

  public ECBlock[] getInputBlocks();

  public ECBlock[] getOutputBlocks();

  public void performCoding(ECChunk[] inputChunks, ECChunk[] outputChunks);

  public void finish();
}

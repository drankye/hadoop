package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

/**
 * Erasure encoding step, a wrapper of all the necessary information to perform
 * an encoding step involved in the whole process of encoding a block group.
 */
public class ErasureEncodingStep extends AbstractErasureCodingStep {

  private RawErasureEncoder rawEncoder;

  /**
   * The constructor with all the necessary info.
   * @param inputBlocks
   * @param outputBlocks
   * @param rawEncoder
   */
  public ErasureEncodingStep(ECBlock[] inputBlocks, ECBlock[] outputBlocks,
                             RawErasureEncoder rawEncoder) {
    super(inputBlocks, outputBlocks);
    this.rawEncoder = rawEncoder;
  }

  @Override
  public void performCoding(ECChunk[] inputChunks, ECChunk[] outputChunks) {
    rawEncoder.encode(inputChunks, outputChunks);
  }

}

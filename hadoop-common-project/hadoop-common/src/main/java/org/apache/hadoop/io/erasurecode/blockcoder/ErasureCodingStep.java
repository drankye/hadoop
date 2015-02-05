package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;

/**
 * Erasure coding step that's involved in encoding/decoding of a block group.
 */
public interface ErasureCodingStep {

  /**
   * Input blocks of readable data involved in this step, may be data blocks
   * or parity blocks.
   * @return input blocks
   */
  public ECBlock[] getInputBlocks();

  /**
   * Output blocks of writable buffers involved in this step, may be data
   * blocks or parity blocks.
   * @return output blocks
   */
  public ECBlock[] getOutputBlocks();

  /**
   * Perform encoding or decoding given the input chunks, and generated results
   * will be written to the output chunks.
   * @param inputChunks
   * @param outputChunks
   */
  public void performCoding(ECChunk[] inputChunks, ECChunk[] outputChunks);

  /**
   * Notify erasure coder that all the chunks of input blocks are processed so
   * the coder can be able to update internal states, considering next step.
   */
  public void finish();
}

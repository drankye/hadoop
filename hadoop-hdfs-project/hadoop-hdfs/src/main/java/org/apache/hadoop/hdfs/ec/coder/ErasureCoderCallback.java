package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECChunk;

/**
 * Callback to be called by ECWorker to get input/output ECChunks for an ECBlock
 */
public interface ErasureCoderCallback {

  /**
   * Prepare for reading input blocks and writing output blocks
   * @param inputBlocks
   * @param outputBlocks
   */
  public void beforeCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);

  /**
   * Any next input chunk for coding?
   * @return
   */
  public boolean hasNextInputs();

  /**
   * Get next input chunks for the blocks while coding
   * @param inputBlocks
   * @return
   */
  public ECChunk[] getNextInputChunks(ECBlock[] inputBlocks);

  /**
   * Get next chunks to output for the blocks while coding
   * @param outputBlocks
   * @return
   */
  public ECChunk[] getNextOutputChunks(ECBlock[] outputBlocks);

  /**
   * Done with coding of group, chances to close input blocks and flush output blocks
   */
  public void postCoding(ECBlock[] inputBlocks, ECBlock[] outputBlocks);
}

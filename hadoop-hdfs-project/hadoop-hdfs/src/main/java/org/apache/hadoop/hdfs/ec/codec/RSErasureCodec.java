package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.ec.BlockGroup;

import java.util.List;

/**
 * Reed-Solomon codec
 */
public abstract class RSErasureCodec extends ErasureCodec {


  /**
   * Calculating BlockGroup according to Reed-Solomon algorithm
   * @param dataBlocks Data blocks to compute parity blocks against
   * @param parityBlocks To be computed parity blocks
   * @return
   */
  @Override
  public BlockGroup makeBlockGroup(List<ExtendedBlockId> dataBlocks, List<ExtendedBlockId> parityBlocks) {
    return null;
  }

}

package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.rawcoder.IsaRSRawEncoder;

public class IsaRSEncoder extends AbstractErasureEncoder {

  public IsaRSEncoder(int dataSize, int paritySize, int chunkSize) {
    super(new IsaRSRawEncoder(dataSize, paritySize, chunkSize));
  }

  @Override
  public void encode(BlockGroup blockGroup) {

  }
}

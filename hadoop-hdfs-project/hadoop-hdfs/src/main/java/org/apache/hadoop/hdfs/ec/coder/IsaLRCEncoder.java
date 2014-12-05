package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;

public class IsaLRCEncoder extends AbstractErasureEncoder {


  public IsaLRCEncoder(int dataSize, int paritySize, int chunkSize) {
    super(null);
  }

  @Override
  public void encode(BlockGroup blockGroup) {

  }
}

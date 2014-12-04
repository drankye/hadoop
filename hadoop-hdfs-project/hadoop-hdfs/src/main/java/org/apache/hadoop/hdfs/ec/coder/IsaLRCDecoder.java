package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;

public class IsaLRCDecoder extends AbstractErasureDecoder {

  public IsaLRCDecoder(int dataSize, int paritySize, int chunkSize) {
    super(null);
  }

  @Override
  public void decode(BlockGroup blockGroup) {

  }
}

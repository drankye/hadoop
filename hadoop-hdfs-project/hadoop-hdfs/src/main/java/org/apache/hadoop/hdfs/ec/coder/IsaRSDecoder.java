package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.rawcoder.IsaRSRawDecoder;

public class IsaRSDecoder extends AbstractErasureDecoder {

  public IsaRSDecoder(int dataSize, int paritySize, int chunkSize) {
    super(new IsaRSRawDecoder(dataSize, paritySize, chunkSize));
  }

  @Override
  public void decode(BlockGroup blockGroup) {

  }
}

package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;

public interface ErasureDecoder {

  public void decode(BlockGroup blockGroup);

}

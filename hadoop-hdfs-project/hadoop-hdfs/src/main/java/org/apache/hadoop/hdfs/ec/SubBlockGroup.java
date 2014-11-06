package org.apache.hadoop.hdfs.ec;

import org.apache.hadoop.hdfs.ExtendedBlockId;

import java.util.List;

public class SubBlockGroup {

  private List<ExtendedBlockId> parityBlock;
  private List<ExtendedBlockId> dataBlocks;

  public List<ExtendedBlockId> getParityBlock() {
    return parityBlock;
  }

  public List<ExtendedBlockId> getDataBlocks() {
    return dataBlocks;
  }
}

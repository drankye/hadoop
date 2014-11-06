package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.IsaLRCErasureCoder;

/**
 * LRC codec implemented using ISA library
 */
public class IsaLRCErasureCodec extends LRCErasureCodec {


  @Override
  public ErasureCoder createErasureCoder() {
    return new IsaLRCErasureCoder();
  }

}

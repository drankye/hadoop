package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.IsaRSErasureCoder;

/**
 * Reed-Solomon codec with ISA library implemented coder
 */
public class IsaRSErasureCodec extends RSErasureCodec {


  @Override
  public ErasureCoder createErasureCoder() {
    return new IsaRSErasureCoder();
  }

  
}

package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.JerasureRSErasureCoder;

import java.util.List;

/**
 * Reed-Solomon codec with Jerasure library implemented coder
 */
public class JerasureRSErasureCodec extends RSErasureCodec {


  @Override
  public ErasureCoder createErasureCoder() {
    return new JerasureRSErasureCoder();
  }


}

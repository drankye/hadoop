package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;

/**
 *
 */
public interface ErasureEncoder {

    public void encode(BlockGroup blockGroup);

}

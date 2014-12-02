package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;

/**
 */
public interface ErasureEncoder {

    public void encode(ECChunk[] dataChunks, ECChunk outputChunk);

    public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks);

}

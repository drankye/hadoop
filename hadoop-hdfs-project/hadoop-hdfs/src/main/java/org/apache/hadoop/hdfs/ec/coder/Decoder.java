package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;

public interface Decoder {

    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk outputChunk);

    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk[] outputChunks);
}

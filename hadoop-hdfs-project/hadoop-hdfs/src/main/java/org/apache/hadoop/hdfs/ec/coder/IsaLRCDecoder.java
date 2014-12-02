package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;

public class IsaLRCDecoder implements ErasureDecoder {
    @Override
    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk outputChunk) {
        //TODO
    }

    @Override
    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk[] outputChunks) {
        //TODO
    }
}

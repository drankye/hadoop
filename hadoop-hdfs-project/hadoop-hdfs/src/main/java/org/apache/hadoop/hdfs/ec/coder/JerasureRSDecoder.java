package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;

public class JerasureRSDecoder implements  Decoder{
    @Override
    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, String annotation, ECChunk outputChunk) {
        //TODO
    }

    @Override
    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
                       String annotation, ECChunk[] outputChunks) {
        //TODO
    }
}

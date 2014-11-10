package org.apache.hadoop.hdfs.ec.coder;

import java.util.List;

/**
 * Erasure Coder for encoding and decoding of ECChunks
 */
public interface ErasureCoder {

  public void encode(ECChunk[] dataChunks, ECChunk outputChunk);

  public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks);

  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
                     ECChunk outputChunk);

  public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
                     ECChunk[] outputChunks);
}

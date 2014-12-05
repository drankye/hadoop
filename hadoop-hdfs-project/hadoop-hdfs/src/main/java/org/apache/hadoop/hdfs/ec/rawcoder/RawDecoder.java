package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

/**
 * Raw Erasure Decoder that corresponds to an erasure code algorithm
 */
public interface RawDecoder {

    public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes);

    /**
     * The number of data elements in the code.
     */
    public int dataSize();

    /**
     * The number of parity elements in the code.
     */
    public int paritySize();
}

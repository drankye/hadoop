package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

/**
 * Raw Erasure Encoder that corresponds to an erasure code algorithm
 *
 */
public interface RawEncoder {

    /**
     * This method would be overridden in the subclass,
     * so that the subclass will have its own encodeBulk behavior.
     */
    public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs);

    /**
     * The number of data elements in the code.
     */
    public int dataSize();

    /**
     * The number of parity elements in the code.
     */
    public int paritySize();


}

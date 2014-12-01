package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

public abstract  class AbstractRawEncoder implements  RawEncoder{

    private int dataSize;
    private int paritySize;
    private int chunkSize;

    public AbstractRawEncoder(int dataSize, int paritySize, int chunkSize) {
        this.dataSize = dataSize;
        this.paritySize = paritySize;
        this.chunkSize = chunkSize;
    }

    @Override
    public abstract void encode(ByteBuffer[] inputs, ByteBuffer[] outputs);

    /**
     * The number of elements in the message.
     */
    public int dataSize() {
        return dataSize;
    }

    /**
     * The number of elements in the code.
     */
    public int paritySize() {
        return paritySize;
    }

    /**
     * Chunk buffer size for an encod()/decode() call
     * @return
     */
    public int chunkSize() {
        return chunkSize;
    }
}

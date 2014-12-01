package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

public abstract  class AbstractRawDecoder implements  RawDecoder{

    private int dataSize;
    private int paritySize;
    private int chunkSize;


    public AbstractRawDecoder(int dataSize, int paritySize, int chunkSize) {
        this.dataSize = dataSize;
        this.paritySize = paritySize;
        this.chunkSize = chunkSize;
    }

    @Override
    public abstract void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes);

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

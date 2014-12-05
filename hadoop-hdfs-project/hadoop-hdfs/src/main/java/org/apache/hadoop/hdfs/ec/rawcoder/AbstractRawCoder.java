package org.apache.hadoop.hdfs.ec.rawcoder;

public abstract class AbstractRawCoder {

    private int dataSize;
    private int paritySize;
    private int chunkSize;

    public AbstractRawCoder(int dataSize, int paritySize, int chunkSize) {
        this.dataSize = dataSize;
        this.paritySize = paritySize;
        this.chunkSize = chunkSize;
    }

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
     * Chunk buffer size for an encode()/decode() call
     * @return
     */
    public int chunkSize() {
        return chunkSize;
    }
}

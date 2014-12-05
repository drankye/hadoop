package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

public abstract class AbstractRawEncoder extends AbstractRawCoder implements RawEncoder {

    public AbstractRawEncoder(int dataSize, int paritySize, int chunkSize) {
        super(dataSize, paritySize, chunkSize);
    }

    @Override
    public abstract void encode(ByteBuffer[] inputs, ByteBuffer[] outputs);

}

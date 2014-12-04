package org.apache.hadoop.hdfs.ec.rawcoder;

import java.nio.ByteBuffer;

public abstract class AbstractRawDecoder extends AbstractRawCoder implements RawDecoder{

    public AbstractRawDecoder(int dataSize, int paritySize, int chunkSize) {
      super(dataSize, paritySize, chunkSize);
    }

    @Override
    public abstract void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes);

}

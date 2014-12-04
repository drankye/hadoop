package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECBlock;
import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.SubBlockGroup;
import org.apache.hadoop.hdfs.ec.coder.util.TransformUtil;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawEncoder;

import java.nio.ByteBuffer;

public class JavaRSEncoder extends AbstractErasureEncoder {

    public JavaRSEncoder(int dataSize, int paritySize, int chunkSize) {
        super(new JavaRSRawEncoder(dataSize, paritySize, chunkSize));
    }

    @Override
    public void encode(BlockGroup blockGroup) {
      SubBlockGroup subGroup = blockGroup.getSubGroups().iterator().next();

      ECBlock[] inputBlocks = subGroup.getDataBlocks();
      ECBlock[] outputBlocks = subGroup.getParityBlocks();
      getCallback().beforeCoding(inputBlocks, outputBlocks);

      while (getCallback().hasNextInputs()) {
        ECChunk[] dataChunks = getCallback().getNextInputChunks(inputBlocks);
        ECChunk[] parityChunks = getCallback().getNextOutputChunks(outputBlocks);
        encode(dataChunks, parityChunks);
      }

      getCallback().postCoding(inputBlocks, outputBlocks);
    }

    private void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {
        ByteBuffer[] dataByteBuffers = TransformUtil.changeToByteBufferArray(dataChunks);
        ByteBuffer[] outputByteBuffers = TransformUtil.changeToByteBufferArray(outputChunks);

        getRawEncoder().encode(dataByteBuffers, outputByteBuffers);
    }

}

package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.coder.util.TransformUtil;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawEncoder;
import org.apache.hadoop.hdfs.ec.rawcoder.RawEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class JavaRSEncoder implements  Encoder{

    private RawEncoder rawEncoder;

    public JavaRSEncoder(int dataSize, int paritySize, int chunkSize) {
        rawEncoder = new JavaRSRawEncoder(dataSize, paritySize, chunkSize);
    }

    @Override
    public void encode(ECChunk[] dataChunks, ECChunk outputChunk) {
        encode(dataChunks, new ECChunk[]{outputChunk});
    }

    public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {
        ByteBuffer[] dataByteBuffers = TransformUtil.changeToByteBufferArray(dataChunks);
        ByteBuffer[] outputByteBuffers = TransformUtil.changeToByteBufferArray(outputChunks);

        rawEncoder.encode(dataByteBuffers, outputByteBuffers);
    }







}

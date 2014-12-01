package org.apache.hadoop.hdfs.ec.coder.util;

import org.apache.hadoop.hdfs.ec.ECChunk;

import java.nio.ByteBuffer;

public class TransformUtil {

    public  static  ByteBuffer[] changeToByteBufferArray(ECChunk[] writeBufs) {
        ByteBuffer[] buffers = new ByteBuffer[writeBufs.length];

        for (int i = 0; i < writeBufs.length; i++) {
            buffers[i] = writeBufs[i].getChunkBuffer();
        }
        return buffers;
    }
}

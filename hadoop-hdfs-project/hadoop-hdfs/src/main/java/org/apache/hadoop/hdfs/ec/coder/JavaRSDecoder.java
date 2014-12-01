package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.coder.util.TransformUtil;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawDecoder;
import org.apache.hadoop.hdfs.ec.rawcoder.RawDecoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class JavaRSDecoder implements  Decoder{

    private RawDecoder rawDecoder;

    //XXX
    private int dataSize;
    private int paritySize;
    private int chunkSize;

    public JavaRSDecoder(int dataSize, int paritySize, int chunkSize) {
        this.dataSize = dataSize;
        this.paritySize = paritySize;
        this.chunkSize = chunkSize;
        rawDecoder = new JavaRSRawDecoder(dataSize, paritySize, chunkSize);
    }

    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
                       String annotation, ECChunk outputChunk) {
        decode(dataChunks, parityChunks, annotation, new ECChunk[]{outputChunk});
    }

    public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
                       String annotation, ECChunk[] outputChunks) {
        ECChunk[] readChunks = combineArrays(parityChunks, dataChunks);
        ByteBuffer[] readBufs = TransformUtil.changeToByteBufferArray(readChunks);
        ByteBuffer[] outputBufs = TransformUtil.changeToByteBufferArray(outputChunks);

        List<Integer> erasedLocationList = getErasedLocation(annotation);
        int[] erasedLocationArray = new int[erasedLocationList.size()];
        for (int i = 0; i < erasedLocationList.size(); i++) {
            erasedLocationArray[i] = erasedLocationList.get(i);
        }

        rawDecoder.decode(readBufs, outputBufs, erasedLocationArray);
    }

    private ECChunk[] combineArrays(ECChunk[] array1, ECChunk[] array2) {
        ECChunk[] result = new ECChunk[array1.length + array2.length];
        for (int i = 0; i < array1.length; ++i) {
            result[i] = array1[i];
        }
        for (int i = 0; i < array2.length; ++i) {
            result[i + array1.length] = array2[i];
        }
        return result;
    }

    private List<Integer> getErasedLocation(final String annotation) {
        List<Integer> erasedLocationArrayList = new ArrayList<Integer>();

        for (int i = 0; i < annotation.length(); i += 2) {
            char c = annotation.charAt(i);
            if (c == '_') {
                int erasedIndexInString = i / 2;
                if (erasedIndexInString >= dataSize ) {
                    erasedLocationArrayList.add(erasedIndexInString - dataSize);
                } else {
                    erasedLocationArrayList.add(erasedIndexInString + paritySize);
                }
            }
        }

        return erasedLocationArrayList;
    }
}

package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.coder.util.TransformUtil;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawDecoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class JavaRSDecoder extends AbstractErasureDecoder {

  public JavaRSDecoder(int dataSize, int paritySize, int chunkSize) {
    super(new JavaRSRawDecoder(dataSize, paritySize, chunkSize));
  }

  @Override
  public void decode(BlockGroup blockGroup) {

  }

  private void decode(ECChunk[] dataChunks, ECChunk[] parityChunks, ECChunk[] outputChunks) {
    ECChunk[] readChunks = combineArrays(parityChunks, dataChunks);

    int[] erasedLocations = getErasedLocationAndCleanUpDirtyData(readChunks);

    ByteBuffer[] readBuffs = TransformUtil.changeToByteBufferArray(readChunks);
    ByteBuffer[] outputBuffs = TransformUtil.changeToByteBufferArray(outputChunks);

    getRawDecoder().decode(readBuffs, outputBuffs, erasedLocations);
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

  private int[] getErasedLocationAndCleanUpDirtyData(ECChunk[] chunks) {
    List<Integer> erasedLocationList = new ArrayList<Integer>();
    for (int i = 0; i < chunks.length; i++) {
      if (chunks[i].isMissing()) {
        erasedLocationList.add(i);
        chunks[i].fillZero();
      }
    }

    int[] erasedLocationArray = new int[erasedLocationList.size()];
    for (int i = 0; i < erasedLocationList.size(); i++) {
      erasedLocationArray[i] = erasedLocationList.get(i);
    }
    return erasedLocationArray;
  }
}

package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.rawcoder.JavaRSRawErasureCoder;
import org.apache.hadoop.hdfs.ec.rawcoder.RawErasureCoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The codes is base on ReedSolomonCode in hadoop-raid
 *
 */
public class JavaRSErasureCoder extends RSErasureCoder {

	private RawErasureCoder rawErasureCoder;

	@Override
	public void initWith(ECSchema schema) {
		super.initWith(schema);
		
		//FIXME get data size and parity size from schema options
		String dataSize = schema.getOptions().get("k");
		schema.setDataBlocks(Integer.parseInt(dataSize));
		String paritySize = schema.getOptions().get("m");
		schema.setParityBlocks(Integer.parseInt(paritySize));
		
		rawErasureCoder = new JavaRSRawErasureCoder(schema.getDataBlocks(), schema.getParityBlocks());
	}

	@Override
	public void encode(ECChunk[] dataChunks, ECChunk outputChunk) {
		encode(dataChunks, new ECChunk[]{outputChunk});
	}

	@Override
	public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {
		ByteBuffer[] dataByteBuffers = changeToByteBufferArray(dataChunks);
		ByteBuffer[] outputByteBuffers = changeToByteBufferArray(outputChunks);
		
		rawErasureCoder.encode(dataByteBuffers, outputByteBuffers);
	}

	@Override
	public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
			String annotation, ECChunk outputChunk) {
		decode(dataChunks, parityChunks, annotation, new ECChunk[]{outputChunk});
	}

	@Override
	public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
			String annotation, ECChunk[] outputChunks) {
		ECChunk[] readChunks = combineArrays(parityChunks, dataChunks);
		ByteBuffer[] readBufs = changeToByteBufferArray(readChunks);
		ByteBuffer[] outputBufs = changeToByteBufferArray(outputChunks);
		
		List<Integer> erasedLocationList = getErasedLocation(annotation);
		int[] erasedLocationArray = new int[erasedLocationList.size()];
		for (int i = 0; i < erasedLocationList.size(); i++) {
			erasedLocationArray[i] = erasedLocationList.get(i);
		}
		
		rawErasureCoder.decode(readBufs, outputBufs, erasedLocationArray);
	}
	
	public int symbolSize() {
		return rawErasureCoder.symbolSize();
	}

	private List<Integer> getErasedLocation(final String annotation) {
		List<Integer> erasedLocationArrayList = new ArrayList<Integer>();

		for (int i = 0; i < annotation.length(); i += 2) {
			char c = annotation.charAt(i);
			if (c == '_') {
				int erasedIndexInString = i / 2;
				if (erasedIndexInString >= schema.getDataBlocks() ) {
					erasedLocationArrayList.add(erasedIndexInString - schema.getDataBlocks());
				} else {
					erasedLocationArrayList.add(erasedIndexInString + schema.getParityBlocks());
				}
			}
		}

		return erasedLocationArrayList;
	}

	private ByteBuffer[] changeToByteBufferArray(ECChunk[] writeBufs) {
		ByteBuffer[] buffers = new ByteBuffer[writeBufs.length];
		
		for (int i = 0; i < writeBufs.length; i++) {
			buffers[i] = writeBufs[i].getChunkBuffer();
		}
		return buffers;
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

}

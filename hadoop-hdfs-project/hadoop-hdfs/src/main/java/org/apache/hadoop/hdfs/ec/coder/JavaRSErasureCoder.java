package org.apache.hadoop.hdfs.ec.coder;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.coder.old.impl.help.GaloisField;

import java.nio.ByteBuffer;

/**
 * The codes is base on ReedSolomonCode in hadoop-raid
 *
 */
public class JavaRSErasureCoder extends RSErasureCoder {

	private int[] generatingPolynomial;
	private int PRIMITIVE_ROOT = 2;
	private int[] primitivePower;
	private GaloisField GF = GaloisField.getInstance();
	private int[] paritySymbolLocations;
	private int[] errSignature;
	private int[] dataBuff;
	
	private RawErasureCoder rawErasureCoder;

	@Override
	public void initWith(ECSchema schema) {
		super.initWith(schema);
		init(schema.getDataBlocks(), schema.getParityBlocks());
	}

	private void init(int dataSize, int paritySize) {
		assert (dataSize + paritySize < GF.getFieldSize());
		 this.errSignature = new int[paritySize];
		this.paritySymbolLocations = new int[paritySize];
		 this.dataBuff = new int[paritySize + dataSize];
		this.rawErasureCoder = new AbstractRawErasureCoder(dataSize, paritySize);
		for (int i = 0; i < paritySize; i++) {
			paritySymbolLocations[i] = i;
		}

		this.primitivePower = new int[dataSize + paritySize];
		// compute powers of the primitive root
		for (int i = 0; i < dataSize + paritySize; i++) {
			primitivePower[i] = GF.power(PRIMITIVE_ROOT, i);
		}
		// compute generating polynomial
		int[] gen = { 1 };
		int[] poly = new int[2];
		for (int i = 0; i < paritySize; i++) {
			poly[0] = primitivePower[i];
			poly[1] = 1;
			gen = GF.multiply(gen, poly);
		}
		// generating polynomial has all generating roots
		generatingPolynomial = gen;
	}

	@Override
	public void encode(ECChunk[] dataChunks, ECChunk outputChunk) {
	}

	@Override
	public void encode(ECChunk[] dataChunks, ECChunk[] outputChunks) {
		final int dataSize = rawErasureCoder.dataSize();
		final int paritySize = rawErasureCoder.paritySize();
		assert (dataSize == dataChunks.length);
		assert (paritySize == outputChunks.length);

		for (int i = 0; i < outputChunks.length; i++) {
			outputChunks[i].fillZero();
		}

		byte[][] data = new byte[dataSize + paritySize][];

		for (int i = 0; i < paritySize; i++) {
			data[i] = outputChunks[i].getChunkBuffer().array();
		}
		for (int i = 0; i < dataSize; i++) {
			data[i + paritySize] = dataChunks[i].getChunkBuffer().array();
		}

		// Compute the remainder
		GF.remainder(data, generatingPolynomial);

	}

	@Override
	public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
			String annotation, ECChunk outputChunk) {
	}

	@Override
	public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
			String annotation, ECChunk[] outputChunks) {
		ECChunk[] readChunks = combineArrays(parityChunks, dataChunks);
		ArrayList<Integer> erasedLocation = getErasedLocation(annotation);
		decode(readChunks, outputChunks, erasedLocation);
	}
	
	public int symbolSize() {
		return rawErasureCoder.symbolSize();
	}

	private ArrayList<Integer> getErasedLocation(final String annotation) {
		ArrayList<Integer> erasedLocationArrayList = new ArrayList<Integer>();

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

	private void decode(ECChunk[] readBufs, ECChunk[] writeBufs, ArrayList<Integer> erasedLocation) {
		if (erasedLocation.size() == 0) {
			return;
		}

		// cleanup the write buffer
		for (int i = 0; i < writeBufs.length; i++) {
			writeBufs[i].fillZero();
		}

		ByteBuffer[] readBuffersWithByteBuffers = changeByteBufferArray(readBufs);
		ByteBuffer[] writeBuffersWithByteBuffers = changeByteBufferArray(writeBufs);
		
		for (int i = 0; i < erasedLocation.size(); i++) {
			errSignature[i] = primitivePower[erasedLocation.get(i)];
			GF.substitute(readBuffersWithByteBuffers, writeBuffersWithByteBuffers[i], primitivePower[i]);
		}
		GF.solveVandermondeSystem(errSignature, writeBuffersWithByteBuffers,
				erasedLocation.size(), readBufs[0].getChunkBuffer().array().length);
	}

	private ByteBuffer[] changeByteBufferArray(ECChunk[] writeBufs) {
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

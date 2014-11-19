package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.coder.old.impl.help.GaloisField;

public class JavaRSErasureCoder extends RSErasureCoder {

	private int[] generatingPolynomial;
	private int PRIMITIVE_ROOT = 2;
	private int[] primitivePower;
	private GaloisField GF = GaloisField.getInstance();
	private int[] paritySymbolLocations;
	
	private RawErasureCoder rawErasureCoder;

	public JavaRSErasureCoder() {
		init(schema.getDataBlocks(), schema.getParityBlocks());
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
			ECChunk outputChunk) {

	}

	@Override
	public void decode(ECChunk[] dataChunks, ECChunk[] parityChunks,
			ECChunk[] outputChunks) {

	}
	
	private void init(int dataSize, int paritySize) {
	    assert(dataSize + paritySize < GF.getFieldSize());
//	    this.errSignature = new int[paritySize];
	    this.paritySymbolLocations = new int[paritySize];
//	    this.dataBuff = new int[paritySize + dataSize];
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
	    int[] gen = {1};
	    int[] poly = new int[2];
	    for (int i = 0; i < paritySize; i++) {
	      poly[0] = primitivePower[i];
	      poly[1] = 1;
	      gen = GF.multiply(gen, poly);
	    }
	    // generating polynomial has all generating roots
	    generatingPolynomial = gen;
	  }
}

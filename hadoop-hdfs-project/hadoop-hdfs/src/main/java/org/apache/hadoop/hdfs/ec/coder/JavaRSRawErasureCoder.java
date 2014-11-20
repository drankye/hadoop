package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.coder.old.impl.help.GaloisField;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Raw Erasure Coder that corresponds to an erasure code algorithm
 */
public class JavaRSRawErasureCoder extends AbstractRawErasureCoder {
	private int[] generatingPolynomial;
	private int PRIMITIVE_ROOT = 2;
	private int[] primitivePower;
	private GaloisField GF = GaloisField.getInstance();
	private int[] paritySymbolLocations;
	private int[] errSignature;
	
	public JavaRSRawErasureCoder(int dataSize, int paritySize) {
		super(dataSize, paritySize);
		init();
	}
	
	private void init() {
		assert (dataSize + paritySize < GF.getFieldSize());
		 this.errSignature = new int[paritySize];
		this.paritySymbolLocations = new int[paritySize];
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
	public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
		assert (dataSize == inputs.length);
		assert (paritySize == outputs.length);

		for (int i = 0; i < inputs.length; i++) {
			Arrays.fill(outputs, 0);
		}

		byte[][] data = new byte[dataSize + paritySize][];

		for (int i = 0; i < paritySize; i++) {
			data[i] = outputs[i].array();
		}
		for (int i = 0; i < dataSize; i++) {
			data[i + paritySize] = inputs[i].array();
		}
		// Compute the remainder
		GF.remainder(data, generatingPolynomial);
	}

	@Override
	public void decode(ByteBuffer[] readBufs, ByteBuffer[] writeBufs, int[] erasedLocation) {
		if (erasedLocation.length == 0) {
			return;
		}

		// cleanup the write buffer
		for (int i = 0; i < writeBufs.length; i++) {
			Arrays.fill(writeBufs, 0);
		}

		for (int i = 0; i < erasedLocation.length; i++) {
			errSignature[i] = primitivePower[erasedLocation[i]];
			GF.substitute(readBufs, writeBufs[i], primitivePower[i]);
		}
		GF.solveVandermondeSystem(errSignature, writeBufs,
				erasedLocation.length, readBufs[0].array().length);
	}
}

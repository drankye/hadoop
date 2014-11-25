package org.apache.hadoop.hdfs.ec.rawcoder;

import org.apache.hadoop.hdfs.ec.rawcoder.impl.IsaReedSolomonDecoder;
import org.apache.hadoop.hdfs.ec.rawcoder.impl.IsaReedSolomonEncoder;

import java.nio.ByteBuffer;

/**
 * Raw Erasure Coder that uses ISA-L library
 */
public class IsaRSRawErasureCoder extends AbstractRawErasureCoder {
	private IsaReedSolomonEncoder encoder;
  private IsaReedSolomonDecoder decoder;

	public IsaRSRawErasureCoder(int dataSize, int paritySize) {
		super(dataSize, paritySize);
		init();
	}

	private void init() {
    this.encoder = new IsaReedSolomonEncoder(dataSize, paritySize);
    this.decoder = new IsaReedSolomonDecoder(dataSize, paritySize);
	}

	@Override
	public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
		assert (dataSize == inputs.length);
		assert (paritySize == outputs.length);

    encoder.encode(inputs, outputs, getChunkSize());
	}

	@Override
	public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasuredIndexes) {
		if (erasuredIndexes.length == 0) {
			return;
		}

    ByteBuffer[] allData = null; // TODO: inputs + outputs;
    decoder.decode(allData, erasuredIndexes, getChunkSize());
	}
}

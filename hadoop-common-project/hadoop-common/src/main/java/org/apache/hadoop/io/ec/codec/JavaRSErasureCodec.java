package org.apache.hadoop.io.ec.codec;

import org.apache.hadoop.io.ec.coder.ErasureDecoder;
import org.apache.hadoop.io.ec.coder.ErasureEncoder;
import org.apache.hadoop.io.ec.coder.JavaRSDecoder;
import org.apache.hadoop.io.ec.coder.JavaRSEncoder;

/**
 * Reed-Solomon codec with Java code
 */
public class JavaRSErasureCodec extends RSErasureCodec{

	@Override
	public ErasureEncoder createEncoder() {
		return new JavaRSEncoder(getSchema().getDataBlocks(), getSchema().getParityBlocks(), getSchema().getChunkSize());
	}

	@Override
	public ErasureDecoder createDecoder() {
		return new JavaRSDecoder(getSchema().getDataBlocks(), getSchema().getParityBlocks(), getSchema().getChunkSize());
	}
}

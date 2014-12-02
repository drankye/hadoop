package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.coder.*;

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

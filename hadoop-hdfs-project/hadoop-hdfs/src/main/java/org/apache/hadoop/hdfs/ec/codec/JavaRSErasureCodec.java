package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.coder.*;

/**
 * Reed-Solomon codec with Java code
 */
public class JavaRSErasureCodec extends RSErasureCodec{

	@Override
	public Encoder createEncoder() {
		return new JavaRSEncoder(getSchema().getDataBlocks(), getSchema().getParityBlocks(), getSchema().getChunkSize());
	}

	@Override
	public Decoder createDecoder() {
		return new JavaRSDecoder(getSchema().getDataBlocks(), getSchema().getParityBlocks(), getSchema().getChunkSize());
	}
}

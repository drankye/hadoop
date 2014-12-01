package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The codes is base on ReedSolomonCode in hadoop-raid
 *
 */
public class JavaRSErasureCoder extends RSErasureCoder {

	@Override
	public void initWith(ECSchema schema) {
		super.initWith(schema);
		//XXX get data size and parity size from schema options
		String dataSize = schema.getOptions().get("k");
		schema.setDataBlocks(Integer.parseInt(dataSize));
		String paritySize = schema.getOptions().get("m");
		schema.setParityBlocks(Integer.parseInt(paritySize));

		encoder = new JavaRSEncoder(schema.getDataBlocks(), schema.getParityBlocks(), schema.getChunkSize());
		decoder = new JavaRSDecoder(schema.getDataBlocks(), schema.getParityBlocks(), schema.getChunkSize());
	}

}

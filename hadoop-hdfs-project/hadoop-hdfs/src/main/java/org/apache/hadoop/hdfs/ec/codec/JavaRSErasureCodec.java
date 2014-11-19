package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;

/**
 * Reed-Solomon codec with Java code
 */
public class JavaRSErasureCodec extends RSErasureCodec{

	@Override
	public ErasureCoder createErasureCoder() {
		return new JavaRSErasureCodec();
	}

}

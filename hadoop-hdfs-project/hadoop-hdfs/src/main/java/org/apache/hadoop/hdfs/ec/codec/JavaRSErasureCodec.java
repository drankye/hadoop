package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ec.coder.AbstractErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.JavaRSErasureCoder;

/**
 * Reed-Solomon codec with Java code
 */
public class JavaRSErasureCodec extends RSErasureCodec{

	@Override
	public ErasureCoder createErasureCoder() {
		AbstractErasureCoder erasureCoder = new JavaRSErasureCoder();
		erasureCoder.initWith(getSchema());
		return erasureCoder;
	}

} 

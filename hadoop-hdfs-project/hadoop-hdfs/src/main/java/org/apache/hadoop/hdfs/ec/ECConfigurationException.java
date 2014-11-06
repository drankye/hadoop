package org.apache.hadoop.hdfs.ec;


/**
 * Thrown when the EC configuration file is malformed.
 */
public class ECConfigurationException extends Exception {
	private static final long serialVersionUID = 4046517047810854249L;

	public ECConfigurationException(String message) {
		super(message);
	}

	public ECConfigurationException(String message, Throwable t) {
		super(message, t);
	}
}

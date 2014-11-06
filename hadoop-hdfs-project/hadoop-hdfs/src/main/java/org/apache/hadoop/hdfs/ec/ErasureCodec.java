package org.apache.hadoop.hdfs.ec;

import java.util.Map;

public class ErasureCodec {
	private String codecName;
	private Map<String, String> properties;
	private String erasureCoder;
	
	public ErasureCodec(String codecName, Map<String, String> properties, String erasureCoder) {
		this.codecName = codecName;
		this.properties = properties;
		this.erasureCoder = erasureCoder;
	}

	public String getCodecName() {
		return codecName;
	}

	public void setCodecName(String codecName) {
		this.codecName = codecName;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

	public String getErasureCoder() {
		return erasureCoder;
	}

	public void setErasureCoder(String erasureCoder) {
		this.erasureCoder = erasureCoder;
	}
	
	
	
}

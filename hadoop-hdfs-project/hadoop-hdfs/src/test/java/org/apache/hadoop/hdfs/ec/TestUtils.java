package org.apache.hadoop.hdfs.ec;

import org.apache.hadoop.conf.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestUtils {

	public static ECSchema makeRSSchema(String codecName, int dataSize, int paritySize, Configuration conf, String schemaFile) throws Exception{
		PrintWriter out = new PrintWriter(new FileWriter(schemaFile));
		out.println("<?xml version=\"1.0\"?>");
		out.println("<schemas>");
		out.println("  <schema name=\"RSSchema\">");
		out.println("    <k>" + dataSize + "</k>");
		out.println("    <m>" + paritySize + "</m>");
		out.println("    <codec>" + codecName + "</codec>");
		out.println("  </schema>");
		out.println("</schemas>");
		out.close();

		SchemaLoader schemaLoader = new SchemaLoader();
		List<ECSchema> schemas = schemaLoader.loadSchema(conf);
		assertEquals(1, schemas.size());
		
		return schemas.get(0);
	}
}
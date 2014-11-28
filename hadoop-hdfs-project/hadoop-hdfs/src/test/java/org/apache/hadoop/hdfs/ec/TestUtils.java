package org.apache.hadoop.hdfs.ec;

import org.apache.hadoop.conf.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestUtils {

	public final static String TEST_DIR = new File(System.getProperty(
			"test.build.data", "/tmp")).getAbsolutePath();

	public final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs")
			.getAbsolutePath();

	public static ECSchema makeRSSchema(int dataSize, int paritySize,
                                      String codecName, String codecClass) throws Exception{
		PrintWriter out = new PrintWriter(new FileWriter(SCHEMA_FILE));
		out.println("<?xml version=\"1.0\"?>");
		out.println("<schemas>");
		out.println("  <schema name=\"RSSchema\">");
		out.println("    <k>" + dataSize + "</k>");
		out.println("    <m>" + paritySize + "</m>");
		out.println("    <codec>" + codecName + "</codec>");
		out.println("  </schema>");
		out.println("</schemas>");
		out.close();

		Configuration conf = new Configuration();
		conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
		conf.set("hadoop.hdfs.ec.erasurecodec.codec." + codecName, codecClass);

		SchemaLoader schemaLoader = new SchemaLoader();
		List<ECSchema> schemas = schemaLoader.loadSchema(conf);
		assertEquals(1, schemas.size());
		
		return schemas.get(0);
	}
}
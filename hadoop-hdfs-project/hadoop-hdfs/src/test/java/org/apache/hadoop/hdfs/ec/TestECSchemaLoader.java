package org.apache.hadoop.hdfs.ec;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestECSchemaLoader {

	final static String TEST_DIR = new File(System.getProperty(
			"test.build.data", "/tmp")).getAbsolutePath();

	final static String SCHEMA_FILE = new File(TEST_DIR, "test-ecs")
			.getAbsolutePath();

	@Test
	public void testLoadSchema() throws Exception {
		PrintWriter out = new PrintWriter(new FileWriter(SCHEMA_FILE));
		out.println("<?xml version=\"1.0\"?>");
		out.println("<schemas>");
		out.println("  <schema name=\"RSk6m3\">");
		out.println("    <k>6</k>");
		out.println("    <m>3</m>");
		out.println("    <codec>RS-Jerasure</codec>");
		out.println("  </schema>");
		out.println("  <schema name=\"LRCk6l2r2\">");
		out.println("    <k>6</k>");
		out.println("    <l>3</l>");
		out.println("    <r>2</r>");
		out.println("    <codec>LRC-ISA</codec>");
		out.println("  </schema>");
		out.println("</schemas>");
		out.close();

		Configuration conf = new Configuration();
		conf.set(ECConfiguration.CONFIGURATION_FILE, SCHEMA_FILE);
		conf.set("hadoop.hdfs.ec.codec.codec.RS-Jerasure",
				"hadoop.hdfs.ec.codec.codec.JerasureRS");
		conf.set("hadoop.hdfs.ec.codec.codec.RS-ISA",
				"hadoop.hdfs.ec.codec.codec.IsaRS");
		conf.set("hadoop.hdfs.ec.codec.codec.LRC",
				"hadoop.hdfs.ec.codec.codec.IsaLRC");

    ECSchemaLoader schemaLoader = new ECSchemaLoader();
		List<ECSchema> codecs = schemaLoader.loadSchema(conf);

		System.out.println("chenlin:" + codecs.size());
		for (int i = 0; i < codecs.size(); i++) {
			System.out.println(codecs.get(i).getCodecName());
		}
		assertEquals(1, codecs.size());
		assertEquals("RSk6m3", codecs.get(0).getCodecName());
		assertEquals(3, codecs.get(0).getOptions().size());
		assertEquals("6", codecs.get(0).getOptions().get("k"));
		assertEquals("3", codecs.get(0).getOptions().get("m"));
		assertEquals("RS-Jerasure",
				codecs.get(0).getOptions().get("codec"));
		assertEquals("RS-Jerasure", codecs.get(0).getCodecName());

	}
}
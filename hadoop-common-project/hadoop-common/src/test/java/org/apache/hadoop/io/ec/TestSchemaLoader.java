package org.apache.hadoop.io.ec;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestSchemaLoader {

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
		conf.set("hadoop.io.ec.erasurecodec.codec.RS-Jerasure",
				"hadoop.io.ec.codec.codec.JerasureRS");
		conf.set("hadoop.io.ec.erasurecodec.codec.RS-ISA",
				"hadoop.io.ec.codec.codec.IsaRS");
		conf.set("hadoop.io.ec.erasurecodec.codec.LRC",
				"hadoop.io.ec.codec.codec.IsaLRC");

		SchemaLoader schemaLoader = new SchemaLoader();
		List<ECSchema> schemas = schemaLoader.loadSchema(conf);

		assertEquals(1, schemas.size());
		assertEquals("RSk6m3", schemas.get(0).getSchemaName());
		assertEquals(3, schemas.get(0).getOptions().size());
		assertEquals("6", schemas.get(0).getOptions().get("k"));
		assertEquals("3", schemas.get(0).getOptions().get("m"));
		assertEquals("RS-Jerasure",
				schemas.get(0).getOptions().get("codec"));
		assertEquals("RS-Jerasure", schemas.get(0).getCodecName());
	}
}
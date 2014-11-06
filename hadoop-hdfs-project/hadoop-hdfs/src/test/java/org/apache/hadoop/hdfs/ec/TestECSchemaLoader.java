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

	final static String ALLOC_FILE = new File(TEST_DIR, "test-ecs")
			.getAbsolutePath();

	@Test
	public void testLoadSchema() throws Exception {
		PrintWriter out = new PrintWriter(new FileWriter(ALLOC_FILE));
		out.println("<?xml version=\"1.0\"?>");
		out.println("<ecschemas>");
		out.println("  <ecschema name=\"RSk6m3\">");
		out.println("    <k>6</k>");
		out.println("    <m>3</m>");
		out.println("    <erasurecodec>RS-Jerasure</erasurecodec>");
		out.println("  </ecschema>");
		out.println("  <ecschema name=\"LRCk6l2r2\">");
		out.println("    <k>6</k>");
		out.println("    <l>3</l>");
		out.println("    <r>2</r>");
		out.println("    <erasurecodec>LRC-ISA</erasurecodec>");
		out.println("  </ecschema>");
		out.println("</ecschemas>");
		out.close();

		Configuration conf = new Configuration();
		conf.set(ECConfiguration.CONFIGURATION_FILE, ALLOC_FILE);
		conf.set("hadoop.hdfs.ec.erasurecodec.codec.RS-Jerasure",
				"hadoop.hdfs.ec.erasurecodec.codec.JerasureRS");
		conf.set("hadoop.hdfs.ec.erasurecodec.codec.RS-ISA",
				"hadoop.hdfs.ec.erasurecodec.codec.IsaRS");
		conf.set("hadoop.hdfs.ec.erasurecodec.codec.LRC",
				"hadoop.hdfs.ec.erasurecodec.codec.IsaLRC");

		ECSchemaLoader ecSchemaLoader = new ECSchemaLoader();
		List<ErasureCodec> codecs = ecSchemaLoader.loadSchema(conf);

		System.out.println("chenlin:" + codecs.size());
		for (int i = 0; i < codecs.size(); i++) {
			System.out.println(codecs.get(i).getCodecName());
		}
		assertEquals(1, codecs.size());
		assertEquals("RSk6m3", codecs.get(0).getCodecName());
		assertEquals(3, codecs.get(0).getProperties().size());
		assertEquals("6", codecs.get(0).getProperties().get("k"));
		assertEquals("3", codecs.get(0).getProperties().get("m"));
		assertEquals("RS-Jerasure",
				codecs.get(0).getProperties().get("erasurecodec"));
		assertEquals("RS-Jerasure", codecs.get(0).getErasureCoder());

	}
}
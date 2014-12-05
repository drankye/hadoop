package org.apache.hadoop.hdfs.ec;

import java.io.FileWriter;
import java.io.PrintWriter;

public abstract class TestRSErasureCodec extends TestErasureCodecBase {

  protected void prepareSchema(String codecName, String schemaName, int dataSize, int paritySize) throws Exception{
    PrintWriter out = new PrintWriter(new FileWriter(SCHEMA_FILE));
    out.println("<?xml version=\"1.0\"?>");
    out.println("<schemas>");
    out.println("  <schema name=\"" + schemaName + "\">");
    out.println("    <k>" + dataSize + "</k>");
    out.println("    <m>" + paritySize + "</m>");
    out.println("    <codec>" + codecName + "</codec>");
    out.println("  </schema>");
    out.println("</schemas>");
    out.close();
  }
}

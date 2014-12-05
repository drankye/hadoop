package org.apache.hadoop.hdfs.ec;

import org.junit.Test;

import java.io.FileWriter;
import java.io.PrintWriter;

public class TestJavaRSErasureCodec extends TestRSErasureCodec {

  @Test
  public void testCodec() throws Exception {
    int dataSize = 10;
    int paritySize = 4;
    prepareSchema(codecName, schemaName, dataSize, paritySize);

    doTest();
  }

  @Override
  protected String getCodecClass() {
    return "org.apache.hadoop.hdfs.ec.codec.JavaRSErasureCodec";
  }
}

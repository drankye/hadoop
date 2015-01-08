package org.apache.hadoop.io.ec;

import org.junit.Test;

public class TestIsaRSErasureCodec extends TestRSErasureCodec {

  @Test
  public void testCodec() throws Exception {
    int dataSize = 10;
    int paritySize = 4;
    prepareSchema(codecName, schemaName, dataSize, paritySize);

    doTest();
  }

  @Override
  protected String getCodecClass() {
    return "org.apache.hadoop.io.ec.codec.IsaRSErasureCodec";
  }
}

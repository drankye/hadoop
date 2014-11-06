package org.apache.hadoop.hdfs.ec;

import java.util.Map;

public class ECSchema {

  private String schemaName;
  private String codecName;
  private Map<String, String> options;
  private int dataBlocks;
  private int parityBlocks;

  public ECSchema(String codecName, Map<String, String> options, String codec) {
    this.codecName = codecName;
    this.options = options;
    this.codecName = codec;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public String getCodecName() {
    return codecName;
  }

  /**
   * Erasure coding options to be configured and used by ErasureCoder.
   * @return
   */
  public Map<String, String> getOptions() {
    return options;
  }

  /**
   * Get required data blocks count in a BlockGroup,
   * to be called by ECManager when calculating BlockGroup.
   * @return
   */
  public int getDataBlocks() {
    return dataBlocks;
  }

  /**
   * Get required parity blocks count in a BlockGroup,
   * to be called by ECManager when calculating BlockGroup.
   * @return
   */
  public int getParityBlocks() {
    return parityBlocks;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public void setCodecName(String codecName) {
    this.codecName = codecName;
  }

  public void setOptions(Map<String, String> options) {
    this.options = options;
  }

  public void setDataBlocks(int dataBlocks) {
    this.dataBlocks = dataBlocks;
  }

  public void setParityBlocks(int parityBlocks) {
    this.parityBlocks = parityBlocks;
  }
}

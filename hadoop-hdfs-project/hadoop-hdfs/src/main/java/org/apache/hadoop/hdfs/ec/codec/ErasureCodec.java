package org.apache.hadoop.hdfs.ec.codec;

import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.ec.BlockGroup;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;

import java.util.List;

/**
 * Erasure Codec to be managed by ECManager and used by ECWorker
 */
public abstract class ErasureCodec {

  private ECSchema schema;

  public void initWith(ECSchema schema) {
    this.schema = schema;
  }

  public String getCodecName() {
    return schema.getCodecName();
  }

  /**
   * Calculating and organizing BlockGroup, to be called by ECManager
   * @param dataBlocks Data blocks to compute parity blocks against
   * @param parityBlocks To be computed parity blocks
   * @return
   */
  public abstract BlockGroup makeBlockGroup(List<ExtendedBlockId> dataBlocks,
                                            List<ExtendedBlockId> parityBlocks);

  /**
   * Create Erasure Coder, to be called by ECWorker
   * @return
   */
  public abstract ErasureCoder createErasureCoder();


  public static ErasureCodec createErasureCodec(ECSchema schema) throws Exception {
    String codecName = schema.getCodecName();
    String codecClassName = null; // TODO: convert from codecName
    Class codecClass = Class.forName(codecClassName);
    ErasureCodec codec = (ErasureCodec) codecClass.newInstance();
    codec.initWith(schema);

    return codec;
  }
}

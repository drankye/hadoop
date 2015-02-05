package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;

public class ErasureDecodingStep extends AbstractErasureCodingStep {
  private int[] erasedIndexes;
  private RawErasureDecoder rawDecoder;

  /**
   * The constructor with all the necessary info.
   * @param inputBlocks
   * @param erasedIndexes the indexes of erased blocks in inputBlocks array
   * @param outputBlocks
   * @param rawDecoder
   */
  public ErasureDecodingStep(ECBlock[] inputBlocks, int[] erasedIndexes,
                             ECBlock[] outputBlocks,
                             RawErasureDecoder rawDecoder) {
    super(inputBlocks, outputBlocks);
    this.erasedIndexes = erasedIndexes;
    this.rawDecoder = rawDecoder;
  }

  @Override
  public void performCoding(ECChunk[] inputChunks, ECChunk[] outputChunks) {
    rawDecoder.decode(inputChunks, erasedIndexes, outputChunks);
  }

}

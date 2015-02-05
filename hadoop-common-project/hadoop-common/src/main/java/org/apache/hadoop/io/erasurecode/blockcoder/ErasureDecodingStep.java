package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECChunk;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;

public class ErasureDecodingStep extends AbstractCodingStep {
  private int[] erasedIndexes;
  private RawErasureDecoder rawDecoder;

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

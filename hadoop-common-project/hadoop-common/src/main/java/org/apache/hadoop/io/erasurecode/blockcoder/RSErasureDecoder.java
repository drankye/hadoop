package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECBlockGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.JavaRSRawDecoder;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;

/**
 * ReedSolomon erasure decoder that decodes a block group.
 *
 * It implements {@link ErasureDecoder}.
 */
public class RSErasureDecoder extends AbstractErasureDecoder {

  @Override
  protected ErasureCodingStep performDecoding(final ECBlockGroup blockGroup) {
    // TODO: should be configured, either JavaRSRawDecoder or IsaRSRawDecoder
    RawErasureDecoder rawDecoder = new JavaRSRawDecoder();
    rawDecoder.initialize(getNumDataUnits(),
        getNumParityUnits(), getChunkSize());

    ECBlock[] inputBlocks = getInputBlocks(blockGroup);

    return new ErasureDecodingStep(inputBlocks,
        getErasedIndexes(inputBlocks),
        getOutputBlocks(blockGroup), rawDecoder);
  }

}

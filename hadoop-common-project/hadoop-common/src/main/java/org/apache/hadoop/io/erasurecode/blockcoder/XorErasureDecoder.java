package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;
import org.apache.hadoop.io.erasurecode.rawcoder.XorRawDecoder;

/**
 *
 */
public class XorErasureDecoder extends AbstractErasureDecoder {

  @Override
  protected CodingStep performDecoding(final ECGroup group) {
    // May be configured
    RawErasureDecoder rawDecoder = new XorRawDecoder();
    rawDecoder.initialize(getNumDataUnits(),
        getNumParityUnits(), getChunkSize());

    ECBlock[] inputBlocks = getInputBlocks(group);

    return new ErasureDecodingStep(inputBlocks,
        getErasedIndexes(inputBlocks),
        getOutputBlocks(group), rawDecoder);
  }

}

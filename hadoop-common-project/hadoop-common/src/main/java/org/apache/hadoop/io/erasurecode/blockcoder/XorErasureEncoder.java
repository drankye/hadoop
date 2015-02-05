package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;
import org.apache.hadoop.io.erasurecode.rawcoder.XorRawEncoder;

/**
 *
 */
public class XorErasureEncoder extends AbstractErasureEncoder {

  @Override
  protected CodingStep performEncoding(final ECGroup blockGroup) {
    // May be configured
    RawErasureEncoder rawEncoder = new XorRawEncoder();
    rawEncoder.initialize(getNumDataUnits(),
        getNumParityUnits(), getChunkSize());

    ECBlock[] inputBlocks = getInputBlocks(blockGroup);

    return new ErasureEncodingStep(inputBlocks,
        getOutputBlocks(blockGroup), rawEncoder);
  }

}

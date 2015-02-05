package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECBlockGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;
import org.apache.hadoop.io.erasurecode.rawcoder.XorRawEncoder;

/**
 * Xor erasure encoder that encodes a block group.
 *
 * It implements {@link ErasureEncoder}.
 */
public class XorErasureEncoder extends AbstractErasureEncoder {

  @Override
  protected ErasureCodingStep performEncoding(final ECBlockGroup blockGroup) {
    // May be configured
    RawErasureEncoder rawEncoder = new XorRawEncoder();
    rawEncoder.initialize(getNumDataUnits(),
        getNumParityUnits(), getChunkSize());

    ECBlock[] inputBlocks = getInputBlocks(blockGroup);

    return new ErasureEncodingStep(inputBlocks,
        getOutputBlocks(blockGroup), rawEncoder);
  }

}
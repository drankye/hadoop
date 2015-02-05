package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECBlockGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.JavaRSRawEncoder;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

/**
 * ReedSolomon erasure encoder that encodes a block group.
 *
 * It implements {@link ErasureEncoder}.
 */
public class RSErasureEncoder extends AbstractErasureEncoder {

  @Override
  protected ErasureCodingStep performEncoding(final ECBlockGroup blockGroup) {
    // TODO: should be configured, either JavaRSRawEncoder or IsaRSRawEncoder
    RawErasureEncoder rawEncoder = new JavaRSRawEncoder();
    rawEncoder.initialize(getNumDataUnits(),
        getNumParityUnits(), getChunkSize());

    ECBlock[] inputBlocks = getInputBlocks(blockGroup);

    return new ErasureEncodingStep(inputBlocks,
        getOutputBlocks(blockGroup), rawEncoder);
  }

}

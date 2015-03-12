package org.apache.hadoop.io.erasurecode.coder;

import org.apache.hadoop.io.erasurecode.ECBlock;
import org.apache.hadoop.io.erasurecode.ECBlockGroup;
import org.apache.hadoop.io.erasurecode.rawcoder.JRSRawEncoder;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

/**
 * Reed-Solomon erasure encoder that encodes a block group.
 *
 * It implements {@link ErasureEncoder}.
 */
public class RSErasureEncoder extends AbstractErasureEncoder {
  private RawErasureEncoder rawEncoder;

  @Override
  protected ErasureCodingStep doEncode(final ECBlockGroup blockGroup) {

    RawErasureEncoder rawEncoder = checkCreateRSRawEncoder();

    ECBlock[] inputBlocks = getInputBlocks(blockGroup);

    return new ErasureEncodingStep(inputBlocks,
        getOutputBlocks(blockGroup), rawEncoder);
  }

  private RawErasureEncoder checkCreateRSRawEncoder() {
    // TODO: should be configured, either JRSRawEncoder or IsaRSRawDecoder
    // Will think about configuration related in ErasureCodec layer.
    // Ref. HADOOP-11649
    if (rawEncoder == null) {
      rawEncoder = new JRSRawEncoder();
      rawEncoder.initialize(getNumDataUnits(), getNumParityUnits(), getChunkSize());
    }
    return rawEncoder;
  }

  @Override
  public void release() {
    if (rawEncoder != null) {
      rawEncoder.release();
    }
  }
}

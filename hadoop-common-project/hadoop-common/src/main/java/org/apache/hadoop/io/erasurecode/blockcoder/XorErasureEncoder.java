package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

/**
 *
 */
public class XorErasureEncoder extends AbstractErasureEncoder {

  /**
   * Constructor providing with a rawEncoder. The raw encoder can be
   * determined by
   * configuration or by default for a codec.
   *
   * @param rawEncoder
   */
  public XorErasureEncoder(RawErasureEncoder rawEncoder) {
    super(rawEncoder);
  }

  @Override
  protected CodingStep perform() {
    return null;
  }

}

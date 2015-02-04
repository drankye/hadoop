package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;

/**
 *
 */
public class XorErasureDecoder extends AbstractErasureDecoder {

  /**
   * Constructor providing with a rawDecoder. The raw decoder can be
   * determined by configuration or by default for a codec.
   *
   * @param rawDecoder
   */
  public XorErasureDecoder(RawErasureDecoder rawDecoder) {
    super(rawDecoder);
  }

  @Override
  protected CodingStep perform() {
    return null;
  }

}

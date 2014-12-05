package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.rawcoder.RawDecoder;

public abstract class AbstractErasureDecoder implements ErasureDecoder {

  private RawDecoder rawDecoder;
  private ErasureCoderCallback callback;

  public AbstractErasureDecoder(RawDecoder rawDecoder) {
    this.rawDecoder = rawDecoder;
  }

  protected RawDecoder getRawDecoder() {
    return rawDecoder;
  }

  protected ErasureCoderCallback getCallback() {
    return callback;
  }

  public void setCallback(ErasureCoderCallback callback) {
    this.callback = callback;
  }
}

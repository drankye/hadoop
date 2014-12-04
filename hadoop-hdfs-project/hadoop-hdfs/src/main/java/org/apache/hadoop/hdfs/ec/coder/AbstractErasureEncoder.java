package org.apache.hadoop.hdfs.ec.coder;

import org.apache.hadoop.hdfs.ec.rawcoder.RawEncoder;

public abstract class AbstractErasureEncoder implements ErasureEncoder {

  private RawEncoder rawEncoder;
  private ErasureCoderCallback callback;

  public AbstractErasureEncoder(RawEncoder rawEncoder) {
    this.rawEncoder = rawEncoder;
  }

  protected RawEncoder getRawEncoder() {
    return rawEncoder;
  }

  protected ErasureCoderCallback getCallback() {
    return callback;
  }

  public void setCallback(ErasureCoderCallback callback) {
    this.callback = callback;
  }
}

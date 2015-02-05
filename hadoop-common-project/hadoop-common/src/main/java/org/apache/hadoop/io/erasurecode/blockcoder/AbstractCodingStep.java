package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.ECBlock;

public abstract class AbstractCodingStep implements CodingStep {

  private ECBlock[] inputBlocks;
  private ECBlock[] outputBlocks;

  public AbstractCodingStep(ECBlock[] inputBlocks, ECBlock[] outputBlocks) {
    this.inputBlocks = inputBlocks;
    this.outputBlocks = outputBlocks;
  }

  @Override
  public ECBlock[] getInputBlocks() {
    return inputBlocks;
  }

  @Override
  public ECBlock[] getOutputBlocks() {
    return outputBlocks;
  }

  @Override
  public void finish() {
    // NOOP by default
  }

}

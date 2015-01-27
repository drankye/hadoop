/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.io.ec;

/**
 * A wrapper of block level data source/output that {@link ECChunk}s can be
 * extracted from. For HDFS, it can be an HDFS block (250MB).
 */
public class ECBlock {

  private boolean isParity;
  private boolean isMissing;

  /**
   * A default constructor. isParity and isMissing are false by default.
   */
  public ECBlock() {
    this(false, false);
  }

  /**
   * A constructor specifying isParity and isMissing.
   * @param isParity
   * @param isMissing
   */
  public ECBlock(boolean isParity, boolean isMissing) {
    this.isParity = isParity;
    this.isMissing = isMissing;
  }

  /**
   * Set true if it's for a parity block.
   * @param isParity
   */
  public void setParity(boolean isParity) {
    this.isParity = isParity;
  }

  /**
   * Set true if the block is missing.
   * @param isMissing
   */
  public void setMissing(boolean isMissing) {
    this.isMissing = isMissing;
  }

  /**
   *
   * @return true if it's parity block, otherwise false
   */
  public boolean isParity() {
    return isParity;
  }

  /**
   *
   * @return true if it's missing, otherwise false
   */
  public boolean isMissing() {
    return isMissing;
  }

}

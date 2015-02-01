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
package org.apache.hadoop.io.erasurecode.blockcoder;

import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

/**
 * An abstract erasure encoder that's to be inherited by new encoders.
 *
 * It implements the {@link ErasureEncoder} interface.
 */
public abstract class AbstractErasureEncoder extends AbstractErasureCoder
    implements ErasureEncoder {

  /**
   * Constructor providing with a rawEncoder. The raw encoder can be determined by
   * configuration or by default for a codec.
   *
   * @param rawEncoder
   */
  public AbstractErasureEncoder(RawErasureEncoder rawEncoder) {
    super(rawEncoder);
  }

  /**
   * Get the underlying raw encoder.
   * @return the underlying raw encoder
   */
  protected RawErasureEncoder getRawEncoder() {
    return (RawErasureEncoder) getRawCoder();
  }

}

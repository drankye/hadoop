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
package org.apache.hadoop.io.erasurecode.rawcoder;

import org.junit.Before;
import org.junit.Test;

/**
 * Test JavaRS encoding and decoding with 10x4.
 */
public class TestJavaRSRawCoder extends TestRawCoderBase {

  @Before
  public void setup() {
    this.encoderClass = JavaRSRawEncoder.class;
    this.decoderClass = JavaRSRawDecoder.class;
  }

  private void prepare(int numDataUnits, int numParityUnits) {
    this.numDataUnits = numDataUnits;
    this.numParityUnits = numParityUnits;
    this.erasedIndexes = new int[] {2};
  }
  
  @Test
  public void testCodingNoDirectBuffer_10x4() {
    prepare(10, 4);
    testCoding(false);
  }

  @Test
  public void testCodingDirectBuffer_10x4() {
    prepare(10, 4);
    testCoding(true);
  }

  @Test
  public void testCodingNoDirectBuffer_3x3() {
    prepare(3, 3);
    testCoding(false);
  }

  @Test
  public void testCodingDirectBuffer_3x3() {
    prepare(3, 3);
    testCoding(true);
  }

}

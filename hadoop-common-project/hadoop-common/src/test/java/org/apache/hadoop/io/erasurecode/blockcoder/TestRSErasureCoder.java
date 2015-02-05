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

import org.junit.Before;
import org.junit.Test;

/**
 * Test ReedSolomon encoding and decoding.
 */
public class TestRSErasureCoder extends TestErasureCoderBase {

  @Before
  public void setup() {
    this.encoderClass = RSErasureEncoder.class;
    this.decoderClass = RSErasureDecoder.class;

    this.numDataUnits = 10;
    this.numParityUnits = 1;

    this.numChunksInBlock = 10;
  }

  private void prepare(int numDataUnits, int numParityUnits,
                       int[] erasedIndexes) {
    this.numDataUnits = numDataUnits;
    this.numParityUnits = numParityUnits;
    this.erasedDataIndexes = erasedIndexes != null ?
        erasedIndexes : new int[] {0};
  }

  @Test
  public void testCodingNoDirectBuffer_10x4() {
    prepare(10, 4, null);
    testCoding(false);
  }

  @Test
  public void testCodingDirectBuffer_10x4() {
    prepare(10, 4, null);
    testCoding(true);
  }

  @Test
  public void testCodingDirectBuffer_10x4_erasure_of_2_4() {
    prepare(10, 4, new int[] {2, 4});
    testCoding(true);
  }

  @Test
  public void testCodingDirectBuffer_10x4_erasing_all() {
    prepare(10, 4, new int[] {0, 1, 2, 3});
    testCoding(true);
  }

  @Test
  public void testCodingNoDirectBuffer_3x3() {
    prepare(3, 3, null);
    testCoding(false);
  }

  @Test
  public void testCodingDirectBuffer_3x3() {
    prepare(3, 3, null);
    testCoding(true);
  }

}
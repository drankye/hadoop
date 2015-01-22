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
package org.apache.hadoop.io.ec.rawcoder;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test XOR encoding and decoding, adapted from HDFS-RAID.
 */
public class TestXorRawEncoder {

  @Test
  public void testXorCoding() {
    java.util.Random RAND = new java.util.Random();
    int dataSize = 10;
    int chunkSize = 16 * 1024;

    /**
     * Generate data and encode
     */
    byte[][] encodingData = new byte[dataSize][];
    for (int i = 0; i < dataSize; i++) {
      encodingData[i] = new byte[chunkSize];
      for (int j = 0; j < chunkSize; j++) {
        encodingData[i][j] = (byte)RAND.nextInt(256);
      }
    }

    byte[][] parityData = new byte[][] {new byte[chunkSize]};

    RawErasureEncoder encoder = new XorRawEncoder(dataSize, chunkSize);
    encoder.encode(encodingData, parityData);

    // Make a copy of a strip for later comparing then erase it
    byte[] erasedData = new byte[chunkSize];
    for (int j = 0; j < chunkSize; j++) {
      erasedData[j] = encodingData[0][j];
      encodingData[0][j] = 0;
    }

    byte[][] decodingData = new byte[dataSize + 1][];
    for (int i = 0; i < dataSize; i++) {
      decodingData[i] = encodingData[i];
    }
    decodingData[dataSize] = parityData[0];

    /**
     * Decode and compare
     */
    byte[][] recoveredData = new byte[][] {new byte[chunkSize]};
    RawErasureDecoder decoder = new XorRawDecoder(dataSize, chunkSize);
    decoder.decode(decodingData, new int[] {0}, recoveredData);
    assertArrayEquals("Decoding and comparing failed", erasedData, recoveredData[0]);
  }
}

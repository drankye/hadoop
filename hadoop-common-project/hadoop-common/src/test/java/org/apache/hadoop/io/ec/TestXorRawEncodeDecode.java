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

import org.apache.hadoop.io.ec.rawcoder.RawErasureDecoder;
import org.apache.hadoop.io.ec.rawcoder.RawErasureEncoder;
import org.apache.hadoop.io.ec.rawcoder.XorRawDecoder;
import org.apache.hadoop.io.ec.rawcoder.XorRawEncoder;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertTrue;

/**
 * Ported from HDFS-RAID
 */
public class TestXorRawEncodeDecode {

  @Test
  public void testPerformance() {
    java.util.Random RAND = new java.util.Random();
    int dataSize = 10;
    final int CHUNK_SIZE = 16 * 1024;

    byte[][] message = new byte[dataSize][];
    ByteBuffer[] messageBuffForEncode = new ByteBuffer[dataSize];
    int bufsize = 1024 * 1024 * 10;
    for (int i = 0; i < dataSize; i++) {
      message[i] = new byte[bufsize];
      for (int j = 0; j < bufsize; j++) {
        message[i][j] = (byte)RAND.nextInt(256);
      }
      messageBuffForEncode[i] = ByteBuffer.wrap(message[i]);
    }

    ByteBuffer[] parityBuff = {ByteBuffer.allocate(bufsize)};

    RawErasureEncoder xorEncoder = new XorRawEncoder(dataSize, CHUNK_SIZE);
    RawErasureDecoder xorDecoder = new XorRawDecoder(dataSize, CHUNK_SIZE);

    long encodeStart = System.currentTimeMillis();
    xorEncoder.encode(messageBuffForEncode, parityBuff);
    long encodeEnd = System.currentTimeMillis();
    float encodeMSecs = encodeEnd - encodeStart;
    System.out.println("Time to encode xor = " + encodeMSecs +
        " msec (" + message[0].length / (1000 * encodeMSecs) + "MB/s)");

    byte[] copy = new byte[bufsize];
    for (int j = 0; j < bufsize; j++) {
      copy[j] = message[0][j];
      message[0][j] = 0;
    }
    
    //decode
    ByteBuffer[] messageBuffForDecode = new ByteBuffer[dataSize + 1];
    for (int i = 0; i < dataSize; i++) {
		messageBuffForDecode[i] = ByteBuffer.wrap(message[i]);
	}
    messageBuffForDecode[dataSize] = parityBuff[0];

    ByteBuffer[] recoveryBuff = {ByteBuffer.allocate(bufsize)};
    long decodeStart = System.currentTimeMillis();
    xorDecoder.decode(messageBuffForDecode, recoveryBuff, new int[]{0});
    long decodeEnd = System.currentTimeMillis();
    float decodeMSecs = decodeEnd - decodeStart;
    System.out.println("Time to decode xor = " + decodeMSecs +
        " msec (" + message[0].length / (1000 * decodeMSecs) + "MB/s)");

    byte[] recoveryData = new byte[bufsize];
    recoveryBuff[0].get(recoveryData);
    assertTrue("Decode failed", java.util.Arrays.equals(copy, recoveryData));
  }
}

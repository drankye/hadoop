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

import org.apache.hadoop.io.erasurecode.ECChunk;

/**
 * Benchmark test base of useful facilities.
 */
public class BenchmarkTestBase extends TestRawCoderBase {

  /**
   * A perf utility to measure how much time an operation would take.
   * @param perfPoint
   * @return
   */
  protected static Perf start(String perfPoint) {
    return new Perf(perfPoint);
  }

  protected static class Perf {

    private final String point;
    private long begin;
    private long result;

    private Perf(String perfPoint) {
      this.point = perfPoint;
      begin = System.currentTimeMillis();
    }

    public void end() {
      long end = System.currentTimeMillis();
      this.result = end - begin;
      String msg = result > 1000 ? (result / 1000) +
          " seconds" : result + " milliseconds";
      System.out.println(point + " takes: " + msg);
    }

    public long getResult() {
      return result;
    }
  }

  /**
   * Run encoding and decoding separately many times.
   * @param coderName
   * @param coders
   * @param times
   * @return two perfs for encoding and decoding
   */
  protected Perf[] runBenchmark(String coderName,
                                RawErasureCoder[] coders, int times) {

    this.usingDirectBuffer = true;

    RawErasureEncoder encoder = (RawErasureEncoder) coders[0];
    RawErasureDecoder decoder = (RawErasureDecoder) coders[1];

    // Generate data and encode
    ECChunk[] dataChunks = prepareDataChunksForEncoding();
    ECChunk[] parityChunks = null;

    // Make a copy of a strip for later comparing
    ECChunk[] toEraseDataChunks = copyDataChunksToErase(dataChunks);

    Perf encodingPerf, decodingPerf;

    try {
      encodingPerf = start("Run encoding for " + coderName);

      for (int i = 0; i < times; ++i) {
        ECChunk[] clonedDataChunks = cloneChunksWithData(dataChunks);
        parityChunks = prepareParityChunksForEncoding();
        encoder.encode(clonedDataChunks, parityChunks);
      }

      encodingPerf.end();
    } finally {
      encoder.release();
    }
    // Erase the copied sources
    eraseSomeDataBlocks(dataChunks);

    //Decode
    ECChunk[] inputChunks = prepareInputChunksForDecoding(dataChunks, parityChunks);
    ECChunk[] recoveredChunks = null;
    try {
      decodingPerf = start("Run decoding for " + coderName);

      for (int i = 0; i < times; ++i) {
        ECChunk[] clonedDataChunks = cloneChunksWithData(inputChunks);
        recoveredChunks = prepareOutputChunksForDecoding();
        decoder.decode(clonedDataChunks,
            getErasedIndexesForDecoding(), recoveredChunks);
      }

      decodingPerf.end();
    } finally {
      decoder.release();
    }

    //Compare
    compareAndVerify(toEraseDataChunks, recoveredChunks);

    return new Perf[] {encodingPerf, decodingPerf};
  }
}

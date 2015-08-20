/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.io.erasurecode;

import org.apache.hadoop.io.erasurecode.rawcoder.NativeRSRawErasureCoderFactory;
import org.apache.hadoop.io.erasurecode.rawcoder.RSRawErasureCoderFactory;
import org.apache.hadoop.io.erasurecode.rawcoder.RSRawErasureCoderFactory2;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureCoderFactory;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureDecoder;
import org.apache.hadoop.io.erasurecode.rawcoder.RawErasureEncoder;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BenchmarkTool3 {
  private static final int DATA_BUFFER_SIZE = 126; //MB

  private static RawErasureCoderFactory[] coderMakers = new RawErasureCoderFactory[]{new RSRawErasureCoderFactory(), new RSRawErasureCoderFactory2(), new NativeRSRawErasureCoderFactory()};

  private static String[] coderNames = new String[]{"hdfs-raid coder", "new Java coder", "isa-l coder",};

  private static void printAvailableCoders() {
    StringBuilder sb = new StringBuilder("Available coders with coderIndex:\n");
    for (int i = 0; i < coderNames.length; i++) {
      if (i == 2 && !ErasureCodeNative.isNativeCodeLoaded()) {
        continue; // Skip the native one if not loaded successfully
      }
      sb.append(i).append(":").append(coderNames[i]).append("\n");
    }
    System.out.println(sb.toString());
  }

  private static void usage(String message) {
    if (message != null) {
      System.out.println(message);
    }
    System.out.println("BenchmarkTool <encode/decode> <coderIndex> [numClients]" + "[dataSize-in-MB] [chunkSize-in-KB]");
    printAvailableCoders();
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    String type = null;
    int coderIndex = 0;
    int dataSize = 10240; //MB
    int chunkSize = 1024; //KB
    int numClients = 1;

    if (args.length > 1) {
      type = args[0];
      if (!"encode".equals(type) && !"decode".equals(type)) {
        usage("Invalid type, either 'encode' or 'decode'");
      }

      int tmp = Integer.parseInt(args[1]);
      if (tmp >= 0 && tmp <= 2) {
        coderIndex = tmp;
      } else {
        usage("Invalid coder index, should be in the list");
      }
    } else {
      usage(null);
    }

    if (args.length > 2) {
      int tmp = Integer.parseInt(args[2]);
      if (tmp > 0) {
        numClients = tmp;
      } else {
        usage("Invalid number of clients.");
      }
    }

    if (args.length > 3) {
      int tmp = Integer.parseInt(args[3]);
      if (tmp > 0) {
        dataSize = tmp;
      } else {
        usage("Invalid dataSize, should be valid integer");
      }
    }

    if (args.length > 4) {
      int tmp = Integer.parseInt(args[4]);
      if (tmp > 0) {
        chunkSize = tmp;
      } else {
        usage("Invalid chunkSize, should be valid integer");
      }
    }

    performBench(type, coderIndex, numClients, dataSize, chunkSize);
  }

  public static void performBench(String type, int coderIndex, int numClients, int dataSize, int chunkSize) {
    BenchData.configure(dataSize, chunkSize);
    ByteBuffer testData = genTestData(coderIndex);

    List<CoderBenchCallable> callables = new ArrayList<>(numClients);
    for (int i = 0; i < numClients; i++) {
      callables.add(new CoderBenchCallable(type, coderIndex, testData.duplicate()));
    }
    ExecutorService executor = Executors.newFixedThreadPool(numClients);
    List<Future<Long>> futures = new ArrayList<>(numClients);
    long start = System.currentTimeMillis();
    for (CoderBenchCallable callable : callables) {
      futures.add(executor.submit(callable));
    }
    try {
      for (Future<Long> future : futures) {
        future.get();
      }
      long duration = System.currentTimeMillis() - start;
      long totalDataSize = dataSize * numClients;
      DecimalFormat df = new DecimalFormat("#.##");
      System.out.println(coderNames[coderIndex] + " " + type + " " + totalDataSize + "MB data, with chunk size " + chunkSize / 1024 + "MB");
      System.out.println("Total time: " + df.format(duration / 1000.0) + " s.");
      System.out.println("Total throughput: " + df.format(totalDataSize * 1.0 / duration * 1000.0) + " MB/s");
    } catch (Exception e) {
      System.out.println("Error waiting for client to finish." + e.getMessage());
    } finally {
      executor.shutdown();
    }
  }

  private static boolean needDirectBuffer(int coderIndex) {
    return coderIndex == 2;
  }

  private static ByteBuffer genTestData(int coderIndex) {
    Random random = new Random();
    int bufferSize = DATA_BUFFER_SIZE * 1024 * 1024;
    byte tmp[] = new byte[bufferSize];
    random.nextBytes(tmp);
    ByteBuffer data = needDirectBuffer(coderIndex) ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
    data.put(tmp);
    data.flip();
    return data;
  }

  static class BenchData {
    static final int numDataUnits = 6;
    static final int numParityUnits = 3;
    static int chunkSize;
    static long dataSize; //MB

    final int numAllUnits = numDataUnits + numParityUnits;
    final int[] erasedIndexes = new int[]{6, 7, 8};
    final ByteBuffer[] inputs = new ByteBuffer[numDataUnits];
    final ByteBuffer[] outputs = new ByteBuffer[numParityUnits];
    final ByteBuffer[] decodeInputs = new ByteBuffer[numAllUnits];

    static void configure(int desiredDataSize, int desiredChunkSize) {
      chunkSize = desiredChunkSize * 1024;
      dataSize = desiredDataSize;
      int times = (int) Math.round((dataSize * 1.0) / DATA_BUFFER_SIZE);
      times = times == 0 ? 1 : times;
      dataSize = times * DATA_BUFFER_SIZE;
    }

    BenchData(boolean useDirectBuffer) {
      for (int i = 0; i < outputs.length; i++) {
        outputs[i] = useDirectBuffer ? ByteBuffer.allocateDirect(chunkSize) : ByteBuffer.allocate(chunkSize);
      }
    }

    public void prepareDecInput() {
      System.arraycopy(inputs, 0, decodeInputs, 0, numDataUnits);
    }

    void encode(RawErasureEncoder encoder) {
      encoder.encode(inputs, outputs);
    }

    void decode(RawErasureDecoder decoder) {
      decoder.decode(decodeInputs, erasedIndexes, outputs);
    }
  }

  static class CoderBenchCallable implements Callable<Long> {
    private final boolean isEncode;
    private RawErasureEncoder encoder;
    private RawErasureDecoder decoder;
    private final BenchData benchData;
    private final ByteBuffer testData;

    public CoderBenchCallable(String opType, int coderIndex, ByteBuffer testData) {
      this.isEncode = opType.equalsIgnoreCase("encode");
      if (isEncode) {
        encoder = coderMakers[coderIndex].createEncoder(BenchData.numDataUnits, BenchData.numParityUnits);
      } else {
        decoder = coderMakers[coderIndex].createDecoder(BenchData.numDataUnits, BenchData.numParityUnits);
      }
      benchData = new BenchData(needDirectBuffer(coderIndex));
      this.testData = testData;
    }

    @Override
    public Long call() throws Exception {
      int times = (int) (BenchData.dataSize / DATA_BUFFER_SIZE);


      long start = System.currentTimeMillis();
      for (int i = 0; i < times; i++) {
        while (testData.remaining() > 0) {
          for (ByteBuffer output : benchData.outputs) {
            output.clear();
          }

          for (int j = 0; j < benchData.inputs.length; j++) {
            benchData.inputs[j] = testData.duplicate();
            benchData.inputs[j].limit(testData.position() + BenchData.chunkSize);
            benchData.inputs[j] = benchData.inputs[j].slice();
            testData.position(tes2tData.position() + BenchData.chunkSize);
          }

          if (!isEncode) {
            benchData.prepareDecInput();
          }

          if (isEncode) {
            benchData.encode(encoder);
          } else {
            benchData.decode(decoder);
          }
        }
        testData.clear();
      }
      return System.currentTimeMillis() - start;
    }
  }
}

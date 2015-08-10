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
package org.apache.hadoop.io.erasurecode;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.io.erasurecode.rawcoder.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

public class BenchmarkTool {
  private static RawErasureCoderFactory[] coderMakers =
      new RawErasureCoderFactory[] {
          new RSRawErasureCoderFactory(),
          new RSRawErasureCoderFactory2(),
          new NativeRSRawErasureCoderFactory()
      };

  private static String[] coderNames = new String[] {
      "Reed-Solomon coder in Java (originated from HDFS-RAID)",
      "Reed-Solomon coder in Java (interoperable with ISA-L)",
      "Reed-Solomon coder in native backed by ISA-L",
  };

  private static void usage(String message) {
    if (message != null) {
      System.out.println(message);
    }
    System.out.println("BenchmarkTool <testDir>");
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    File testDir = null, testDataFile = null;

    if (args.length == 1) {
      testDir = new File(args[0]);
      if (!testDir.exists() || !testDir.isDirectory()) {
        usage("Invalid testDir");
      }
    } else {
      usage(null);
    }

    performBench(testDir);
  }

  static int[] getCoderIndexes() {
    return new int[] {0, 1};
  }

  public static void performBench(File testDir) throws Exception {
    File testDataFile = new File(testDir, "generated-benchtest-data.dat");
    generateTestData(testDataFile);

    for (int coderIndex : getCoderIndexes()) {
      System.out.println("Performing benchmark test for "
          + coderNames[coderIndex]);

      File encodedDataFile = new File(testDir,
          "encoded-benchtest-data-coder" + coderIndex + ".dat");
      File decodedDataFile = new File(testDir,
          "decoded-benchtest-data-coder" + coderIndex + ".dat");

      RawErasureCoderFactory coderMaker = coderMakers[coderIndex];
      CoderBench bench = new CoderBench(coderMaker);
      bench.performEncode(testDataFile, encodedDataFile);
      bench.performDecode(encodedDataFile, decodedDataFile, testDataFile);
    }
  }

  static void generateTestData(File testDataFile) throws IOException {
    FileOutputStream out = new FileOutputStream(testDataFile);
    Random random = new Random();
    long times = 6;
    int buffSize = 1 * 1024; // MB
    byte buf[] = new byte[buffSize];

    try {
      for (int i = 0; i < times; i++) {
        random.nextBytes(buf);
        out.write(buf);
      }
    } finally {
      out.close();
    }
  }

  static class BenchData {
    final static int numDataUnits = 6;
    final static int numParityUnits = 3;
    final static int chunkSize = 1 * 1024;
    final static byte[] EMPTY_CHUNK = new byte[chunkSize];

    final boolean useDirectBuffer;
    final int numAllUnits = numDataUnits + numParityUnits;
    final int[] erasedIndexes = new int[]{0, 5, 8};
    final ByteBuffer[] inputs = new ByteBuffer[numDataUnits];
    final ByteBuffer[] outputs = new ByteBuffer[numParityUnits];
    final ByteBuffer[] decodeInputs = new ByteBuffer[numAllUnits];
    final ByteBuffer[] decodeOutputs = new ByteBuffer[erasedIndexes.length];
    final ByteBuffer[] inputsWithRecovered = new ByteBuffer[numDataUnits];

    BenchData(boolean useDirectBuffer) {
      this.useDirectBuffer = useDirectBuffer;

      for (int i = 0; i < inputs.length; i++) {
        inputs[i] = useDirectBuffer ? ByteBuffer.allocateDirect(chunkSize) :
            ByteBuffer.allocate(chunkSize);
      }

      for (int i = 0; i < outputs.length; i++) {
        outputs[i] = useDirectBuffer ? ByteBuffer.allocateDirect(chunkSize) :
            ByteBuffer.allocate(chunkSize);
      }

      System.arraycopy(inputs, 0, decodeInputs, 0, numDataUnits);
      System.arraycopy(outputs, 0, decodeInputs, numDataUnits, numParityUnits);
      for (int i = 0; i < erasedIndexes.length; i++) {
        decodeInputs[erasedIndexes[i]] = null;
      }

      for (int i = 0; i < decodeOutputs.length; i++) {
        decodeOutputs[i] = useDirectBuffer ?
            ByteBuffer.allocateDirect(chunkSize) :
            ByteBuffer.allocate(chunkSize);
      }

      System.arraycopy(inputs, 0, inputsWithRecovered, 0, numDataUnits);
      for (int i = 0, idx = 0; i < erasedIndexes.length; i++) {
        if (erasedIndexes[i] < numDataUnits) {
          inputsWithRecovered[erasedIndexes[i]] = decodeOutputs[idx++];
        }
      }
    }

    void encode(RawErasureEncoder encoder) {
      encoder.encode(inputs, outputs);
    }

    void decode(RawErasureDecoder decoder) {
      decoder.decode(decodeInputs, erasedIndexes, decodeOutputs);
    }
  }

  static class CoderBench {
    static BenchData heapBufferBenchData;
    static BenchData directBufferBenchData;
    final RawErasureEncoder encoder;
    final RawErasureDecoder decoder;
    BenchData benchData;

    CoderBench(RawErasureCoderFactory coderMaker) throws IOException {
      encoder = coderMaker.createEncoder(benchData.numDataUnits,
          benchData.numParityUnits);
      decoder = coderMaker.createDecoder(benchData.numDataUnits,
          benchData.numParityUnits);
      if (encoder.preferDirectBuffer()) {
        if (directBufferBenchData == null) {
          directBufferBenchData = new BenchData(true);
        }
        benchData = directBufferBenchData;
      } else {
        if (heapBufferBenchData == null) {
          heapBufferBenchData = new BenchData(false);
        }
        benchData = heapBufferBenchData;
      }
    }

    void performEncode(File testDataFile, File resultDataFile) throws Exception {
      FileChannel inputChannel = new FileInputStream((testDataFile)).getChannel();
      FileChannel outputChannel = new FileOutputStream(resultDataFile).getChannel();

      long got;
      while (true) {
        for (ByteBuffer input : benchData.inputs) {
          input.clear();
        }

        got = inputChannel.read(benchData.inputs);
        if (got < 1) {
          break;
        }
        for (ByteBuffer input : benchData.inputs) {
          input.flip();
        }

        for (ByteBuffer output : benchData.outputs) {
          output.clear();
          output.put(benchData.EMPTY_CHUNK);
          output.clear();
        }

        benchData.encode(encoder);

        for (ByteBuffer input : benchData.inputs) {
          input.flip();
        }

        outputChannel.write(benchData.inputs);
        outputChannel.write(benchData.outputs);
      }

      inputChannel.close();
      outputChannel.close();
    }

    void performDecode(File encodedDataFile, File resultDataFile,
                       File originalDataFile) throws IOException {
      FileChannel inputChannel = new FileInputStream((encodedDataFile)).getChannel();
      FileChannel outputChannel = new FileOutputStream(resultDataFile).getChannel();

      long got, written;
      while (true) {
        for (ByteBuffer input : benchData.inputs) {
          input.clear();
        }
        for (ByteBuffer output : benchData.outputs) {
          output.clear();
        }

        got = inputChannel.read(benchData.inputs);
        if (got < 1) {
          break;
        }
        got = inputChannel.read(benchData.outputs);
        if (got < 1) {
          break;
        }

        for (ByteBuffer input : benchData.inputs) {
          input.flip();
        }
        for (ByteBuffer output : benchData.outputs) {
          output.flip();
        }

        for (ByteBuffer output : benchData.decodeOutputs) {
          output.clear();
          output.put(benchData.EMPTY_CHUNK);
          output.clear();
        }

        benchData.decode(decoder);

        for (ByteBuffer input : benchData.decodeInputs) {
          if (input != null) {
            input.flip();
          }
        }

        written = outputChannel.write(benchData.inputsWithRecovered);
        if (written < 1) {
          break;
        }
      }

      inputChannel.close();
      outputChannel.close();

      if (!FileUtils.contentEquals(resultDataFile, originalDataFile)) {
        throw new RuntimeException("Decoding failed, not the same with the original file");
      }
    }

    /*
    private void performCoding(boolean isEncode) {
      long begin = System.currentTimeMillis();
      for (int i = 0; i < testTimes; i++) {
        if (isEncode) {
          benchData.encode(encoder);
        } else {
          benchData.decode(decoder);
        }
      }
      long end = System.currentTimeMillis();

      double usedTime = end - begin;
      long usedData = (testTimes * benchData.numDataUnits *
          benchData.chunkSize) / (1024 * 1024);
      double throughput = (usedData * 1000) / usedTime;

      DecimalFormat df = new DecimalFormat("#.##");
      String text = isEncode ? "Encode " : "Decode ";
      text += usedData + "MB data takes " + usedTime
          + " milliseconds, throughput:" + df.format(throughput) + "MB/s";

      System.out.println(text);
    }
    */
  }
}

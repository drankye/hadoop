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
package org.apache.hadoop.hdfs.ec.coder.old.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.ec.coder.old.Encoder;
import org.apache.hadoop.hdfs.ec.coder.old.impl.help.RaidUtils;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


public class ISAEncoder extends Encoder {
  public static final Log LOG = LogFactory.getLog(
      "org.apache.hadoop.raid.ISAEncoder");

  public ISAEncoder(
      Configuration conf, int stripeSize, int paritySize) {
    super(conf, stripeSize, paritySize);
    isaEnInit(stripeSize, paritySize);
  }

  public static native int isaEnInit(int stripeSize, int paritySize);

  public static native int isaEncode(ByteBuffer[] data, ByteBuffer[] code, int blocksize);

  public static native int isaEnEnd();
  static {
    System.loadLibrary("isajni");
  }

  public void end() {
    isaEnEnd();
  }

  protected void encodeStripe(
      InputStream[] blocks,
      long stripeStartOffset,
      long blockSize,
      OutputStream[] outs,
      Progressable reporter) throws IOException {

    ByteBuffer[] readByteBuf = new ByteBuffer[stripeSize];
    ByteBuffer[] writeByteBuf = new ByteBuffer[paritySize];

    for (int i = 0; i < stripeSize; i++) {
      readByteBuf[i] = ByteBuffer.allocateDirect(bufSize);
    }

    for (int i = 0; i < paritySize; i++) {
      writeByteBuf[i] = ByteBuffer.allocateDirect(bufSize);
    }

    long start, end, readTotal = 0, writeTotal = 0, memTotal = 0, calTotal = 0, allstart, allend, alltime;

    allstart = System.nanoTime();
    for (long encoded = 0; encoded < blockSize; encoded += bufSize) {
      // Read some data from each block = bufSize.
      start = System.nanoTime();
      for (int i = 0; i < blocks.length; i++) {
        RaidUtils.readTillEnd(blocks[i], readBufs[i], true);
      }
      end = System.nanoTime();
      readTotal += end - start;

      start = System.nanoTime();
      for (int i = 0; i < stripeSize; i++) {
        readByteBuf[i].position(0);
        readByteBuf[i].put(readBufs[i]);
      }

      for (int i = 0; i < paritySize; i++) {
        writeByteBuf[i].position(0);
        writeByteBuf[i].put(writeBufs[i]);
      }
      end = System.nanoTime();
      memTotal += end - start;
      // Encode the data read.
      start = System.nanoTime();
      isaEncode(readByteBuf, writeByteBuf, bufSize);
      end = System.nanoTime();
      calTotal += end - start;

      start = System.nanoTime();
      for (int rdbuf = 0; rdbuf < paritySize; rdbuf++) {
        writeByteBuf[rdbuf].position(0);
        writeByteBuf[rdbuf].get(writeBufs[rdbuf], 0, writeByteBuf[rdbuf].remaining());
      }
      end = System.nanoTime();
      memTotal += end - start;


      start = System.nanoTime();
      // Now that we have some data to write, send it to the temp files.
      for (int i = 0; i < paritySize; i++) {
        outs[i].write(writeBufs[i], 0, bufSize);
      }
      end = System.nanoTime();
      writeTotal += end - start;
      if (reporter != null) {
        reporter.progress();
      }
    }
    allend = System.nanoTime();
    alltime = allend - allstart;
    System.out.println("[ISA encode one strip] readTotal:" + readTotal / 1000 + ", writeTotal:" + writeTotal / 1000 + ", calTotal" + calTotal / 1000 + ", memTotal" + memTotal / 1000 + ", all:" + alltime / 1000);
    System.out.printf("read:%3.2f%%, write:%3.2f%%, cal:%3.2f%%, mem:%3.2f%%\n",
        (float) readTotal * 100 / alltime, (float) writeTotal * 100 / alltime,
        (float) calTotal * 100 / alltime, (float) memTotal * 100 / alltime);
  }

  public Path getParityTempPath() {
    return null;
    // return new Path(RaidNode.isaTempPrefix(conf));
  }

  protected void finalize() {
    isaEnEnd();
  }
}

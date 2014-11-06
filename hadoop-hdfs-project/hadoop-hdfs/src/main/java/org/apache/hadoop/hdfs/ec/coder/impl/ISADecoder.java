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

package org.apache.hadoop.hdfs.ec.coder.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.hdfs.BlockMissingException;
import org.apache.hadoop.hdfs.ec.coder.Decoder;
import org.apache.hadoop.hdfs.ec.coder.impl.help.RaidUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ISADecoder extends Decoder {

  public static final Log LOG = LogFactory.getLog(
      "org.apache.hadoop.raid.ISADecoder");

  public ISADecoder(
      Configuration conf, int stripeSize, int paritySize) {
    super(conf, stripeSize, paritySize);
    isaDeInit(stripeSize, paritySize);
  }

  public native static int isaDeInit(int stripeSize, int paritySize);

  public native static int isaDecode(ByteBuffer[] alldata, int[] erasured, int blockSize);

  ;

  public native static int isaDeEnd();
  static {
    System.loadLibrary("isajni");
  }

  public void end() {
    isaDeEnd();
  }

  @Override
  protected void fixErasedBlock(
      FileSystem fs, Path srcFile,
      FileSystem parityFs, Path parityFile,
      long blockSize, long errorOffset, long bytesToSkip, long limit,
      OutputStream out) throws IOException {

    FSDataInputStream[] inputs = new FSDataInputStream[stripeSize + paritySize];
    int[] erasedLocations = buildInputs(fs, srcFile, parityFs, parityFile,
        errorOffset, inputs);
    int blockIdxInStripe = ((int) (errorOffset / blockSize)) % stripeSize;
    int erasedLocationToFix = paritySize + blockIdxInStripe;
    writeFixedBlock(inputs, erasedLocations, erasedLocationToFix,
        bytesToSkip, limit, out);
  }

  protected int[] buildInputs(FileSystem fs, Path srcFile,
                              FileSystem parityFs, Path parityFile,
                              long errorOffset, FSDataInputStream[] inputs)
      throws IOException {
    FileStatus srcStat = fs.getFileStatus(srcFile);
    long blockSize = srcStat.getBlockSize();
    long blockIdx = (int) (errorOffset / blockSize);
    long stripeIdx = blockIdx / stripeSize;
    LOG.info("FileSize = " + srcStat.getLen() + ", blockSize = " + blockSize +
        ", blockIdx = " + blockIdx + ", stripeIdx = " + stripeIdx);
    ArrayList<Integer> erasedLocations = new ArrayList<Integer>();
    // First open streams to the parity blocks.
    for (int i = 0; i < paritySize; i++) {
      long offset = blockSize * i;
      FSDataInputStream in = parityFs.open(
          parityFile, conf.getInt("io.file.buffer.size", 64 * 1024));
      in.seek(offset);
      LOG.info("Adding " + parityFile + ":" + offset + " as input " + i);
      inputs[i] = in;
    }
    // Now open streams to the data blocks.
    for (int i = paritySize; i < paritySize + stripeSize; i++) {
      long offset = blockSize * (stripeIdx * stripeSize + i - paritySize);
      if (offset == errorOffset) {
        LOG.info(srcFile + ":" + offset +
            " is known to have error, adding zeros as input " + i);
        inputs[i] = new FSDataInputStream(new RaidUtils.ZeroInputStream(
            offset + blockSize));
        erasedLocations.add(i);
      } else if (offset > srcStat.getLen()) {
        LOG.info(srcFile + ":" + offset +
            " is past file size, adding zeros as input " + i);
        inputs[i] = new FSDataInputStream(new RaidUtils.ZeroInputStream(
            offset + blockSize));
      } else {
        FSDataInputStream in = fs.open(
            srcFile, conf.getInt("io.file.buffer.size", 64 * 1024));
        in.seek(offset);
        LOG.info("Adding " + srcFile + ":" + offset + " as input " + i);
        inputs[i] = in;
      }
    }
    if (erasedLocations.size() > paritySize) {
      String msg = "Too many erased locations: " + erasedLocations.size();
      LOG.error(msg);
      throw new IOException(msg);
    }
    int[] locs = new int[erasedLocations.size()];
    for (int i = 0; i < locs.length; i++) {
      locs[i] = erasedLocations.get(i);
    }
    return locs;
  }

  /**
   * Decode the inputs provided and write to the output.
   *
   * @param inputs              array of inputs.
   * @param erasedLocations     indexes in the inputs which are known to be erased.
   * @param erasedLocationToFix index in the inputs which needs to be fixed.
   * @param skipBytes           number of bytes to skip before writing to output.
   * @param limit               maximum number of bytes to be written/skipped.
   * @param out                 the output.
   * @throws IOException
   */
  void writeFixedBlock(
      FSDataInputStream[] inputs,
      int[] erasedLocations,
      int erasedLocationToFix,
      long skipBytes,
      long limit,
      OutputStream out) throws IOException {

    LOG.info("Need to write " + (limit - skipBytes) +
        " bytes for erased location index " + erasedLocationToFix);
    int[] tmp = new int[inputs.length];
    int[] decoded = new int[erasedLocations.length];
    long toDiscard = skipBytes;

    ByteBuffer[] readByteBuf = new ByteBuffer[stripeSize + paritySize];
    long start, end, readTotal = 0, writeTotal = 0, calTotal = 0, memTotal = 0, allstart, allend, alltime;

    start = System.nanoTime();
    for (int i = 0; i < stripeSize + paritySize; i++) {
      readByteBuf[i] = ByteBuffer.allocateDirect(bufSize);
    }
    end = System.nanoTime();
    memTotal += end - start;

    allstart = System.nanoTime();


    // Loop while the number of skipped + written bytes is less than the max.
    for (long written = 0; skipBytes + written < limit; ) {
      start = System.nanoTime();
      erasedLocations = readFromInputs(inputs, erasedLocations, limit);
      end = System.nanoTime();
      readTotal += end - start;

      if (decoded.length != erasedLocations.length) {
        decoded = new int[erasedLocations.length];
      }

      int toWrite = (int) Math.min((long) bufSize, limit - (skipBytes + written));
      if (toDiscard >= toWrite) {
        toDiscard -= toWrite;
        continue;
      }

      // Decoded bufSize amount of data.

      start = System.nanoTime();
      for (int i = 0; i < stripeSize + paritySize; i++) {
        readByteBuf[i].position(0);
        readByteBuf[i].put(readBufs[i]);
      }
      end = System.nanoTime();
      memTotal += end - start;

      start = System.nanoTime();
      isaDecode(readByteBuf, erasedLocations, bufSize);
      end = System.nanoTime();
      calTotal += end - start;

      for (int i = 0; i < erasedLocations.length; i++) {
        int index = erasedLocations[i];
        toWrite -= toDiscard;
        start = System.nanoTime();
        readByteBuf[index].position(0);
        readByteBuf[index].get(readBufs[index], 0, readByteBuf[index].remaining());
        end = System.nanoTime();
        memTotal += end - start;

        start = System.nanoTime();
        out.write(readBufs[index], (int) toDiscard, toWrite);
        end = System.nanoTime();
        writeTotal += end - start;

        toDiscard = 0;
        written += toWrite;
        LOG.debug("Wrote " + toWrite + " bytes for erased location index " +
            erasedLocationToFix);
        break;

      }

    }
    allend = System.nanoTime();
    alltime = allend - allstart;
    System.out.println("[ISA decode fix one block] readTotal:" + readTotal
        / 1000 + ", writeTotal:" + writeTotal / 1000 + ", calTotal"
        + calTotal / 1000 + ", memTotal" + memTotal / 1000 + ", all:"
        + alltime / 1000);
    System.out.printf(
        "read:%3.2f%%, write:%3.2f%%, cal:%3.2f%%, mem:%3.2f%%\n",
        (float) readTotal * 100 / alltime, (float) writeTotal * 100
            / alltime, (float) calTotal * 100 / alltime,
        (float) memTotal * 100 / alltime);
  }

  int[] readFromInputs(
      FSDataInputStream[] inputs,
      int[] erasedLocations,
      long limit) throws IOException {
    // For every input, read some data = bufSize.
    for (int i = 0; i < inputs.length; i++) {
      long curPos = inputs[i].getPos();
      try {
        RaidUtils.readTillEnd(inputs[i], readBufs[i], true);
        continue;
      } catch (BlockMissingException e) {
        LOG.error("Encountered BlockMissingException in stream " + i);
      } catch (ChecksumException e) {
        LOG.error("Encountered ChecksumException in stream " + i);
      }

      // Found a new erased location.
      if (erasedLocations.length == paritySize) {
        String msg = "Too many read errors";
        LOG.error(msg);
        throw new IOException(msg);
      }

      // Add this stream to the set of erased locations.
      int[] newErasedLocations = new int[erasedLocations.length + 1];
      for (int j = 0; j < erasedLocations.length; j++) {
        newErasedLocations[j] = erasedLocations[j];
      }
      newErasedLocations[newErasedLocations.length - 1] = i;
      erasedLocations = newErasedLocations;

      LOG.info("Using zeros for stream " + i);
      inputs[i] = new FSDataInputStream(
          new RaidUtils.ZeroInputStream(curPos + limit));
      inputs[i].seek(curPos);
      RaidUtils.readTillEnd(inputs[i], readBufs[i], true);
    }
    return erasedLocations;
  }

  protected void finalize() {
    isaDeEnd();
  }

}

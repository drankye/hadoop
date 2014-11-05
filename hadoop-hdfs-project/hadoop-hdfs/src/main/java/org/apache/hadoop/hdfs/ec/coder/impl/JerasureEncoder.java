package org.apache.hadoop.hdfs.ec.coder.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.ec.coder.Encoder;
import org.apache.hadoop.hdfs.ec.help.RaidUtils;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


public class JerasureEncoder extends Encoder {

  public static final Log LOG = LogFactory.getLog(
      "org.apache.hadoop.raid.JerasureEncoder");

  public JerasureEncoder(Configuration conf, int stripeSize, int paritySize) {
    super(conf, stripeSize, paritySize);
    JerasureEnInit(stripeSize, paritySize, 8, 8192);
  }

  public native static int JerasureEnInit(int k, int m, int w, int packetsize);

  public native static int JerasureEncode(ByteBuffer[] strip, ByteBuffer[] parity, int blocksize);

  public native static int JerasureEnEnd();
  static {
    System.loadLibrary("jerasurejni");
  }

  public void end() {
    JerasureEnEnd();
  }

  @Override
  protected void encodeStripe(InputStream[] blocks, long stripeStartOffset,
                              long blockSize, OutputStream[] outs, Progressable reporter)
      throws IOException {
    // TODO Auto-generated method stub
    long start, end, readTotal = 0, writeTotal = 0, calTotal = 0, memTotal = 0, allstart, allend, alltime;

    start = System.nanoTime();

    ByteBuffer[] readByteBuf = new ByteBuffer[stripeSize];
    ByteBuffer[] writeByteBuf = new ByteBuffer[paritySize];

    for (int i = 0; i < stripeSize; i++) {
      readByteBuf[i] = ByteBuffer.allocateDirect(bufSize);
    }

    for (int i = 0; i < paritySize; i++) {
      writeByteBuf[i] = ByteBuffer.allocateDirect(bufSize);
    }
    end = System.nanoTime();
    memTotal += end - start;

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

      start = System.nanoTime();
      JerasureEncode(readByteBuf, writeByteBuf, bufSize);
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
    System.out.println("[JE encode one stripe] readTotal:" + readTotal / 1000 + ", writeTotal:" + writeTotal / 1000
        + ", calTotal:" + calTotal / 1000 + ", memTotal:" + memTotal / 1000 + ", all:" + alltime / 1000);
    System.out.printf("read:%3.2f%%, write:%3.2f%%, cal:%3.2f%%, mem:%3.2f%%\n",
        (float) readTotal * 100 / alltime, (float) writeTotal * 100 / alltime,
        (float) calTotal * 100 / alltime, (float) memTotal * 100 / alltime);
  }

  @Override
  protected Path getParityTempPath() {
    // TODO Auto-generated method stub
    return null;
    // return new Path(RaidNode.jeTempPrefix(conf));
  }

  protected void finalize() {
    JerasureEnEnd();
  }

}
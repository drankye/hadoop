package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.util.DataChecksum;
import java.io.*;
import java.nio.ByteBuffer;


public abstract class ECBlockReaderBase implements ECBlockReader {

  static final Log LOG = LogFactory.getLog(ECBlockReaderBase.class);

  protected ExtendedBlock block;
  protected final DataNode dn;


  protected DataChecksum checksum;  //get this object when connecting
   /**
   * The total number of bytes we need to transfer from the DN.
   * This is the amount that the user has requested plus some padding
   * at the beginning so that the read can begin on a chunk boundary.
   */
  protected final long startOffset; //user specified read position of the block
  protected long firstChunkOffset;  //the first chunk offset;
  protected long userSpecifiedBytesToRead; //user specified bytes to read
  protected long bytesNeededToFinish;



  protected final boolean verifyChecksum;

  protected ByteBuffer curDataSlice = null;

  protected byte[] skipBuf = null;


  /**
   *
   * @param dataNode
   * @param block
   * @param startOffset  the read position specified by user
   * @param bytesToRead  how many bytes user wants to read
   */
  protected ECBlockReaderBase(DataNode dataNode, ExtendedBlock block,
                             long startOffset, long bytesToRead,
                             boolean verifyChecksum) {

    this.dn = dataNode;
    this.block = block;
    this.startOffset = Math.max(startOffset, 0 );
    this.userSpecifiedBytesToRead = Math.max(bytesToRead, 0);
    this.verifyChecksum = verifyChecksum;

  }


  @Override
  public synchronized int read(byte[] buf, int off, int len)
      throws IOException {

    if (curDataSlice == null || curDataSlice.remaining() == 0 && bytesNeededToFinish > 0) {
      try {
        readNextPacket();
      } finally {
      }
    }
    if (curDataSlice.remaining() == 0) {
      // we're at EOF now
      return -1;
    }

    int nRead = Math.min(curDataSlice.remaining(), len);
    curDataSlice.get(buf, off, nRead);

    return nRead;
  }

  @Override
  public int read(ByteBuffer buf) throws IOException {
    if (curDataSlice == null || curDataSlice.remaining() == 0 && bytesNeededToFinish > 0) {

      try {
        readNextPacket();
      } finally {

      }
    }
    if (curDataSlice.remaining() == 0) {
      // we're at EOF now
      return -1;
    }

    int nRead = Math.min(curDataSlice.remaining(), buf.remaining());
    ByteBuffer writeSlice = curDataSlice.duplicate();
    writeSlice.limit(writeSlice.position() + nRead);
    buf.put(writeSlice);
    curDataSlice.position(writeSlice.position());

    return nRead;
  }

  @Override
  public void readFully(ByteBuffer buf) throws IOException {
    int toRead = buf.remaining();
    while (toRead > 0) {
      int ret = read(buf);
      if (ret < 0) {
        throw new IOException("Premature EOF from inputStream");
      }
      toRead -= ret;
    }
  }

  @Override
  public int readAll(ByteBuffer buf) throws IOException {
    int len = buf.remaining();
    int n = 0;
    for (;;) {
      int nread = read(buf);
      if (nread <= 0)
        return (n == 0) ? nread : n;
      n += nread;
      if (n >= len)
        return n;
    }
  }

  @Override
  public int readAll(byte[] buf, int offset, int len) throws IOException {
    int n = 0;
    for (;;) {
      int nread = read(buf, offset + n, len - n);
      if (nread <= 0)
        return (n == 0) ? nread : n;
      n += nread;
      if (n >= len)
        return n;
    }
  }

  @Override
  public void readFully(byte[] buf, int off, int len) throws IOException {
    int toRead = len;
    while (toRead > 0) {
      int ret = read(buf, off, toRead);
      if (ret < 0) {
        throw new IOException("Premature EOF from inputStream");
      }
      toRead -= ret;
      off += ret;
    }
  }

  protected abstract void readNextPacket() throws IOException;

  public abstract void connect() throws IOException;

  @Override
  public synchronized long skip(long n) throws IOException {
    /* How can we make sure we don't throw a ChecksumException, at least
     * in majority of the cases?. This one throws. */
    if (skipBuf == null) {
      skipBuf = new byte[checksum.getBytesPerChecksum()];
    }

    long nSkipped = 0;
    while (nSkipped < n) {
      int toSkip = (int) Math.min(n - nSkipped, skipBuf.length);
      int ret = read(skipBuf, 0, toSkip);
      if (ret <= 0) {
        return nSkipped;
      }
      nSkipped += ret;
    }
    return nSkipped;
  }



  @Override
  public synchronized void close()
      throws IOException {

  }


}

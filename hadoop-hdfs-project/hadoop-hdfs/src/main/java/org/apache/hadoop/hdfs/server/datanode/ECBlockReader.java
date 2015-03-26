package org.apache.hadoop.hdfs.server.datanode;

import org.apache.hadoop.fs.ByteBufferReadable;
import org.apache.hadoop.fs.ReadOption;
import org.apache.hadoop.hdfs.shortcircuit.ClientMmap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;

/**
 *  This reader is used by datanode to read a block
 *  from local or remote datanode.
 */

public interface ECBlockReader{


  /*
   * same interface as inputStream java.io.InputStream#read()
   * return -1 on EOF, even in the case of a 0-byte read.
   *
   */
  int read(byte[] buf, int off, int len) throws IOException;

  /**
   * Read exactly the given amount of data, throwing an exception
   * if EOF is reached before that amount
   */
  void readFully(byte[] buf, int readOffset, int amtToRead) throws IOException;

  /**
   * Similar to {@link #readFully(byte[], int, int)} except that it will
   * not throw an exception on EOF. However, it differs from the simple
   * {@link #read(byte[], int, int)} call in that it is guaranteed to
   * read the data if it is available. In other words, if this call
   * does not throw an exception, then either the buffer has been
   * filled or the next call will return EOF.
   */
  int readAll(byte[] buf, int offset, int len) throws IOException;

  /**
   * same as {@link #read(byte[], int, int)} except using ByteBuffer.
   *
   */
  int read(ByteBuffer buf) throws IOException;

  /**
   * same as {@link #readFully(byte[], int, int)} except using ByteBuffer.
   *
   */
  void readFully(ByteBuffer buf) throws IOException;

  /**
   * same as {@link #readAll(byte[], int, int)} except using ByteBuffer.
   *
   */
  int readAll(ByteBuffer buf) throws IOException;

  /**
   * Skip the given number of bytes
   *
   * @throws IOException
   */
  long skip(long n) throws IOException;


  /**
   * connect to the source datanode.
   * This method must be called before starting
   * reading data.
   *
   * @throws IOException
   */
  void connect() throws IOException;

  /**
   * Close the block reader.
   *
   * @throws IOException
   */
  void close() throws IOException;


}

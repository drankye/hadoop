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
package org.apache.hadoop.hdfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.apache.hadoop.hdfs.util.IOUtilsClient.updateReadStatistics;

/**
 * Wraps different possible read implementations so that callers can be
 * strategy-agnostic.
 */
interface ReaderStrategy {
  /**
   * Read from a block using the blockReader.
   * @param blockReader
   * @return number of bytes read
   * @throws IOException
   */
  int readBlock(BlockReader blockReader) throws IOException;

  /**
   * Read from a block using the blockReader.
   * @param blockReader
   * @param length number of bytes desired to read, not ensured
   * @return number of bytes read
   * @throws IOException
   */
  int readBlock(BlockReader blockReader, int length) throws IOException;

  /**
   * Read or copy from a src buffer.
   * @param src
   * @return number of bytes copied
   */
  int readBuffer(ByteBuffer src);

  /**
   * Read or copy length of data bytes from a src buffer.
   * @param src
   * @return number of bytes copied
   */
  int readBuffer(ByteBuffer src, int length);

  /**
   * @return the target read buffer.
   */
  ByteBuffer getReadBuffer();

  /**
   * @return the target length to read.
   */
  int getTargetLength();
}

/**
 * Used to read bytes into a byte array buffer.
 */
class ByteArrayStrategy implements ReaderStrategy {
  private final ReadStatistics readStatistics;
  private final byte[] readBuf;
  private int offset;
  private final int targetLength;

  /**
   * The constructor.
   * @param readBuf target buffer to read into
   * @param offset offset into the buffer
   * @param targetLength target length of data
   * @param readStatistics statistics counter
   */
  public ByteArrayStrategy(byte[] readBuf, int offset, int targetLength,
                           ReadStatistics readStatistics) {
    this.readBuf = readBuf;
    this.offset = offset;
    this.targetLength = targetLength;
    this.readStatistics = readStatistics;
  }

  @Override
  public ByteBuffer getReadBuffer() {
    return ByteBuffer.wrap(readBuf, offset, targetLength);
  }

  @Override
  public int getTargetLength() {
    return targetLength;
  }

  @Override
  public int readBlock(BlockReader blockReader) throws IOException {
    return readBlock(blockReader, targetLength);
  }

  @Override
  public int readBlock(BlockReader blockReader,
                       int length) throws IOException {
    int nRead = blockReader.read(readBuf, offset, length);
    if (nRead > 0) {
      updateReadStatistics(readStatistics, nRead, blockReader);
      offset += nRead;
    }
    return nRead;
  }

  @Override
  public int readBuffer(ByteBuffer src) {
    return readBuffer(src, src.remaining());
  }

  @Override
  public int readBuffer(ByteBuffer src, int length) {
    ByteBuffer dup = src.duplicate();
    dup.get(readBuf, offset, length);
    offset += length;
    return length;
  }
}

/**
 * Used to read bytes into a user-supplied ByteBuffer
 */
class ByteBufferStrategy implements ReaderStrategy {
  private final ReadStatistics readStatistics;
  private final ByteBuffer readBuf;
  private final int targetLength;

  /**
   * The constructor.
   * @param readBuf target buffer to read into
   * @param readStatistics statistics counter
   */
  ByteBufferStrategy(ByteBuffer readBuf,
                     ReadStatistics readStatistics) {
    this.readBuf = readBuf;
    this.targetLength = readBuf.remaining();
    this.readStatistics = readStatistics;
  }

  @Override
  public ByteBuffer getReadBuffer() {
    return readBuf;
  }

  @Override
  public int readBlock(BlockReader blockReader) throws IOException {
    return readBlock(blockReader, readBuf.remaining());
  }

  @Override
  public int readBlock(BlockReader blockReader,
                       int length) throws IOException {
    ByteBuffer tmpBuf = readBuf.duplicate();
    tmpBuf.limit(tmpBuf.position() + length);
    int nRead = blockReader.read(readBuf.slice());
    // Only when data are read, update the position
    if (nRead > 0) {
      readBuf.position(readBuf.position() + nRead);
      updateReadStatistics(readStatistics, nRead, blockReader);
    }

    return nRead;
  }

  @Override
  public int getTargetLength() {
    return targetLength;
  }

  @Override
  public int readBuffer(ByteBuffer src) {
    return readBuffer(src, src.remaining());
  }

  @Override
  public int readBuffer(ByteBuffer src, int length) {
    ByteBuffer dup = src.duplicate();
    int newLen = Math.min(readBuf.remaining(), dup.remaining());
    newLen = Math.min(newLen, length);
    dup.limit(dup.position() + newLen);
    readBuf.put(dup);
    return newLen;
  }
}

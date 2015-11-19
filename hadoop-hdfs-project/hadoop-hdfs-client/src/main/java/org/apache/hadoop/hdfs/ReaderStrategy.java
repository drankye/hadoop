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
   * @param length number of bytes desired to read
   * @return number of bytes read
   * @throws IOException
   */
  int readBlock(BlockReader blockReader, int length) throws IOException;

  /**
   * 
   * @param src
   * @return
   */
  int readBuffer(ByteBuffer src);
  int readBuffer(ByteBuffer src, int length);
  ByteBuffer getReadBuffer();
  int getTargetLength();
}

/**
 * Used to read bytes into a byte[]
 */
class ByteArrayStrategy implements ReaderStrategy {
  private final ReadStatistics readStatistics;
  private final byte[] buf;
  private int offset;
  private final int targetLength;

  public ByteArrayStrategy(byte[] buf, int offset, int targetLength,
                           ReadStatistics readStatistics) {
    this.buf = buf;
    this.offset = offset;
    this.targetLength = targetLength;
    this.readStatistics = readStatistics;
  }

  @Override
  public ByteBuffer getReadBuffer() {
    return ByteBuffer.wrap(buf, offset, targetLength);
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
  public int readBlock(BlockReader blockReader, int length) throws IOException {
    int nRead = blockReader.read(buf, offset, length);
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
    dup.get(buf, offset, length);
    offset += length;
    return length;
  }
}

/**
 * Used to read bytes into a user-supplied ByteBuffer
 */
class ByteBufferStrategy implements ReaderStrategy {
  private final ReadStatistics readStatistics;
  private final ByteBuffer readBuffer;
  private final int targetLength;

  ByteBufferStrategy(ByteBuffer readBuffer,
                     ReadStatistics readStatistics) {
    this.readBuffer = readBuffer;
    this.targetLength = readBuffer.remaining();
    this.readStatistics = readStatistics;
  }

  @Override
  public ByteBuffer getReadBuffer() {
    return readBuffer;
  }

  @Override
  public int readBlock(BlockReader blockReader) throws IOException {
    return readBlock(blockReader, readBuffer.remaining());
  }

  @Override
  public int readBlock(BlockReader blockReader, int length) throws IOException {
    ByteBuffer tmpBuf = readBuffer.duplicate();
    tmpBuf.limit(tmpBuf.position() + length);
    int nRead = blockReader.read(readBuffer.slice());
    if (nRead > 0) {
      readBuffer.position(readBuffer.position() + nRead);
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
    int newLen = Math.min(readBuffer.remaining(), dup.remaining());
    newLen = Math.min(newLen, length);
    dup.limit(dup.position() + newLen);
    readBuffer.put(dup);
    return newLen;
  }
}

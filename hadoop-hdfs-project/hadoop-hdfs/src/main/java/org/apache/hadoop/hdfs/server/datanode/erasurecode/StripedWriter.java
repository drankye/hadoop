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
package org.apache.hadoop.hdfs.server.datanode.erasurecode;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.StorageType;
import org.apache.hadoop.hdfs.DFSPacket;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.datatransfer.BlockConstructionStage;
import org.apache.hadoop.hdfs.protocol.datatransfer.IOStreamPair;
import org.apache.hadoop.hdfs.protocol.datatransfer.Sender;
import org.apache.hadoop.hdfs.protocol.datatransfer.sasl.DataEncryptionKeyFactory;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.token.Token;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.EnumSet;

/**
 * StripedReader is used to read from one source DN, it contains a block
 * reader, buffer and striped block index.
 * Only allocate StripedReader once for one source, and the StripedReader
 * has the same array order with sources. Typically we only need to allocate
 * minimum number (minRequiredSources) of StripedReader, and allocate
 * new for new source DN if some existing DN invalid or slow.
 * If some source DN is corrupt, set the corresponding blockReader to
 * null and will never read from it again.
 */
class StripedWriter {
  private static final Logger LOG = DataNode.LOG;

  private StripedReconstructor reconstrutor;
  private StripedWriters stripedWriters;
  private final DataNode datanode;
  private final Configuration conf;

  protected final short index; // internal block index
  protected final ExtendedBlock block;
  private Socket targetSocket;
  private DataOutputStream targetOutputStream;
  private DataInputStream targetInputStream;
  protected ByteBuffer targetBuffer;
  protected long blockOffset4Target = 0;
  protected long seqNo4Target = 0;

  /**
   * Constructor
   * @param stripedWriters
   * @param i the array index of targets
   */
  StripedWriter(StripedWriters stripedWriters, DataNode datanode,
                Configuration conf, short i) throws IOException {
    this.stripedWriters = stripedWriters;
    this.reconstrutor = stripedWriters.reconstructor;
    this.datanode = datanode;
    this.conf = conf;
    this.index = i;

    this.targetBuffer = reconstrutor.allocateBuffer(reconstrutor.getBufferSize());

    this.block = reconstrutor.getBlock(reconstrutor.blockGroup,
        reconstrutor.targetIndices[i]);

    createTargetStream(i);
  }

  ByteBuffer getTargetBuffer() {
    return targetBuffer;
  }

  /**
   * Initialize  output/input streams for transferring data to target
   * and send create block request.
   */
  private boolean createTargetStream(int i) throws IOException {
    Socket socket = null;
    DataOutputStream out = null;
    DataInputStream in = null;
    boolean success = false;
    try {
      InetSocketAddress targetAddr =
          reconstrutor.getSocketAddress4Transfer(stripedWriters.targets[i]);
      socket = datanode.newSocket();
      NetUtils.connect(socket, targetAddr,
          datanode.getDnConf().getSocketTimeout());
      socket.setSoTimeout(datanode.getDnConf().getSocketTimeout());

      Token<BlockTokenIdentifier> blockToken =
          datanode.getBlockAccessToken(block,
              EnumSet.of(BlockTokenIdentifier.AccessMode.WRITE));

      long writeTimeout = datanode.getDnConf().getSocketWriteTimeout();
      OutputStream unbufOut = NetUtils.getOutputStream(socket, writeTimeout);
      InputStream unbufIn = NetUtils.getInputStream(socket);
      DataEncryptionKeyFactory keyFactory =
          datanode.getDataEncryptionKeyFactoryForBlock(block);
      IOStreamPair saslStreams = datanode.getSaslClient().socketSend(
          socket, unbufOut, unbufIn, keyFactory, blockToken, stripedWriters.targets[i]);

      unbufOut = saslStreams.out;
      unbufIn = saslStreams.in;

      out = new DataOutputStream(new BufferedOutputStream(unbufOut,
          DFSUtilClient.getSmallBufferSize(conf)));
      in = new DataInputStream(unbufIn);

      DatanodeInfo source = new DatanodeInfo(datanode.getDatanodeId());
      new Sender(out).writeBlock(block, stripedWriters.targetStorageTypes[i],
          blockToken, "", new DatanodeInfo[]{stripedWriters.targets[i]},
          new StorageType[]{stripedWriters.targetStorageTypes[i]}, source,
          BlockConstructionStage.PIPELINE_SETUP_CREATE, 0, 0, 0, 0,
          reconstrutor.checksum, reconstrutor.cachingStrategy, false, false, null);

      targetSocket = socket;
      targetOutputStream = out;
      targetInputStream = in;
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeStream(out);
        IOUtils.closeStream(in);
        IOUtils.closeStream(socket);
      }
    }
    return success;
  }

  /**
   * Send data to targets
   */
  void transferData2Target(byte[] packetBuf) throws IOException {
    if (targetBuffer.remaining() == 0) {
      return;
    }

    reconstrutor.checksum.calculateChunkedSums(
        targetBuffer.array(), 0, targetBuffer.remaining(), stripedWriters.checksumBuf, 0);

    int ckOff = 0;
    while (targetBuffer.remaining() > 0) {
      DFSPacket packet = new DFSPacket(packetBuf, stripedWriters.maxChunksPerPacket,
          blockOffset4Target, seqNo4Target++,
          stripedWriters.checksumSize, false);
      int maxBytesToPacket = stripedWriters.maxChunksPerPacket * stripedWriters.bytesPerChecksum;
      int toWrite = targetBuffer.remaining() > maxBytesToPacket ?
          maxBytesToPacket : targetBuffer.remaining();
      int ckLen = ((toWrite - 1) / stripedWriters.bytesPerChecksum + 1) * stripedWriters.checksumSize;
      packet.writeChecksum(stripedWriters.checksumBuf, ckOff, ckLen);
      ckOff += ckLen;
      packet.writeData(targetBuffer, toWrite);

      // Send packet
      packet.writeTo(targetOutputStream);

      blockOffset4Target += toWrite;
    }
  }

  // send an empty packet to mark the end of the block
  void endTargetBlock(byte[] packetBuf) throws IOException {
    DFSPacket packet = new DFSPacket(packetBuf, 0,
        blockOffset4Target, seqNo4Target++,
        stripedWriters.checksumSize, true);
    packet.writeTo(targetOutputStream);
    targetOutputStream.flush();
  }

  void close() {
    IOUtils.closeStream(targetOutputStream);
    IOUtils.closeStream(targetInputStream);
    IOUtils.closeStream(targetSocket);
  }
}

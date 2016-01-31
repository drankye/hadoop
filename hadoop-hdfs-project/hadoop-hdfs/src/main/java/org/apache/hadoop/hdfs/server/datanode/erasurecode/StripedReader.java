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
import org.apache.hadoop.fs.ChecksumException;
import org.apache.hadoop.hdfs.BlockReader;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.DFSUtilClient.CorruptedBlocks;
import org.apache.hadoop.hdfs.RemoteBlockReader2;
import org.apache.hadoop.hdfs.net.Peer;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.token.Token;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.Callable;

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
class StripedReader {
  private static final Logger LOG = DataNode.LOG;

  private StripedReconstructor reconstrutor;
  private final DataNode datanode;
  private final Configuration conf;

  protected final short index; // internal block index
  protected final ExtendedBlock block;
  protected final DatanodeInfo source;
  protected BlockReader blockReader;
  private ByteBuffer buffer;

  /**
   * Constructor
   * @param i the array index of sources
   * @param offsetInBlock offset for the internal block
   */
  StripedReader(StripedReconstructor rtb, DataNode datanode,
                Configuration conf,
                int i, long offsetInBlock) {
    this.reconstrutor = rtb;
    this.datanode = datanode;
    this.conf = conf;

    this.index = reconstrutor.liveIndices[i];
    this.source = reconstrutor.sources[i];
    this.block = reconstrutor.getBlock(reconstrutor.blockGroup, index);

    BlockReader blockReader = newBlockReader(block, offsetInBlock, source);
    if (blockReader != null) {
      this.blockReader = blockReader;
    }
  }

  ByteBuffer getReadBuffer() {
    if (buffer == null) {
      this.buffer = reconstrutor.allocateReadBuffer();
    }
    return buffer;
  }

  void resetBlockReader(long offsetInBlock) {
    this.blockReader = newBlockReader(block, offsetInBlock, source);
  }

  private BlockReader newBlockReader(final ExtendedBlock block,
                                     long offsetInBlock, DatanodeInfo dnInfo) {
    if (offsetInBlock >= block.getNumBytes()) {
      return null;
    }
    try {
      InetSocketAddress dnAddr = reconstrutor.getSocketAddress4Transfer(dnInfo);
      Token<BlockTokenIdentifier> blockToken = datanode.getBlockAccessToken(
          block, EnumSet.of(BlockTokenIdentifier.AccessMode.READ));
        /*
         * This can be further improved if the replica is local, then we can
         * read directly from DN and need to check the replica is FINALIZED
         * state, notice we should not use short-circuit local read which
         * requires config for domain-socket in UNIX or legacy config in Windows.
         *
         * TODO: add proper tracer
         */
      return RemoteBlockReader2.newBlockReader(
          "dummy", block, blockToken, offsetInBlock,
          block.getNumBytes() - offsetInBlock, true,
          "", newConnectedPeer(block, dnAddr, blockToken, dnInfo), dnInfo,
          null, reconstrutor.cachingStrategy, datanode.getTracer());
    } catch (IOException e) {
      return null;
    }
  }

  private Peer newConnectedPeer(ExtendedBlock b, InetSocketAddress addr,
                                Token<BlockTokenIdentifier> blockToken, DatanodeID datanodeId)
      throws IOException {
    Peer peer = null;
    boolean success = false;
    Socket sock = null;
    final int socketTimeout = datanode.getDnConf().getSocketTimeout();
    try {
      sock = NetUtils.getDefaultSocketFactory(conf).createSocket();
      NetUtils.connect(sock, addr, socketTimeout);
      peer = DFSUtilClient.peerFromSocketAndKey(datanode.getSaslClient(),
          sock, datanode.getDataEncryptionKeyFactoryForBlock(b),
          blockToken, datanodeId);
      peer.setReadTimeout(socketTimeout);
      success = true;
      return peer;
    } finally {
      if (!success) {
        IOUtils.cleanup(null, peer);
        IOUtils.closeSocket(sock);
      }
    }
  }

  Callable<Void> readFromBlock(final int length,
                               final CorruptedBlocks corruptedBlocks) {
    return new Callable<Void>() {

      @Override
      public Void call() throws Exception {
        try {
          getReadBuffer().limit(length);
          actualReadFromBlock();
          return null;
        } catch (ChecksumException e) {
          LOG.warn("Found Checksum error for " + block + " from "
              + source + " at " + e.getPos());
          corruptedBlocks.addCorruptedBlock(block, source);
          throw e;
        } catch (IOException e) {
          LOG.info(e.getMessage());
          throw e;
        }
      }
    };
  }

  /**
   * Read bytes from block
   */
  void actualReadFromBlock() throws IOException {
    int len = buffer.remaining();
    int n = 0;
    while (n < len) {
      int nread = blockReader.read(buffer);
      if (nread <= 0) {
        break;
      }
      n += nread;
    }
  }

  // close block reader
  void closeBlockReader() {
    try {
      if (blockReader != null) {
        blockReader.close();
        blockReader = null;
      }
    } catch (IOException e) {
      // ignore
    }
  }
}

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
package org.apache.hadoop.hdfs.server.datanode;

import com.google.common.base.Preconditions;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.StripedBlockInfo;
import org.apache.hadoop.hdfs.protocol.datatransfer.IOStreamPair;
import org.apache.hadoop.hdfs.protocol.datatransfer.Op;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.LengthInputStream;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.MD5Hash;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class BlockChecksumHelper {

  public static final Logger LOG =
      LoggerFactory.getLogger(BlockChecksumHelper.class);

  /**
   * The abstract base block checksum computer.
   */
  static abstract class AbstractBlockChecksumComputer {
    final DataNode datanode;
    byte[] outBytes;
    int bytesPerCRC = -1;
    DataChecksum.Type crcType = null;
    long crcPerBlock = -1;
    int checksumSize = -1;

    public AbstractBlockChecksumComputer(DataNode datanode) throws IOException {
      this.datanode = datanode;
    }

    abstract public void compute() throws IOException;
  }

  /**
   * The abstract base block checksum computer.
   */
  static abstract class BlockChecksumComputer
      extends AbstractBlockChecksumComputer {
    final ExtendedBlock block;
    // client side now can specify a range of the block for checksum
    final long requestLength;
    final LengthInputStream metadataIn;
    final DataInputStream checksumIn;
    final long visibleLength;
    final boolean partialBlk;

    BlockMetadataHeader header;
    DataChecksum checksum;

    public BlockChecksumComputer(DataNode datanode,
                                 ExtendedBlock block) throws IOException {
      super(datanode);
      this.block = block;
      this.requestLength = block.getNumBytes();
      Preconditions.checkArgument(requestLength >= 0);

      this.metadataIn = datanode.data.getMetaDataInputStream(block);
      this.visibleLength = datanode.data.getReplicaVisibleLength(block);
      this.partialBlk = requestLength < visibleLength;

      int ioFileBufferSize =
          DFSUtilClient.getIoFileBufferSize(datanode.getConf());
      this.checksumIn = new DataInputStream(new BufferedInputStream(metadataIn,
          ioFileBufferSize));
    }

    abstract public void compute() throws IOException;

    protected void readHeader() throws IOException {
      //read metadata file
      header = BlockMetadataHeader.readHeader(checksumIn);
      checksum = header.getChecksum();
      checksumSize = checksum.getChecksumSize();
      bytesPerCRC = checksum.getBytesPerChecksum();
      crcPerBlock = checksumSize <= 0 ? 0 :
          (metadataIn.getLength() -
              BlockMetadataHeader.getHeaderSize()) / checksumSize;
      crcType = checksum.getChecksumType();
    }

    protected byte[] crcPartialBlock() throws IOException {
      int partialLength = (int) (requestLength % bytesPerCRC);
      if (partialLength > 0) {
        byte[] buf = new byte[partialLength];
        final InputStream blockIn = datanode.data.getBlockInputStream(block,
            requestLength - partialLength);
        try {
          // Get the CRC of the partialLength.
          IOUtils.readFully(blockIn, buf, 0, partialLength);
        } finally {
          IOUtils.closeStream(blockIn);
        }
        checksum.update(buf, 0, partialLength);
        byte[] partialCrc = new byte[checksumSize];
        checksum.writeValue(partialCrc, 0, true);
        return partialCrc;
      }

      return null;
    }
  }

  static class ReplicatedBlockChecksumComputer extends BlockChecksumComputer {

    public ReplicatedBlockChecksumComputer(DataNode datanode,
                                           ExtendedBlock block) throws IOException {
      super(datanode, block);
    }

    @Override
    public void compute() throws IOException {
      try {
        readHeader();

        MD5Hash md5out;
        if (partialBlk && crcPerBlock > 0) {
          md5out = checksumPartialBlock();
        } else {
          md5out = checksumWholeBlock();
        }
        outBytes = md5out.getDigest();

        if (LOG.isDebugEnabled()) {
          LOG.debug("block=" + block + ", bytesPerCRC=" + bytesPerCRC
              + ", crcPerBlock=" + crcPerBlock + ", md5out=" + md5out);
        }
      } finally {
        IOUtils.closeStream(checksumIn);
        IOUtils.closeStream(metadataIn);
      }
    }

    protected MD5Hash checksumWholeBlock() throws IOException {
      MD5Hash md5out = MD5Hash.digest(checksumIn);
      return md5out;
    }

    protected MD5Hash checksumPartialBlock() throws IOException {
      byte[] buffer = new byte[4*1024];
      MessageDigest digester = MD5Hash.getDigester();

      long remaining = (requestLength / bytesPerCRC) * checksumSize;
      for (int toDigest = 0; remaining > 0; remaining -= toDigest) {
        toDigest = checksumIn.read(buffer, 0,
            (int) Math.min(remaining, buffer.length));
        if (toDigest < 0) {
          break;
        }
        digester.update(buffer, 0, toDigest);
      }

      byte[] partialCrc = crcPartialBlock();
      if (partialCrc != null) {
        digester.update(partialCrc);
      }

      return new MD5Hash(digester.digest());
    }
  }

  /**
   * Block checksum computer that simply reads and returns the CRC32 checksum
   * data.Currently only works for striped block group checksum computer.
   */
  static class RawBlockChecksumComputer extends BlockChecksumComputer {
    final int offset;
    final int length;

    public RawBlockChecksumComputer(DataNode datanode, ExtendedBlock block,
        int offset, int length) throws IOException {
      super(datanode, block);
      this.offset = offset;
      this.length = length;
      Preconditions.checkArgument(offset >= 0);
      Preconditions.checkArgument(length >= 0);
    }

    @Override
    public void compute() throws IOException {
      try {
        readHeader();

        outBytes = readBlockCrc();

        if (LOG.isDebugEnabled()) {
          LOG.debug("block=" + block + ", bytesPerCRC=" + bytesPerCRC
              + ", crcPerBlock=" + crcPerBlock);
        }
      } finally {
        IOUtils.closeStream(checksumIn);
        IOUtils.closeStream(metadataIn);
      }
    }

    /**
     * Read block crc checksum data, no MD5 hash.
     */
    private byte[] readBlockCrc() throws IOException {
      int partialLength = (int) (requestLength % bytesPerCRC);
      long crcLen = (requestLength / bytesPerCRC) * checksumSize;
      long remainingCrcLen = crcLen - offset;

      // No need to read partial block at all
      if (remainingCrcLen >= length || partialLength == 0) {
        checksumIn.skip(offset);

        int toReadLen = (int) Math.min(length, remainingCrcLen);
        byte[] buffer = new byte[toReadLen];
        checksumIn.read(buffer);
        return buffer;
      }

      // Need to read partial block because checksumIn isn't enough
      byte[] partialCrc = crcPartialBlock();
      long allRemainingLen = remainingCrcLen + partialCrc.length;
      if (allRemainingLen > 0) {
        int bufferLen = (int) Math.min(length, allRemainingLen);
        byte[] buffer = new byte[bufferLen];

        // Some bytes left in checksumIn to read
        if (crcLen > offset) {
          checksumIn.skip(offset);
          int readLen = checksumIn.read(buffer);
          System.arraycopy(partialCrc, 0, buffer, readLen, bufferLen - readLen);
        } else {
          System.arraycopy(partialCrc, 0, buffer, 0, bufferLen);
        }
        return buffer;
      }

      return new byte[0];
    }
  }

  static class StripedBlockChecksumComputer
      extends AbstractBlockChecksumComputer {

    final StripedBlockInfo stripedBlockInfo;
    final ExtendedBlock blockGroup;
    final ErasureCodingPolicy ecPolicy;
    final DatanodeInfo[] datanodes;
    final Token<BlockTokenIdentifier>[] blockTokens;

    final DataOutputBuffer md5writer = new DataOutputBuffer();

    public StripedBlockChecksumComputer(DataNode datanode,
                      StripedBlockInfo stripedBlockInfo) throws IOException {
      super(datanode);
      this.stripedBlockInfo = stripedBlockInfo;
      this.blockGroup = stripedBlockInfo.getBlock();
      this.ecPolicy = stripedBlockInfo.getErasureCodingPolicy();
      this.datanodes = stripedBlockInfo.getDatanodes();
      this.blockTokens = stripedBlockInfo.getBlockTokens();
    }

    @Override
    public void compute() throws IOException {
      ExtendedBlock blockGroup = stripedBlockInfo.getBlock();

      for (int idx = 0; idx < ecPolicy.getNumDataUnits(); idx++) {
        ExtendedBlock block = StripedBlockUtil.constructInternalBlock(blockGroup,
            ecPolicy.getCellSize(), ecPolicy.getNumDataUnits(), idx);
        DatanodeInfo datanode = datanodes[idx];
        Token<BlockTokenIdentifier> blockToken = blockTokens[idx];
      }

      MD5Hash md5out = MD5Hash.digest(md5writer.getData());
      outBytes = md5out.getDigest();
    }

    private boolean getBlockChecksumData(ExtendedBlock block,
                                         DatanodeInfo datanodeID) throws IOException {
      int timeout = 3000;

      //try the datanode location of the block
      boolean done = false;
      DataOutputStream out = null;
      DataInputStream in = null;

      try {
        //connect to a datanode
        IOStreamPair pair = datanode.connectToDN(datanodeID, timeout,
            block, null);

        LOG.debug("write to {}: {}, block={}",
            datanode, Op.BLOCK_CHECKSUM, block);

      } catch (IOException ie) {
        //LOG.warn("src=" + src + ", datanode=" + datanode, ie);
      } finally {
        IOUtils.closeStream(in);
        IOUtils.closeStream(out);
      }

      return done;
    }
  }
}

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
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.StripedBlockInfo;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.LengthInputStream;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.MD5Hash;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class BlockChecksumHelper {
  public static final Logger LOG =
      LoggerFactory.getLogger(BlockChecksumHelper.class);

  static abstract class BlockChecksumComputer {
    final DataNode datanode;
    MD5Hash md5out;
    int bytesPerCRC = -1;
    DataChecksum.Type crcType = null;
    long crcPerBlock = -1;

    public BlockChecksumComputer(DataNode datanode) throws IOException {
      this.datanode = datanode;
    }

    abstract public void compute() throws IOException;
  }

  static class RawBlockChecksumComputer extends BlockChecksumComputer {
    ExtendedBlock block;
    int offset;
    int length;

    public RawBlockChecksumComputer(DataNode datanode, ExtendedBlock block,
        int offset, int length) throws IOException {
      super(datanode);
      this.block = block;
      this.offset = offset;
      this.length = length;
    }

    @Override
    public void compute() throws IOException {
      // client side now can specify a range of the block for checksum
      long requestLength = block.getNumBytes();
      Preconditions.checkArgument(requestLength >= 0);
      LengthInputStream metadataIn = datanode.data.getMetaDataInputStream(block);

      int ioFileBufferSize = DFSUtilClient.getIoFileBufferSize(datanode.getConf());
      DataInputStream checksumIn = new DataInputStream(
          new BufferedInputStream(metadataIn, ioFileBufferSize));

      try {
        //read metadata file
        final BlockMetadataHeader header = BlockMetadataHeader
            .readHeader(checksumIn);
        final DataChecksum checksum = header.getChecksum();
        final int csize = checksum.getChecksumSize();
        bytesPerCRC = checksum.getBytesPerChecksum();
        crcPerBlock = csize <= 0 ? 0 :
            (metadataIn.getLength() - BlockMetadataHeader.getHeaderSize()) / csize;
        crcType = checksum.getChecksumType();

        md5out = MD5Hash.digest(checksumIn);
        if (LOG.isDebugEnabled()) {
          LOG.debug("block=" + block + ", bytesPerCRC=" + bytesPerCRC
              + ", crcPerBlock=" + crcPerBlock + ", md5out=" + md5out);
        }
      } finally {
        IOUtils.closeStream(checksumIn);
        IOUtils.closeStream(metadataIn);
      }
    }
  }

  static class ReplicatedBlockChecksumComputer extends BlockChecksumComputer {
    final ExtendedBlock block;

    public ReplicatedBlockChecksumComputer(DataNode datanode, ExtendedBlock block) throws IOException {
      super(datanode);
      this.block = block;
    }

    private MD5Hash calcPartialBlockChecksum(ExtendedBlock block,
                                             long requestLength, DataChecksum checksum, DataInputStream checksumIn)
        throws IOException {
      final int bytesPerCRC = checksum.getBytesPerChecksum();
      final int csize = checksum.getChecksumSize();
      final byte[] buffer = new byte[4*1024];
      MessageDigest digester = MD5Hash.getDigester();

      long remaining = requestLength / bytesPerCRC * csize;
      for (int toDigest = 0; remaining > 0; remaining -= toDigest) {
        toDigest = checksumIn.read(buffer, 0,
            (int) Math.min(remaining, buffer.length));
        if (toDigest < 0) {
          break;
        }
        digester.update(buffer, 0, toDigest);
      }

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
        byte[] partialCrc = new byte[csize];
        checksum.writeValue(partialCrc, 0, true);
        digester.update(partialCrc);
      }
      return new MD5Hash(digester.digest());
    }

    @Override
    public void compute() throws IOException {
      // client side now can specify a range of the block for checksum
      long requestLength = block.getNumBytes();
      Preconditions.checkArgument(requestLength >= 0);
      long visibleLength = datanode.data.getReplicaVisibleLength(block);
      boolean partialBlk = requestLength < visibleLength;

      LengthInputStream metadataIn = datanode.data.getMetaDataInputStream(block);

      int ioFileBufferSize = DFSUtilClient.getIoFileBufferSize(datanode.getConf());
      DataInputStream checksumIn = new DataInputStream(
          new BufferedInputStream(metadataIn, ioFileBufferSize));

      try {
        //read metadata file
        final BlockMetadataHeader header = BlockMetadataHeader
            .readHeader(checksumIn);
        final DataChecksum checksum = header.getChecksum();
        final int csize = checksum.getChecksumSize();
        bytesPerCRC = checksum.getBytesPerChecksum();
        crcPerBlock = csize <= 0 ? 0 :
            (metadataIn.getLength() - BlockMetadataHeader.getHeaderSize()) / csize;
        crcType = checksum.getChecksumType();

        md5out = partialBlk && crcPerBlock > 0 ?
            calcPartialBlockChecksum(block, requestLength, checksum, checksumIn)
            : MD5Hash.digest(checksumIn);
        if (LOG.isDebugEnabled()) {
          LOG.debug("block=" + block + ", bytesPerCRC=" + bytesPerCRC
              + ", crcPerBlock=" + crcPerBlock + ", md5out=" + md5out);
        }
      } finally {
        IOUtils.closeStream(checksumIn);
        IOUtils.closeStream(metadataIn);
      }
    }
  }

  static class StripedBlockChecksumComputer extends BlockChecksumComputer {
    final StripedBlockInfo stripedBlockInfo;
    final DataOutputBuffer md5writer = new DataOutputBuffer();

    public StripedBlockChecksumComputer(DataNode datanode,
                                        StripedBlockInfo stripedBlockInfo)
        throws IOException {
      super(datanode);
      this.stripedBlockInfo = stripedBlockInfo;
    }

    @Override
    public void compute() throws IOException {
      /*
      LocatedBlock[] stripBlocks = StripedBlockUtil.parseStripedBlockGroup(
          blockGroup, ecPolicy);

      for (int idx = 0; idx < ecPolicy.getNumDataUnits(); idx++) {
        getBlockChecksumData(i, stripBlocks[idx]);
      }
      */

      md5out = MD5Hash.digest(md5writer.getData());
    }

    /*
    private boolean getBlockChecksumData(int i,
                                         LocatedBlock locatedBlock) throws IOException {
      final ExtendedBlock block = locatedBlock.getBlock();
      final DatanodeInfo datanode = locatedBlock.getLocations()[0];

      int timeout = 3000 * 1 + client.getConf().getSocketTimeout();

      //try the datanode location of the block
      boolean done = false;
      DataOutputStream out = null;
      DataInputStream in = null;

      try {
        //connect to a datanode
        IOStreamPair pair = client.connectToDN(datanode, timeout,
            locatedBlock.getBlockToken());
        out = new DataOutputStream(new BufferedOutputStream(pair.out,
            client.smallBufferSize));
        in = new DataInputStream(pair.in);

        LOG.debug("write to {}: {}, block={}",
            datanode, Op.BLOCK_CHECKSUM, block);
        // get block MD5
        new Sender(out).blockChecksum(block, locatedBlock.getBlockToken());

        final BlockOpResponseProto reply =
            BlockOpResponseProto.parseFrom(PBHelperClient.vintPrefixed(in));

        String logInfo = "for block " + block + " from datanode " + datanode;
        DataTransferProtoUtil.checkBlockOpStatus(reply, logInfo);

        OpBlockChecksumResponseProto checksumData =
            reply.getChecksumResponse();

        //read byte-per-checksum
        final int bpc = checksumData.getBytesPerCrc();
        if (bytesPerCRC == -1) { //first block
          bytesPerCRC = bpc;
          firstBlock = true;
        } else {
          firstBlock = false;
          if (bpc != bytesPerCRC) {
            throw new IOException("Byte-per-checksum not matched: bpc=" + bpc
                + " but bytesPerCRC=" + bytesPerCRC);
          }
        }

        //read crc-per-block
        final long cpb = checksumData.getCrcPerBlock();
        if (firstBlock) { // first block
          crcPerBlock = cpb;
        }

        //read md5out
        final MD5Hash md5out = new MD5Hash(
            checksumData.getMd5().toByteArray());
        md5out.write(md5out);

        // read crc-type
        final DataChecksum.Type ct;
        if (checksumData.hasCrcType()) {
          ct = PBHelperClient.convert(checksumData
              .getCrcType());
        } else {
          LOG.debug("Retrieving checksum from an earlier-version DataNode: " +
              "inferring checksum by reading first byte");
          ct = client.inferChecksumTypeByReading(locatedBlock, datanode);
        }

        if (firstBlock) {
          crcType = ct;
        } else if (crcType != DataChecksum.Type.MIXED && crcType != ct) {
          // if crc types are mixed in a file
          crcType = DataChecksum.Type.MIXED;
        }

        done = true;

        if (firstBlock) {
          LOG.debug("set bytesPerCRC=" + bytesPerCRC
              + ", crcPerBlock=" + crcPerBlock);
        }
        LOG.debug("got reply from " + datanode + ": md5out=" + md5out);
      } catch (InvalidBlockTokenException ibte) {
        if (i > lastRetriedIndex) {
          LOG.debug("Got access token error in response to OP_BLOCK_CHECKSUM "
              + "for file {} for block {} from datanode {}. Will retry "
              + "the block once.", src, block, datanode);
          lastRetriedIndex = i;
          done = true; // actually it's not done; but we'll retry
          i--; // repeat at i-th block
          refetchBlocks = true;
        }
      } catch (IOException ie) {
        LOG.warn("src=" + src + ", datanode=" + datanode, ie);
      } finally {
        IOUtils.closeStream(in);
        IOUtils.closeStream(out);
      }

      return done;
    }*/
  }
}

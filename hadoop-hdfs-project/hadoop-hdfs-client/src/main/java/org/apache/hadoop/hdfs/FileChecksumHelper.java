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

import org.apache.hadoop.fs.MD5MD5CRC32CastagnoliFileChecksum;
import org.apache.hadoop.fs.MD5MD5CRC32FileChecksum;
import org.apache.hadoop.fs.MD5MD5CRC32GzipFileChecksum;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ErasureCodingPolicy;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.hdfs.protocol.LocatedStripedBlock;
import org.apache.hadoop.hdfs.protocol.StripedBlockInfo;
import org.apache.hadoop.hdfs.protocol.datatransfer.DataTransferProtoUtil;
import org.apache.hadoop.hdfs.protocol.datatransfer.IOStreamPair;
import org.apache.hadoop.hdfs.protocol.datatransfer.Op;
import org.apache.hadoop.hdfs.protocol.datatransfer.Sender;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.BlockOpResponseProto;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos.OpBlockChecksumResponseProto;
import org.apache.hadoop.hdfs.protocolPB.PBHelperClient;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.security.token.block.InvalidBlockTokenException;
import org.apache.hadoop.hdfs.util.StripedBlockUtil;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.MD5Hash;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.DataChecksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class FileChecksumHelper {
  public static final Logger LOG =
      LoggerFactory.getLogger(FileChecksumHelper.class);

  static abstract class FileChecksumComputer {
    String src;
    long length;
    LocatedBlocks blockLocations;
    final DFSClient client;
    final ClientProtocol namenode;
    final DataOutputBuffer md5out = new DataOutputBuffer();

    List<LocatedBlock> locatedblocks;
    long remaining = 0L;

    boolean firstBlock = true;
    int bytesPerCRC = -1;
    DataChecksum.Type crcType = null;
    long crcPerBlock = -1;
    boolean refetchBlocks = false;
    int lastRetriedIndex = -1;

    public FileChecksumComputer(String src, long length,
                                LocatedBlocks blockLocations,
                                ClientProtocol namenode,
                                DFSClient client) throws IOException {
      this.src = src;
      this.length = length;
      this.blockLocations = blockLocations;
      this.namenode = namenode;
      this.client = client;
    }

    abstract public MD5MD5CRC32FileChecksum compute() throws IOException;

    protected MD5MD5CRC32FileChecksum computeFileChecksum() {
      //compute file MD5
      final MD5Hash fileMD5 = MD5Hash.digest(md5out.getData());
      switch (crcType) {
        case CRC32:
          return new MD5MD5CRC32GzipFileChecksum(bytesPerCRC,
              crcPerBlock, fileMD5);
        case CRC32C:
          return new MD5MD5CRC32CastagnoliFileChecksum(bytesPerCRC,
              crcPerBlock, fileMD5);
        default:
          // If there is no block allocated for the file,
          // return one with the magic entry that matches what previous
          // hdfs versions return.
          if (locatedblocks.isEmpty()) {
            return new MD5MD5CRC32GzipFileChecksum(0, 0, fileMD5);
          }

          // we should never get here since the validity was checked
          // when getCrcType() was called above.
          return null;
      }
    }
  }

  static class ReplicatedFileChecksumComputer extends FileChecksumComputer {

    public ReplicatedFileChecksumComputer(String src, long length,
                                          LocatedBlocks blockLocations,
                                          ClientProtocol namenode,
                                          DFSClient client) throws IOException {
      super(src, length, blockLocations, namenode, client);
    }

    @Override
    public MD5MD5CRC32FileChecksum compute() throws IOException {
      locatedblocks = blockLocations.getLocatedBlocks();
      long remaining = length;
      if (src.contains(HdfsConstants.SEPARATOR_DOT_SNAPSHOT_DIR_SEPARATOR)) {
        remaining = Math.min(length, blockLocations.getFileLength());
      }

      // get block checksum for each block
      for (int i = 0; i < locatedblocks.size() && remaining > 0; i++) {
        if (refetchBlocks) {  // refetch to get fresh tokens
          blockLocations = client.getBlockLocations(src, length);
          locatedblocks = blockLocations.getLocatedBlocks();
          refetchBlocks = false;
        }

        LocatedBlock locatedBlock = locatedblocks.get(i);

        getBlockChecksumData(i, locatedBlock);
      }

      return computeFileChecksum();
    }

    private void getBlockChecksumData(int i,
                               LocatedBlock locatedBlock) throws IOException {
      final ExtendedBlock block = locatedBlock.getBlock();
      if (remaining < block.getNumBytes()) {
        block.setNumBytes(remaining);
      }
      remaining -= block.getNumBytes();
      final DatanodeInfo[] datanodes = locatedBlock.getLocations();

      final int timeout = 3000 * datanodes.length +
          client.getConf().getSocketTimeout();

      //try each datanode location of the block
      boolean done = false;
      IOStreamPair pair = null;
      for (int j = 0; !done && j < datanodes.length; j++) {
        try {
          //connect to a datanode
          pair = client.connectToDN(datanodes[j], timeout,
                  locatedBlock.getBlockToken());

          LOG.debug("write to {}: {}, block={}",
              datanodes[j], Op.BLOCK_CHECKSUM, block);
          // get block MD5
          new Sender((DataOutputStream) pair.out).blockChecksum(block,
              locatedBlock.getBlockToken());

          final BlockOpResponseProto reply =
              BlockOpResponseProto.parseFrom(PBHelperClient.vintPrefixed(pair.in));

          String logInfo = "for block " + block + " from datanode " +
              datanodes[j];
          DataTransferProtoUtil.checkBlockOpStatus(reply, logInfo);

          OpBlockChecksumResponseProto checksumData =
              reply.getChecksumResponse();

          //read byte-per-checksum
          final int bpc = checksumData.getBytesPerCrc();
          if (i == 0) { //first block
            bytesPerCRC = bpc;
          } else if (bpc != bytesPerCRC) {
            throw new IOException("Byte-per-checksum not matched: bpc=" + bpc
                + " but bytesPerCRC=" + bytesPerCRC);
          }

          //read crc-per-block
          final long cpb = checksumData.getCrcPerBlock();
          if (locatedblocks.size() > 1 && i == 0) {
            crcPerBlock = cpb;
          }

          //read md5
          final MD5Hash md5 = new MD5Hash(
              checksumData.getMd5().toByteArray());
          md5.write(md5out);

          // read crc-type
          final DataChecksum.Type ct;
          if (checksumData.hasCrcType()) {
            ct = PBHelperClient.convert(checksumData
                .getCrcType());
          } else {
            LOG.debug("Retrieving checksum from an earlier-version DataNode: " +
                "inferring checksum by reading first byte");
            ct = client.inferChecksumTypeByReading(locatedBlock, datanodes[j]);
          }

          if (i == 0) { // first block
            crcType = ct;
          } else if (crcType != DataChecksum.Type.MIXED
              && crcType != ct) {
            // if crc types are mixed in a file
            crcType = DataChecksum.Type.MIXED;
          }

          done = true;

          if (LOG.isDebugEnabled()) {
            if (i == 0) {
              LOG.debug("set bytesPerCRC=" + bytesPerCRC
                  + ", crcPerBlock=" + crcPerBlock);
            }
            LOG.debug("got reply from " + datanodes[j] + ": md5=" + md5);
          }
        } catch (InvalidBlockTokenException ibte) {
          if (i > lastRetriedIndex) {
            LOG.debug("Got access token error in response to OP_BLOCK_CHECKSUM "
                    + "for file {} for block {} from datanode {}. Will retry "
                    + "the block once.",
                src, block, datanodes[j]);
            lastRetriedIndex = i;
            done = true; // actually it's not done; but we'll retry
            i--; // repeat at i-th block
            refetchBlocks = true;
            break;
          }
        } catch (IOException ie) {
          LOG.warn("src=" + src + ", datanodes[" + j + "]=" + datanodes[j], ie);
        } finally {
          if (pair != null) {
            IOUtils.closeStream(pair.in);
            IOUtils.closeStream(pair.out);
          }
        }
      }

      if (!done) {
        throw new IOException("Fail to get block MD5 for " + block);
      }
    }
  }

  static class StripedFileChecksumComputer extends FileChecksumComputer {
    private ErasureCodingPolicy ecPolicy;

    public StripedFileChecksumComputer(String src, long length,
                                       LocatedBlocks blockLocations,
                                       ClientProtocol namenode,
                                       DFSClient client,
                                       ErasureCodingPolicy ecPolicy)
        throws IOException {
      super(src, length, blockLocations, namenode, client);

      this.ecPolicy = ecPolicy;
    }

    @Override
    public MD5MD5CRC32FileChecksum compute() throws IOException {
      locatedblocks = blockLocations.getLocatedBlocks();
      long remaining = length;
      if (src.contains(HdfsConstants.SEPARATOR_DOT_SNAPSHOT_DIR_SEPARATOR)) {
        remaining = Math.min(length, blockLocations.getFileLength());
      }

      // get block checksum for each block
      for (int i = 0; i < locatedblocks.size() && remaining > 0; i++) {
        if (refetchBlocks) {  // refetch to get fresh tokens
          blockLocations = client.getBlockLocations(src, length);
          locatedblocks = blockLocations.getLocatedBlocks();
          refetchBlocks = false;
        }

        LocatedBlock locatedBlock = locatedblocks.get(i);
        LocatedStripedBlock blockGroup = (LocatedStripedBlock) locatedBlock;
        getBlockGroupChecksumData(i, blockGroup);
      }

      return computeFileChecksum();
    }

    private void getBlockGroupChecksumData(int i,
                                           LocatedStripedBlock blockGroup) throws IOException {
      LocatedBlock[] stripBlocks = StripedBlockUtil.parseStripedBlockGroup(
          blockGroup, ecPolicy);

      for (int idx = 0; idx < ecPolicy.getNumDataUnits(); idx++) {
        getBlockChecksumData(i, stripBlocks[idx]);
      }
    }

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

        //read md5
        final MD5Hash md5 = new MD5Hash(
            checksumData.getMd5().toByteArray());
        md5.write(md5out);

        // read crc-type
        final DataChecksum.Type ct;
        if (checksumData.hasCrcType()) {
          ct = PBHelperClient.convert(checksumData.getCrcType());
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
        LOG.debug("got reply from " + datanode + ": md5=" + md5);
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
    }
  }

  static class StripedFileChecksumComputer2 extends FileChecksumComputer {
    private ErasureCodingPolicy ecPolicy;

    public StripedFileChecksumComputer2(String src, long length,
                                        LocatedBlocks blockLocations,
                                        ClientProtocol namenode,
                                        DFSClient client,
                                        ErasureCodingPolicy ecPolicy)
        throws IOException {
      super(src, length, blockLocations, namenode, client);

      this.ecPolicy = ecPolicy;
    }

    @Override
    public MD5MD5CRC32FileChecksum compute() throws IOException {
      locatedblocks = blockLocations.getLocatedBlocks();
      long remaining = length;
      if (src.contains(HdfsConstants.SEPARATOR_DOT_SNAPSHOT_DIR_SEPARATOR)) {
        remaining = Math.min(length, blockLocations.getFileLength());
      }

      // get block checksum for each block
      for (int i = 0; i < locatedblocks.size() && remaining > 0; i++) {
        if (refetchBlocks) {  // refetch to get fresh tokens
          blockLocations = client.getBlockLocations(src, length);
          locatedblocks = blockLocations.getLocatedBlocks();
          refetchBlocks = false;
        }

        LocatedBlock locatedBlock = locatedblocks.get(i);
        LocatedStripedBlock blockGroup = (LocatedStripedBlock) locatedBlock;
        getBlockGroupChecksumData(i, blockGroup);
      }

      return computeFileChecksum();
    }

    private void getBlockGroupChecksumData(int i,
                                           LocatedStripedBlock blockGroup) throws IOException {
      LocatedBlock[] stripBlocks = StripedBlockUtil.parseStripedBlockGroup(
          blockGroup, ecPolicy);

      int numBlocks = stripBlocks.length;
      ExtendedBlock leadBlock = stripBlocks[0].getBlock();
      Token<BlockTokenIdentifier> blockToken = stripBlocks[0].getBlockToken();
      //TODO: random to choose the target Datanode
      DatanodeInfo chosenDatanode = stripBlocks[0].getLocations()[0];
      DatanodeInfo[] datanodes = new DatanodeInfo[numBlocks];
      for (LocatedBlock lb : stripBlocks) {
        datanodes[i] = lb.getLocations()[0];
      }

      StripedBlockInfo stripedBlockInfo = new StripedBlockInfo(
          leadBlock, datanodes, ecPolicy);


      int timeout = 3000 * numBlocks + client.getConf().getSocketTimeout();

      //try the datanode location of the block
      boolean done = false;
      DataOutputStream out = null;
      DataInputStream in = null;

      try {
        //connect to a datanode
        IOStreamPair pair = client.connectToDN(chosenDatanode,
            timeout, blockToken);
        out = new DataOutputStream(new BufferedOutputStream(pair.out,
            client.smallBufferSize));
        in = new DataInputStream(pair.in);

        LOG.debug("write to {}: {}, block={}",
            chosenDatanode, Op.BLOCK_CHECKSUM, leadBlock);
        // get block MD5
        new Sender(out).blockGroupChecksum(stripedBlockInfo, blockToken, 1);

        final BlockOpResponseProto reply =
            BlockOpResponseProto.parseFrom(PBHelperClient.vintPrefixed(in));

        String logInfo = "for block " + leadBlock +
            " from datanode " + chosenDatanode;
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

        //read md5
        final MD5Hash md5 = new MD5Hash(
            checksumData.getMd5().toByteArray());
        md5.write(md5out);

        // read crc-type
        final DataChecksum.Type ct;
        if (checksumData.hasCrcType()) {
          ct = PBHelperClient.convert(checksumData
              .getCrcType());
        } else {
          LOG.debug("Retrieving checksum from an earlier-version DataNode: " +
              "inferring checksum by reading first byte");
          throw new IOException("No checksum type found for block group " +
              leadBlock);
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
        LOG.debug("got reply from " + chosenDatanode + ": md5=" + md5);
      } catch (InvalidBlockTokenException ibte) {
        if (i > lastRetriedIndex) {
          LOG.debug("Got access token error in response to OP_BLOCK_CHECKSUM "
              + "for file {} for block {} from datanode {}. Will retry "
              + "the block once.", src, leadBlock, chosenDatanode);
          lastRetriedIndex = i;
          done = true; // actually it's not done; but we'll retry
          i--; // repeat at i-th block
          refetchBlocks = true;
        }
      } catch (IOException ie) {
        LOG.warn("src=" + src + ", datanode=" + chosenDatanode, ie);
      } finally {
        IOUtils.closeStream(in);
        IOUtils.closeStream(out);
      }
    }
  }
}

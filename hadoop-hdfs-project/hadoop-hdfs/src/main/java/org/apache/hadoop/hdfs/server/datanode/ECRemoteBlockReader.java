package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.protocol.datatransfer.*;
import org.apache.hadoop.hdfs.protocol.datatransfer.sasl.DataEncryptionKeyFactory;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos;
import org.apache.hadoop.hdfs.protocolPB.PBHelper;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenSecretManager;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.protocol.DatanodeRegistration;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.token.Token;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.EnumSet;

/**
 * Read a block from remote datanode.
 *
 */
public class ECRemoteBlockReader extends ECBlockReaderBase {

  static final Log LOG = LogFactory.getLog(ECRemoteBlockReader.class);

  private DatanodeInfo remoteDataNodeInfo;

  private ReadableByteChannel in;
  private DataOutputStream responseOutputStream;
  private Socket socket;

  /**
   * offset in block of the last chunk received
   */
  private long lastSeqNo = -1;

  protected final PacketReceiver packetReceiver = new PacketReceiver(true);

  public ECRemoteBlockReader(DataNode dataNode, ExtendedBlock block,
                             DatanodeInfo remoteDataNodeInfo) {
    this(dataNode, block, remoteDataNodeInfo, 0, block.getNumBytes(), true);
  }
  public ECRemoteBlockReader(DataNode dataNode, ExtendedBlock block,
                                DatanodeInfo remoteDataNodeInfo, long startOffset,
                                long bytesToRead, boolean verifyChecksum) {

    super(dataNode, block, startOffset, bytesToRead, verifyChecksum);
    this.remoteDataNodeInfo = remoteDataNodeInfo;
   }

  @Override
  public void connect() throws IOException {
    try {
      DatanodeRegistration bpReg = dn.getDNRegistrationForBP(block.getBlockPoolId());
      final String dnAddr = remoteDataNodeInfo.getXferAddr(dn.getDnConf().connectToDnViaHostname);
      InetSocketAddress curTarget = NetUtils.createSocketAddr(dnAddr);
      if (DataNode.LOG.isDebugEnabled()) {
        DataNode.LOG.debug("ECRemoteBlockReader Connecting to datanode " + dnAddr
            + " from " + dn.getXferAddress() + " for block "
            + block);
      }
      socket = dn.newSocket();
      NetUtils.connect(socket, curTarget, dn.getDnConf().socketTimeout);
      socket.setSoTimeout(dn.getDnConf().socketTimeout);

      // Header info
      Token<BlockTokenIdentifier> accessToken = BlockTokenSecretManager.DUMMY_TOKEN;
      if (dn.isBlockTokenEnabled) {
        accessToken = dn.blockPoolTokenSecretManager.generateToken(block,
            EnumSet.of(BlockTokenSecretManager.AccessMode.WRITE));
      }
      long writeTimeout = dn.getDnConf().socketWriteTimeout +
          HdfsServerConstants.WRITE_TIMEOUT_EXTENSION;
      OutputStream unbufOut = NetUtils.getOutputStream(socket, writeTimeout);
      InputStream unbufIn = NetUtils.getInputStream(socket);
      DataEncryptionKeyFactory keyFactory =
          dn.getDataEncryptionKeyFactoryForBlock(block);
      IOStreamPair saslStreams = dn.saslClient.socketSend(socket, unbufOut,
          unbufIn, keyFactory, accessToken, bpReg);
      unbufOut = saslStreams.out;
      unbufIn = saslStreams.in;

      responseOutputStream = new DataOutputStream(new BufferedOutputStream(unbufOut,
          HdfsConstants.SMALL_BUFFER_SIZE));
      DataInputStream inputStream = new DataInputStream(unbufIn);
      new Sender(responseOutputStream).readBlock(block, accessToken,
          NetUtils.getHostNameOfIP(dn.getDisplayName()), 0, block.getNumBytes(),
          true, new CachingStrategy(true, dn.getDnConf().readaheadLength));
      DataTransferProtos.BlockOpResponseProto status = DataTransferProtos.BlockOpResponseProto.parseFrom(
          PBHelper.vintPrefixed(inputStream));
      if (status.getStatus() != DataTransferProtos.Status.SUCCESS) {
        DataNode.LOG.debug("Error connecting to DN " + curTarget);
        throw new IOException("Got error for OP_READ_BLOCK, remote="
            + remoteDataNodeInfo.getName() + ", block pool = " + block.getBlockPoolId()
            + ", block " + block.getBlockId() + "_" + block.getGenerationStamp());
      }
      DataTransferProtos.ReadOpChecksumInfoProto checksumInfo = status.getReadOpChecksumInfo();
      checksum = DataTransferProtoUtil.fromProto(checksumInfo.getChecksum());
      // Read the first chunk offset.
      firstChunkOffset = checksumInfo.getChunkOffset();

      if (firstChunkOffset < 0 || firstChunkOffset > startOffset ||
          firstChunkOffset <= (startOffset - checksum.getBytesPerChecksum())) {
        throw new IOException("BlockReader: error in first chunk offset (" +
            firstChunkOffset + ") startOffset is " + startOffset);
      }

      bytesNeededToFinish = userSpecifiedBytesToRead + (startOffset - firstChunkOffset);

      in = Channels.newChannel(inputStream);
    } catch (IOException ioe) {
      try {
        close();
      } catch (IOException e) {
        LOG.warn("Error in close(): ", e);
      }
      throw ioe;
    }
  }


  @Override
  protected void readNextPacket() throws IOException {

    if(bytesNeededToFinish <=0){
      if(curDataSlice != null)
        curDataSlice.clear();
      return;
    }
    //Read packet headers.
    packetReceiver.receiveNextPacket(in);

    PacketHeader curHeader = packetReceiver.getHeader();
    curDataSlice = packetReceiver.getDataSlice();
    assert curDataSlice.capacity() == curHeader.getDataLen();

    if (LOG.isTraceEnabled()) {
      LOG.trace("DFSClient readNextPacket got header " + curHeader);
    }

    // Sanity check the lengths
    if (!curHeader.sanityCheck(lastSeqNo)) {
      throw new IOException("BlockReader: error in packet header " +
          curHeader);
    }

    if (curHeader.getDataLen() > 0) {
      int chunks = 1 + (curHeader.getDataLen() - 1) / checksum.getBytesPerChecksum();
      int checksumsLen = chunks * checksum.getChecksumSize();

      assert packetReceiver.getChecksumSlice().capacity() == checksumsLen :
          "checksum slice capacity=" + packetReceiver.getChecksumSlice().capacity() +
              " checksumsLen=" + checksumsLen;

      lastSeqNo = curHeader.getSeqno();
      if (verifyChecksum && curDataSlice.remaining() > 0) {
        checksum.verifyChunkedSums(curDataSlice,
            packetReceiver.getChecksumSlice(),
            "", curHeader.getOffsetInBlock());    //filename = ""
      }
      bytesNeededToFinish -= curHeader.getDataLen();
    }

    // First packet will include some data prior to the first byte
    // the user requested. Skip it.
    if (curHeader.getOffsetInBlock() < startOffset) {
      int newPos = (int) (startOffset - curHeader.getOffsetInBlock());
      curDataSlice.position(newPos);
    }

    if (bytesNeededToFinish <= 0) {
 //     readTrailingEmptyPacket();  //question: why this sentence?
      if (verifyChecksum) {
        sendReadResult(DataTransferProtos.Status.CHECKSUM_OK);
      } else {
        sendReadResult(DataTransferProtos.Status.SUCCESS);
      }
    }
  }


  /**
   * When the reader reaches end of the read, it sends a status response
   * (e.g. CHECKSUM_OK) to the DN. Failure to do so could lead to the DN
   * closing our connection (which we will re-open), but won't affect
   * data correctness.
   */
  void sendReadResult(DataTransferProtos.Status statusCode) {
     try {
      DataTransferProtos.ClientReadStatusProto.newBuilder()
          .setStatus(statusCode)
          .build()
          .writeDelimitedTo(responseOutputStream);
      responseOutputStream.flush();

    } catch (IOException e) {
      // It's ok not to be able to send this. But something is probably wrong.
      LOG.info("Could not send read status (" + statusCode + ") to datanode " +
          remoteDataNodeInfo.getName() + ": " + e.getMessage());
    }
  }




  private void readTrailingEmptyPacket() throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Reading empty packet at end of read");
    }

    packetReceiver.receiveNextPacket(in);

    PacketHeader trailer = packetReceiver.getHeader();
    if (!trailer.isLastPacketInBlock() ||
        trailer.getDataLen() != 0) {
      throw new IOException("Expected empty end-of-read packet! Header: " +
          trailer);
    }
  }

  @Override
  public void close() throws IOException {
    super.close();
    IOException ioe = null;
    if(socket != null) {
      try {
        socket.close(); // close checksum file
      } catch (IOException e) {
        ioe = e;
      }
      socket = null;
    }

    // throw IOException if there is any
    if(ioe!= null) {
      throw ioe;
    }
  }


}

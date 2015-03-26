package org.apache.hadoop.hdfs.server.datanode;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.nio.channels.ClosedChannelException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.HadoopIllegalArgumentException;
import org.apache.hadoop.fs.FSOutputSummer;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSClientFaultInjector;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.StorageType;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.protocol.datatransfer.*;
import org.apache.hadoop.hdfs.protocol.datatransfer.sasl.DataEncryptionKeyFactory;
import org.apache.hadoop.hdfs.protocol.proto.DataTransferProtos;
import org.apache.hadoop.hdfs.protocolPB.PBHelper;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenSecretManager;
import org.apache.hadoop.hdfs.security.token.block.InvalidBlockTokenException;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.protocol.DatanodeRegistration;
import org.apache.hadoop.hdfs.util.ByteArrayManager;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;

import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.Daemon;
import org.apache.hadoop.util.DataChecksum;

/**
 * ECWorker on a datanode may generate multiple blocks.
 * We use ECRemoteBlockWriter to write some blocks directly
 * to remote datanodes specified by namenode.
 */
public class ECRemoteBlockWriter extends FSOutputSummer{
  public static final Log LOG = LogFactory.getLog(ECRemoteBlockWriter.class);;

  private DataNode dn;
  private Socket s;
  // closed is accessed by different threads under different locks.
  private volatile boolean closed = false;
  private final long blockSize;
  private final int bytesPerChecksum;
  private final DataChecksum checksum4WriteBlock;
  /** max size of the block queue */
  private final int maxQueueSize;
  private final int defaultPacketSize = 64*1024;//todo
  /** both dataQueue and ackQueue are protected by dataQueue lock */
  private final LinkedList<Packet> dataQueue = new LinkedList<Packet>();
  private final LinkedList<Packet> ackQueue = new LinkedList<Packet>();

  private Packet currentPacket = null;
  private DataStreamer streamer;

  private long currentSeqno = 0;
  private long lastQueuedSeqno = -1;
  private long lastAckedSeqno = -1;
  /** bytes written in current block */
  private long bytesCurBlock = 0;
  /** write packet size, not including the header */
  private int packetSize = 0;
  private int chunksPerPacket = 0;
  private final AtomicReference<IOException> lastException = new AtomicReference<IOException>();

  /** offset when flush was invoked */
  private long lastFlushOffset = 0;
  /** persist blocks on namenode */
  private final AtomicBoolean persistBlocks = new AtomicBoolean(false);

  /** force blocks to disk upon close */
  private boolean shouldSyncBlock = false;
  private final AtomicReference<CachingStrategy> cachingStrategy;

  private ByteArrayManager byteArrayManager;


  private ECRemoteBlockWriter(ExtendedBlock block, DataNode dn,
             DatanodeInfo remoteDatanodeInfo, StorageType remoteStorageType,
             DataChecksum checksum) throws IOException {
    this(block, dn, remoteDatanodeInfo,remoteStorageType, checksum,20);  //20 can be get from property
  }
  private ECRemoteBlockWriter(ExtendedBlock block, DataNode dn,
                   DatanodeInfo remoteDatanodeInfo, StorageType remoteStorageType,
                   DataChecksum checksum, int maxQueueSize) throws IOException {
    super(checksum);
    this.dn = dn;
    this.blockSize =  dn.getConf().getLong(DFSConfigKeys.DFS_BLOCK_SIZE_KEY,DFSConfigKeys.DFS_BLOCK_SIZE_DEFAULT);
    this.cachingStrategy = new AtomicReference<CachingStrategy>(CachingStrategy.newDefaultStrategy());
    this.maxQueueSize = maxQueueSize;
    this.checksum4WriteBlock = checksum;
    this.bytesPerChecksum = checksum.getBytesPerChecksum();
    if (bytesPerChecksum <= 0) {
      throw new HadoopIllegalArgumentException(
          "Invalid value: bytesPerChecksum = " + bytesPerChecksum + " <= 0");
    }
    if (blockSize % bytesPerChecksum != 0) {
      throw new HadoopIllegalArgumentException("Invalid values: "
          + DFSConfigKeys.DFS_BYTES_PER_CHECKSUM_KEY + " (=" + bytesPerChecksum
          + ") must divide block size (=" + blockSize + ").");
    }


    //set the ByteArrayManager according to the configuration of client
    final int countThreshold = dn.getConf().getInt(
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_THRESHOLD_KEY,
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_THRESHOLD_DEFAULT);
    final int countLimit = dn.getConf().getInt(
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_LIMIT_KEY,
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_LIMIT_DEFAULT);
    final long countResetTimePeriodMs = dn.getConf().getLong(
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_RESET_TIME_PERIOD_MS_KEY,
        DFSConfigKeys.DFS_CLIENT_WRITE_BYTE_ARRAY_MANAGER_COUNT_RESET_TIME_PERIOD_MS_DEFAULT);
    ByteArrayManager.Conf writeByteArrayManagerConf = new ByteArrayManager.Conf(
        countThreshold, countLimit, countResetTimePeriodMs);
    this.byteArrayManager = ByteArrayManager.newInstance(writeByteArrayManagerConf);


    //the packet size is same with the client
    this.packetSize = dn.getConf().getInt(DFSConfigKeys.DFS_CLIENT_WRITE_PACKET_SIZE_KEY,
                         DFSConfigKeys.DFS_CLIENT_WRITE_PACKET_SIZE_DEFAULT);
    computePacketChunkSize(packetSize, bytesPerChecksum);
    streamer = new DataStreamer(block, remoteDatanodeInfo, remoteStorageType);

  }

  public static ECRemoteBlockWriter newInstance(ExtendedBlock block, DataNode dn,
                              DatanodeInfo remoteDatanodeInfo, StorageType remoteStorageType,
                              DataChecksum checksum) throws IOException {
    final ECRemoteBlockWriter out = new ECRemoteBlockWriter(block, dn,
                           remoteDatanodeInfo, remoteStorageType, checksum);
    out.start();
    return out;
  }


  /** Use {@link ByteArrayManager} to create buffer.*/
  private Packet createPacket(int packetSize, int chunksPerPkt, long offsetInBlock,
                              long seqno) throws InterruptedIOException {
    final byte[] buf;
    final int bufferSize = PacketHeader.PKT_MAX_HEADER_LEN + packetSize;

    try {
      buf = byteArrayManager.newByteArray(bufferSize);
    } catch (InterruptedException ie) {
      final InterruptedIOException iioe = new InterruptedIOException(
          "seqno=" + seqno);
      iioe.initCause(ie);
      throw iioe;
    }
    return new Packet(buf, chunksPerPkt, offsetInBlock, seqno, getChecksumSize());
  }



  /**
   * Create a socket for a remote datanode
   * @return the socket connected to the remote datanode
   */
  private Socket createSocket(final DatanodeInfo remoteDatanodeInfo) throws IOException {

    final String dnAddr = remoteDatanodeInfo.getXferAddr(dn.getDnConf().connectToDnViaHostname);
    InetSocketAddress curTarget = NetUtils.createSocketAddr(dnAddr);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Connecting to datanode " + dnAddr);
    }
    Socket socket = dn.newSocket();
    NetUtils.connect(socket, curTarget, dn.getDnConf().socketTimeout);
    socket.setSoTimeout(dn.getDnConf().socketTimeout);

    return socket;
  }

  @Override
  protected void checkClosed() throws IOException {
    if (closed) {
      IOException e = lastException.get();
      throw e != null ? e : new ClosedChannelException();
    }
  }


  private void computePacketChunkSize(int psize, int csize) {
    final int chunkSize = csize + getChecksumSize();
    chunksPerPacket = Math.max(psize/chunkSize, 1);
    packetSize = chunkSize*chunksPerPacket;
    if (DFSClient.LOG.isDebugEnabled()) {
      DFSClient.LOG.debug("computePacketChunkSize: chunkSize=" + chunkSize +
          ", chunksPerPacket=" + chunksPerPacket +
          ", packetSize=" + packetSize);
    }
  }

  private void queueCurrentPacket() {
    synchronized (dataQueue) {
      if (currentPacket == null) return;
      dataQueue.addLast(currentPacket);
      lastQueuedSeqno = currentPacket.seqno;
      if (DFSClient.LOG.isDebugEnabled()) {
        DFSClient.LOG.debug("Queued packet " + currentPacket.seqno);
      }
      currentPacket = null;
      dataQueue.notifyAll();
    }
  }

  private void waitAndQueueCurrentPacket() throws IOException {
    synchronized (dataQueue) {
      try {
        // If queue is full, then wait till we have enough space
        while (!closed && dataQueue.size() + ackQueue.size()  > maxQueueSize) {
          try {
            dataQueue.wait();
          } catch (InterruptedException e) {
            // If we get interrupted while waiting to queue data, we still need to get rid
            // of the current packet. This is because we have an invariant that if
            // currentPacket gets full, it will get queued before the next writeChunk.
            //
            // Rather than wait around for space in the queue, we should instead try to
            // return to the caller as soon as possible, even though we slightly overrun
            // the MAX_PACKETS length.
            Thread.currentThread().interrupt();
            break;
          }
        }
        checkClosed();
        queueCurrentPacket();
      } catch (ClosedChannelException e) {
      }
    }
  }


  @Override
  protected synchronized void writeChunk(byte[] b, int offset, int len,
                                         byte[] checksum, int ckoff, int cklen) throws IOException {

    checkClosed();

    if (len > bytesPerChecksum) {
      throw new IOException("writeChunk() buffer size is " + len +
          " is larger than supported  bytesPerChecksum " +
          bytesPerChecksum);
    }
    if (cklen != 0 && cklen != getChecksumSize()) {
      throw new IOException("writeChunk() checksum size is supposed to be " +
          getChecksumSize() + " but found to be " + cklen);
    }

    if (bytesCurBlock + len > blockSize) {
      throw new IOException("The block will be overflowed. bytesCurBlock: "
          +bytesCurBlock+ ", len: " + len + ", block size: " + blockSize);
    }

    if (currentPacket == null) {
      currentPacket = createPacket(packetSize, chunksPerPacket,
          bytesCurBlock, currentSeqno++);
      if (DFSClient.LOG.isDebugEnabled()) {
        DFSClient.LOG.debug("writeChunk() allocating new packet seqno=" +
            currentPacket.seqno +
            ", packetSize=" + packetSize +
            ", chunksPerPacket=" + chunksPerPacket +
            ", bytesCurBlock=" + bytesCurBlock);
      }
    }

    currentPacket.writeChecksum(checksum, ckoff, cklen);
    currentPacket.writeData(b, offset, len);
    currentPacket.numChunks++;
    bytesCurBlock += len;

    // If packet is full, enqueue it for transmission
    if (currentPacket.numChunks == currentPacket.maxChunks ||
        bytesCurBlock == blockSize) {
      if (DFSClient.LOG.isDebugEnabled()) {
        DFSClient.LOG.debug("writeChunk() packet full seqno=" +
            currentPacket.seqno +
            ", bytesCurBlock=" + bytesCurBlock +
            ", blockSize=" + blockSize);
      }
      waitAndQueueCurrentPacket();


      int psize = Math.min((int)(blockSize-bytesCurBlock), defaultPacketSize);
      computePacketChunkSize(psize, bytesPerChecksum);

      //
      // if a block if full, send an empty packet to
      // indicate the end of block .
      //
//      if (bytesCurBlock == blockSize) {
//        currentPacket = createPacket(0, 0, bytesCurBlock, currentSeqno++);
//        currentPacket.lastPacketInBlock = true;
//        currentPacket.syncBlock = shouldSyncBlock;
//        waitAndQueueCurrentPacket();
//        bytesCurBlock = 0;
//        lastFlushOffset = 0;
//      }
    }
  }


  /**
   * Waits till all existing data is flushed and confirmations 
   * received from datanodes. 
   */
  private void flushInternal() throws IOException {
    long toWaitFor;
    synchronized (this) {
       checkClosed();
      // If there is data in the current buffer, send it across
      queueCurrentPacket();
      toWaitFor = lastQueuedSeqno;
    }
    waitForAckedSeqno(toWaitFor);
  }

  private void waitForAckedSeqno(long seqno) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Waiting for ack for: " + seqno);
    }
     try {
      synchronized (dataQueue) {
        while (!closed) {
          checkClosed();
          if (lastAckedSeqno >= seqno) {
            break;
          }
          try {
            dataQueue.wait(1000); // when we receive an ack, we notify on dataQueue
          } catch (InterruptedException ie) {
            throw new InterruptedIOException(
                "Interrupted while waiting for data to be acknowledged.");
          }
        }
      }
      checkClosed();
    } catch (ClosedChannelException e) {
    }

  }

  private synchronized void start() {
    streamer.start();
  }



  // shutdown datastreamer and responseprocessor threads.
  // interrupt datastreamer if force is true
  private void closeThreads(boolean force) throws IOException {
    try {
      streamer.close(force);
      streamer.join();
      if (s != null) {
        s.close();
      }
    } catch (InterruptedException e) {
      throw new IOException("Failed to shutdown streamer");
    } finally {
      streamer = null;
      s = null;
      closed = true;
    }
  }

  /**
   * Closes this output stream and releases any system 
   * resources associated with this stream.
   */
  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      IOException e = lastException.getAndSet(null);
      if (e == null)
        return;
      else
        throw e;
    }

    try {
      flushBuffer();       // flush from all upper layers

      if (currentPacket != null) {
        waitAndQueueCurrentPacket();
      }

      if (bytesCurBlock != 0) {
        // send an empty packet to mark the end of the block
        currentPacket = createPacket(0, 0, bytesCurBlock, currentSeqno++);
        currentPacket.lastPacketInBlock = true;
        currentPacket.syncBlock = shouldSyncBlock;
      }

      flushInternal();             // flush all data to Datanodes

      closeThreads(false);
      //here: ackowledge ECWorker this write has been finished and ECWorker will acknowledge namenode when all writes are finished
      //todo
   //   completeFile(lastBlock);
     } catch (ClosedChannelException e) {
    } finally {
      closed = true;
    }
  }




  //
  // The DataStreamer class is responsible for sending data packets to a
  // remote datanode. Every packet has a sequence number associated with
  // it. When all the packets for a block are sent out and acks for each
  // if them are received, the DataStreamer closes the block.
  //
  class DataStreamer extends Daemon {
    private Log LOG = ECRemoteBlockWriter.LOG;
    private volatile boolean streamerClosed = false;
    private ExtendedBlock block; // its length is number of bytes acked
    private DataOutputStream blockStream;
    private DataInputStream blockReplyStream;
    private ResponseProcessor response = null;
    private volatile DatanodeInfo remoteDatanodeInfo = null;
    private volatile StorageType remoteStorageType = null;

    private long bytesSent = 0; // number of bytes that've been sent

    volatile boolean hasError = false;
    //  private final boolean isLazyPersistFile;



    private DataStreamer(ExtendedBlock block, DatanodeInfo remoteDatanodeInfo,
                         StorageType remoteStorageType) throws IOException {
      this.block = block;
      bytesSent = block.getNumBytes();
      //  isLazyPersistFile = isLazyPersist(stat);
      this.remoteDatanodeInfo = remoteDatanodeInfo;
      this.remoteStorageType = remoteStorageType;
    }


    /**
     * Initialize for data streaming
     */
    private void initDataStreaming() {
      this.setName("DataStreamer for block " + block);
      response = new ResponseProcessor(remoteDatanodeInfo);
      response.start();
    }

    private void endBlock() {
      if(LOG.isDebugEnabled()) {
        LOG.debug("Closing block " + block);
      }
      this.setName("DataStreamer for block " + block); //?
      closeResponder();
      closeStream();

    }

    /*
     * streamer thread is the only thread that opens streams to datanode,
     * and closes them. Any error recovery is also done by this thread.
     */
    @Override
    public void run() {

      createBlockOutputStream(remoteDatanodeInfo, remoteStorageType,
          block.getGenerationStamp());
      initDataStreaming();

      while (!streamerClosed) {

        // if the Responder encountered an error, shutdown Responder
        if (hasError) {
          try {
            if(response != null) {
              response.close();
              response.join();
              response = null;
            }

          } catch (InterruptedException  e) {
            LOG.warn("Caught exception ", e);
          }
          break;  //TODO: for simplity, if we find an error, just return;
        }

        Packet one;
        try {
          synchronized (dataQueue) {
            // wait for a packet to be sent.
            while ((!streamerClosed && !hasError && dataQueue.size() == 0 )) {
              long timeout = 1000; //todo : set this value
//              long timeout = dfsClient.getConf().socketTimeout/2 - (now-lastPacket);
//              timeout = timeout <= 0 ? 1000 : timeout;
//              timeout = (stage == BlockConstructionStage.DATA_STREAMING)?
//                  timeout : 1000;
              try {
                dataQueue.wait(timeout);
              } catch (InterruptedException  e) {
                LOG.warn("Caught exception ", e);
              }

            }
            if (streamerClosed || hasError ) { //seems will exit the first while loop
              continue;
            }
            // get packet to be sent.
            if (dataQueue.isEmpty()) {
              continue;
            } else {
              one = dataQueue.getFirst(); // regular data packet
            }
          }

          assert one != null;

          long lastByteOffsetInBlock = one.getLastByteOffsetBlock();
          if (lastByteOffsetInBlock > blockSize) {
            throw new IOException("BlockSize " + blockSize +
                " is smaller than data size. " +
                " Offset of packet in block " +
                lastByteOffsetInBlock );
          }

          if (one.lastPacketInBlock) {
            // wait for all data packets have been successfully acked
            synchronized (dataQueue) {
              while (!streamerClosed && !hasError &&
                  ackQueue.size() != 0) {
                try {
                  // wait for acks to arrive from remote datanode
                  dataQueue.wait(1000);
                } catch (InterruptedException  e) {
                  DFSClient.LOG.warn("Caught exception ", e);
                }
              }
            }
            if (streamerClosed || hasError ) {
              continue;
            }
          }
          // send the packet
          synchronized (dataQueue) {

            // move packet from dataQueue to ackQueue
            dataQueue.removeFirst();
            ackQueue.addLast(one);
            dataQueue.notifyAll();
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("DataStreamer block " + block +
                " sending packet " + one);
          }
          // write out data to remote datanode
          try {
            one.writeTo(blockStream);
            blockStream.flush();
          } catch (IOException e) {
            throw e;
          }

          // update bytesSent
          long tmpBytesSent = one.getLastByteOffsetBlock();
          if (bytesSent < tmpBytesSent) {
            bytesSent = tmpBytesSent;
          }

          if (streamerClosed || hasError) {
            continue;
          }

          // Is this block full?
          if (one.lastPacketInBlock) {
            // wait for the close packet has been acked
            synchronized (dataQueue) {
              while (!streamerClosed && !hasError &&
                  ackQueue.size() != 0 ) {
                dataQueue.wait(1000);// wait for acks to arrive from datanodes
              }
            }
            if (streamerClosed || hasError   ) {
              continue;
            }

            endBlock();
          }

        } catch (Throwable e) {

          if (e instanceof IOException) {
            setLastException((IOException)e);
          }
          hasError = true;
        }
      }

      closeInternal();
    }

    private void closeInternal() {
      closeResponder();       // close and join
      closeStream();
      streamerClosed = true;
      closed = true;
      synchronized (dataQueue) {
        dataQueue.notifyAll();
      }
    }

    /*
     * close both streamer and DFSOutputStream, should be called only
     * by an external thread and only after all data to be sent has
     * been flushed to datanode.
     *
     * Interrupt this data streamer if force is true
     *
     * @param force if this data stream is forced to be closed
     */
    void close(boolean force) {
      streamerClosed = true;
      synchronized (dataQueue) {
        dataQueue.notifyAll();
      }
      if (force) {
        this.interrupt();
      }
    }

    private void closeResponder() {
      if (response != null) {
        try {
          response.close();
          response.join();
        } catch (InterruptedException  e) {
          DFSClient.LOG.warn("Caught exception ", e);
        } finally {
          response = null;
        }
      }
    }

    private void closeStream() {
      if (blockStream != null) {
        try {
          blockStream.close();
        } catch (IOException e) {
          setLastException(e);
        } finally {
          blockStream = null;
        }
      }
      if (blockReplyStream != null) {
        try {
          blockReplyStream.close();
        } catch (IOException e) {
          setLastException(e);
        } finally {
          blockReplyStream = null;
        }
      }
      if (null != s) {
        try {
          s.close();
        } catch (IOException e) {
          setLastException(e);
        } finally {
          s = null;
        }
      }
    }

    //
    // Processes responses from the remote datanode.  A packet is removed
    // from the ackQueue when its response arrives.
    //
    private class ResponseProcessor extends Daemon {

      private volatile boolean responderClosed = false;
      private DatanodeInfo target = null;
      private boolean isLastPacketInBlock = false;

      ResponseProcessor (DatanodeInfo target) {
        this.target = target;
      }

      @Override
      public void run() {

        setName("ResponseProcessor for block " + block);
        PipelineAck ack = new PipelineAck();

        while (!responderClosed && !isLastPacketInBlock) {
          // process responses from datanodes.
          try {
            ack.readFields(blockReplyStream);
            long seqno = ack.getSeqno();
            // processes response status from datanodes.

            final DataTransferProtos.Status reply = ack.getReply(0);

            // node error
            if (reply != DataTransferProtos.Status.SUCCESS) {

              throw new IOException("Bad response " + reply +
                  " for block " + block +
                  " from datanode " +
                  target);
            }


            assert seqno != PipelineAck.UNKOWN_SEQNO :
                "Ack for unknown seqno should be a failed ack: " + ack;


            // a success ack for a data packet
            Packet one;
            synchronized (dataQueue) {
              one = ackQueue.getFirst();
            }
            if (one.seqno != seqno) {
              throw new IOException("ResponseProcessor: Expecting seqno " +
                  " for block " + block +
                  one.seqno + " but received " + seqno);
            }
            isLastPacketInBlock = one.lastPacketInBlock;


            // update bytesAcked
            block.setNumBytes(one.getLastByteOffsetBlock());

            synchronized (dataQueue) {
              lastAckedSeqno = seqno;
              ackQueue.removeFirst();
              dataQueue.notifyAll();

              one.releaseBuffer(byteArrayManager);
            }
          } catch (Exception e) {
            if (!responderClosed) {
              if (e instanceof IOException) {
                setLastException((IOException)e);
              }
              hasError = true;

              synchronized (dataQueue) {
                dataQueue.notifyAll();
              }
              LOG.warn("ResponseProcessor exception "
                  + " for block " + block, e);

              responderClosed = true;
            }
          }
        }
      }

      void close() {
        responderClosed = true;
        this.interrupt();
      }
    }


    // connects to the remote datanode
    // Returns true if success, otherwise return failure.
    //
    private boolean createBlockOutputStream(DatanodeInfo node,
                                            StorageType nodeStorageType, long newGS) {

      String firstBadLink = "";

      boolean result = false;
      try {
        DatanodeRegistration bpReg = dn.getDNRegistrationForBP(block.getBlockPoolId());
        s = createSocket(node);
        Token<BlockTokenIdentifier> accessToken = BlockTokenSecretManager.DUMMY_TOKEN;
        if (dn.isBlockTokenEnabled) {
          accessToken = dn.blockPoolTokenSecretManager.generateToken(block,
              EnumSet.of(BlockTokenSecretManager.AccessMode.WRITE));
        }
        long writeTimeout = dn.getDnConf().socketWriteTimeout +
            HdfsServerConstants.WRITE_TIMEOUT_EXTENSION;
        OutputStream unbufOut = NetUtils.getOutputStream(s, writeTimeout);
        InputStream unbufIn = NetUtils.getInputStream(s);
        DataEncryptionKeyFactory keyFactory =
            dn.getDataEncryptionKeyFactoryForBlock(block);
        IOStreamPair saslStreams = dn.saslClient.socketSend(s, unbufOut,
            unbufIn, keyFactory, accessToken, bpReg);
        unbufOut = saslStreams.out;
        unbufIn = saslStreams.in;


        blockStream = new DataOutputStream(new BufferedOutputStream(unbufOut,
            HdfsConstants.SMALL_BUFFER_SIZE));
        blockReplyStream = new DataInputStream(unbufIn);
        // We cannot change the block length in 'block' as it counts the number
        // of bytes ack'ed.
        ExtendedBlock blockCopy = new ExtendedBlock(block);
        blockCopy.setNumBytes(blockSize);
        // send the request
        new Sender(blockStream).writeBlock(blockCopy, nodeStorageType, accessToken,
            NetUtils.getHostNameOfIP(dn.getDisplayName()), new DatanodeInfo[]{node},
            new StorageType[]{nodeStorageType}, null, BlockConstructionStage.PIPELINE_SETUP_CREATE,
            1, 0L, blockSize, newGS, checksum4WriteBlock, cachingStrategy.get(), false);  //isLazyPersistFile...

        // receive ack for connect
        DataTransferProtos.BlockOpResponseProto resp = DataTransferProtos.BlockOpResponseProto.parseFrom(
            PBHelper.vintPrefixed(blockReplyStream));
        DataTransferProtos.Status pipelineStatus = resp.getStatus();
        firstBadLink = resp.getFirstBadLink();

        if (pipelineStatus != DataTransferProtos.Status.SUCCESS) {
          if (pipelineStatus == DataTransferProtos.Status.ERROR_ACCESS_TOKEN) {
            throw new InvalidBlockTokenException(
                "Got access token error for connect ack with firstBadLink as "
                    + firstBadLink);
          } else {
            throw new IOException("Bad connect ack with firstBadLink as "
                + firstBadLink);
          }
        }

        result =  true; // success
        hasError = false;
      } catch (IOException ie) {
        LOG.info("Exception in createBlockOutputStream", ie);
        hasError = true;
        setLastException(ie);
        result =  false;  // error
      } finally {
        if (!result) {
          IOUtils.closeSocket(s);
          s = null;
          IOUtils.closeStream(blockStream);
          blockStream = null;
          IOUtils.closeStream(blockReplyStream);
          blockReplyStream = null;
        }
      }
      return result;

    }

    private void setLastException(IOException e) {
      lastException.compareAndSet(null, e);
    }
  }

  private static class Packet {
    final long seqno; // sequencenumber of buffer in block
    final long offsetInBlock; // offset in block
    boolean syncBlock; // this packet forces the current block to disk
    int numChunks; // number of chunks currently in packet
    final int maxChunks; // max chunks in packet
    private byte[] buf;
    private boolean lastPacketInBlock; // is this the last packet in block?
    int checksumStart;
    int checksumPos;
    final int dataStart;
    int dataPos;

    /**
     * Create a new packet.
     *
     *
     * @param chunksPerPkt maximum number of chunks per packet.
     * @param offsetInBlock offset in bytes into the HDFS block.
     */
    private Packet(byte[] buf, int chunksPerPkt, long offsetInBlock, long seqno,
                   int checksumSize) {
      this.lastPacketInBlock = false;
      this.numChunks = 0;
      this.offsetInBlock = offsetInBlock;
      this.seqno = seqno;

      this.buf = buf;

      checksumStart = PacketHeader.PKT_MAX_HEADER_LEN;
      checksumPos = checksumStart;
      dataStart = checksumStart + (chunksPerPkt * checksumSize);
      dataPos = dataStart;
      maxChunks = chunksPerPkt;
    }

    void writeData(byte[] inarray, int off, int len) {
      if (dataPos + len > buf.length) {
        throw new BufferOverflowException();
      }
      System.arraycopy(inarray, off, buf, dataPos, len);
      dataPos += len;
    }

    void writeChecksum(byte[] inarray, int off, int len) {
      if (len == 0) {
        return;
      }
      if (checksumPos + len > dataStart) {
        throw new BufferOverflowException();
      }
      System.arraycopy(inarray, off, buf, checksumPos, len);
      checksumPos += len;
    }

    /**
     * Write the full packet, including the header, to the given output stream.
     */
    void writeTo(DataOutputStream stm) throws IOException {
      final int dataLen = dataPos - dataStart;
      final int checksumLen = checksumPos - checksumStart;
      final int pktLen = HdfsConstants.BYTES_IN_INTEGER + dataLen + checksumLen;

      PacketHeader header = new PacketHeader(
          pktLen, offsetInBlock, seqno, lastPacketInBlock, dataLen, syncBlock);

      if (checksumPos != dataStart) {
        // Move the checksum to cover the gap. This can happen for the last
        // packet or during an hflush/hsync call.
        System.arraycopy(buf, checksumStart, buf,
            dataStart - checksumLen , checksumLen);
        checksumPos = dataStart;
        checksumStart = checksumPos - checksumLen;
      }

      final int headerStart = checksumStart - header.getSerializedSize();
      assert checksumStart + 1 >= header.getSerializedSize();
      assert checksumPos == dataStart;
      assert headerStart >= 0;
      assert headerStart + header.getSerializedSize() == checksumStart;

      // Copy the header data into the buffer immediately preceding the checksum
      // data.
      System.arraycopy(header.getBytes(), 0, buf, headerStart,
          header.getSerializedSize());

      // corrupt the data for testing.
      if (DFSClientFaultInjector.get().corruptPacket()) {
        buf[headerStart+header.getSerializedSize() + checksumLen + dataLen-1] ^= 0xff;
      }

      // Write the now contiguous full packet to the output stream.
      stm.write(buf, headerStart, header.getSerializedSize() + checksumLen + dataLen);

      // undo corruption.
      if (DFSClientFaultInjector.get().uncorruptPacket()) {
        buf[headerStart+header.getSerializedSize() + checksumLen + dataLen-1] ^= 0xff;
      }
    }

    private void releaseBuffer(ByteArrayManager bam) {
      bam.release(buf);
      buf = null;
    }

    // get the packet's last byte's offset in the block
    long getLastByteOffsetBlock() {
      return offsetInBlock + dataPos - dataStart;
    }

    @Override
    public String toString() {
      return "packet seqno:" + this.seqno +
          " offsetInBlock:" + this.offsetInBlock +
          " lastPacketInBlock:" + this.lastPacketInBlock +
          " lastByteOffsetInBlock: " + this.getLastByteOffsetBlock();
    }
  }



}

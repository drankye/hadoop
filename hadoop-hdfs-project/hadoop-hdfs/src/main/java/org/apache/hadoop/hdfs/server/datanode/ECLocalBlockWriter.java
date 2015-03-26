package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSOutputSummer;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.StorageType;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.ReplicaOutputStreams;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.nativeio.NativeIO;
import org.apache.hadoop.util.DataChecksum;
import org.apache.hadoop.util.Time;
import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

/**
 * This class is used by datanode to write a block to its local.
 * When ECWorker generates a block(parity block or recovery block),
 * the block may be placed locally.
 */

public class ECLocalBlockWriter extends FSOutputSummer {

  public static final Log LOG = LogFactory.getLog(ECLocalBlockWriter.class);;
  static long CACHE_DROP_LAG_BYTES = 8 * 1024 * 1024;
  private final long datanodeSlowLogThresholdMs;

  /** to block file at local disk */
  private OutputStream out = null;
  private FileDescriptor outFd;
  /** to crc file at local disk */
  private DataOutputStream checksumOut = null;
  /** checksum we write to disk */
  private DataChecksum diskChecksum;


  /** block size, default is 128M */
  private long blockSize;
  /** number of bytes that've been written to local */
  private long bytesWritten= 0;
  /** number of bytes of partial chunk stored in super class's buffer */
  private int flushedButKeepInBufferBytes = 0;

  private ReplicaOutputStreams streams;

  /** the datanode this writer works for */
  private final DataNode dn;

  /** the block to write */
  private final ExtendedBlock block;
  /** the replica to write */
  private final ReplicaInPipelineInterface replicaInfo ;

  /** if this writer has been closed */
  private boolean closed;
  private boolean syncOnClose;

  // Cache management state
  private boolean dropCacheBehindWrites;
  private long lastCacheManagementOffset = 0;
  private boolean syncBehindWrites;
  private boolean syncBehindWritesInBackground;


  public static ECLocalBlockWriter newInstance(final ExtendedBlock block, final StorageType storageType,
                            final DataNode datanode, DataChecksum requestedChecksum,
                            CachingStrategy cachingStrategy)throws IOException{

    final ECLocalBlockWriter writer = new ECLocalBlockWriter(block, storageType,
          datanode, requestedChecksum,cachingStrategy);
    return writer;
  }
  public static ECLocalBlockWriter newInstance(final ExtendedBlock block, final StorageType storageType,
                                               final DataNode datanode)throws IOException{
    DataChecksum requestedChecksum = DataChecksum.newDataChecksum(DataChecksum.Type.CRC32,512);
    CachingStrategy cachingStrategy = CachingStrategy.newDefaultStrategy();
    final ECLocalBlockWriter writer = new ECLocalBlockWriter(block, storageType,
        datanode, requestedChecksum,cachingStrategy);
    return writer;
  }
  private ECLocalBlockWriter(final ExtendedBlock block, final StorageType storageType,
                     final DataNode datanode, DataChecksum requestedChecksum,
                     CachingStrategy cachingStrategy) throws IOException {
    super(requestedChecksum);
    try{

      this.block = block;

      this.dn = datanode;

      this.syncOnClose = datanode.getDnConf().syncOnClose;
      this.closed = false;
      this.datanodeSlowLogThresholdMs = datanode.getDnConf().datanodeSlowIoWarningThresholdMs;
      this.blockSize = datanode.getConf().getLong(DFSConfigKeys.DFS_BLOCK_SIZE_KEY,
          DFSConfigKeys.DFS_BLOCK_SIZE_DEFAULT);
      replicaInfo = datanode.data.createTemporary(storageType, block);


      this.dropCacheBehindWrites = (cachingStrategy.getDropBehind() == null) ?
          datanode.getDnConf().dropCacheBehindWrites :
          cachingStrategy.getDropBehind();
      this.syncBehindWrites = datanode.getDnConf().syncBehindWrites;
      this.syncBehindWritesInBackground = datanode.getDnConf().
          syncBehindWritesInBackground;

      streams = replicaInfo.createStreams(true, requestedChecksum);
      assert streams != null : "null streams!";

      this.diskChecksum = streams.getChecksum();



      this.out = streams.getDataOut();
      if (out instanceof FileOutputStream) {
        this.outFd = ((FileOutputStream)out).getFD();
      } else {
        LOG.warn("Could not get file descriptor for outputstream of class " +
            out.getClass());
      }
      this.checksumOut = new DataOutputStream(new BufferedOutputStream(
          streams.getChecksumOut(), HdfsConstants.SMALL_BUFFER_SIZE));
      // write data chunk header if creating a new replica
      BlockMetadataHeader.writeHeader(checksumOut, diskChecksum);


    } catch (ReplicaAlreadyExistsException bae) {
      throw bae;
    } catch(IOException ioe) {
      IOUtils.closeStream(this);
      cleanupBlock();

      // check if there is a disk error
      IOException cause = DatanodeUtil.getCauseIfDiskError(ioe);
      DataNode.LOG.warn("IOException in ECLocalBlokWriter constructor. Cause is ",
          cause);

      if (cause != null) { // possible disk error
        ioe = cause;
        datanode.checkDiskErrorAsync();
      }

      throw ioe;
    }
  }

  @Override
  protected void checkClosed() throws IOException
  {
    if (closed) {
      throw new ClosedChannelException();
    }
  }


  /**
   * close files.
   */
  @Override
  public void close() throws IOException {

    closed = true;

    IOException ioe = null;
    if (syncOnClose && (out != null || checksumOut != null)) {
      dn.metrics.incrFsyncCount();
    }
    long flushTotalNanos = 0;
    boolean measuredFlushTime = false;
    // close checksum file
    try {
      if (checksumOut != null) {
        long flushStartNanos = System.nanoTime();
        checksumOut.flush();
        long flushEndNanos = System.nanoTime();
        if (syncOnClose) {
          long fsyncStartNanos = flushEndNanos;
          streams.syncChecksumOut();
          dn.metrics.addFsyncNanos(System.nanoTime() - fsyncStartNanos);
        }
        flushTotalNanos += flushEndNanos - flushStartNanos;
        measuredFlushTime = true;
        checksumOut.close();
        checksumOut = null;
      }
    } catch(IOException e) {
      ioe = e;
    } finally {
      IOUtils.closeStream(checksumOut);
    }
    // close block file
    try {
      if (out != null) {
        long flushStartNanos = System.nanoTime();
        out.flush();
        long flushEndNanos = System.nanoTime();
        if (syncOnClose) {
          long fsyncStartNanos = flushEndNanos;
          streams.syncDataOut();
          dn.metrics.addFsyncNanos(System.nanoTime() - fsyncStartNanos);
        }
        flushTotalNanos += flushEndNanos - flushStartNanos;
        measuredFlushTime = true;
        out.close();
        out = null;
      }
    } catch (IOException e) {
      ioe = e;
    } finally{
      IOUtils.closeStream(out);
    }
    if (measuredFlushTime) {
      dn.metrics.addFlushNanos(flushTotalNanos);
    }
    // disk check
    if(ioe != null) {
      dn.checkDiskErrorAsync();
      throw ioe;
    }

    block.setNumBytes(replicaInfo.getNumBytes());
    // Finalize the block.
    dn.data.finalizeBlock(block);
    dn.metrics.incrBlocksWritten();
  }


  /**
   * We flush partial chunk, but keep the partial chunk in buffer.
   * @throws IOException
   */
  @Override
  protected synchronized void flushBuffer() throws IOException {
    flushedButKeepInBufferBytes = flushBuffer(true, true);
  }

  /**
   * If there is a trailing partial chunk, it is flushed and is
   * maintained in the buffer.
   */
  @Override
  public void flush() throws IOException {
    flushedButKeepInBufferBytes = flushBuffer(true, true);
  }

  /**
   * handle the previous partial chunk.
   */
  private void handlePartialChunk() throws IOException {
    if(flushedButKeepInBufferBytes == 0)
      return;

    if (out != null) {
      out.flush();
    }
    if (checksumOut != null) {
      checksumOut.flush();
    }

    // rollback the position of the meta file
    FileChannel checksumChannel = ((FileOutputStream)streams.getChecksumOut()).getChannel();
    long checksumOldPos = checksumChannel.position();
    long checksumNewPos = checksumOldPos - diskChecksum.getChecksumSize();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Changing crc meta file offset of block " + block + " from " +
          checksumOldPos + " to " + checksumNewPos);
    }
    checksumChannel.position(checksumNewPos);

    // rollback the position of the data file
    FileChannel dataChannel = ((FileOutputStream)streams.getDataOut()).getChannel();
    long dataOldPos = dataChannel.position();
    long dataNewPos = dataOldPos - flushedButKeepInBufferBytes;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Changing data meta file offset of block " + block + " from " +
          dataOldPos + " to " + dataNewPos);
    }
    dataChannel.position(dataNewPos);

    flushedButKeepInBufferBytes = 0;
  }


  @Override
  protected void writeChunk(byte[] b, int offset, int len,
                            byte[] checksum, int ckoff, int cklen)
                            throws  IOException
  {
    checkClosed();
    handlePartialChunk();


    // Sanity check the header
    if (bytesWritten + len > blockSize) {
      throw new IOException("The block will be overflowed. bytesWritten: "
          +bytesWritten+ ", len: " + len + ", block size: " + blockSize);
    }

    // update received bytes
    bytesWritten += len;
    if (replicaInfo.getNumBytes() < bytesWritten) {
      replicaInfo.setNumBytes(bytesWritten);
    }


      final boolean shouldNotWriteChecksum = cklen == 0
          && streams.isTransientStorage();
      try {
          // Write data to disk.
          long begin = Time.monotonicNow();
          out.write(b, offset, len);
          long duration = Time.monotonicNow() - begin;
          if (duration > datanodeSlowLogThresholdMs) {
            LOG.warn("Slow write data to disk cost:" + duration
                + "ms (threshold=" + datanodeSlowLogThresholdMs + "ms)");
          }

          if (!shouldNotWriteChecksum) {
            checksumOut.write(checksum, ckoff, cklen);
          }

          dn.metrics.incrBytesWritten(len);

 //       manageWriterOsCache(offsetInBlock);
      } catch (IOException iex) {
        dn.checkDiskErrorAsync();
        throw iex;
      }

   }



  private void manageWriterOsCache(long offsetInBlock) {
    try {
      if (outFd != null &&
          offsetInBlock > lastCacheManagementOffset + CACHE_DROP_LAG_BYTES) {
        long begin = Time.monotonicNow();
        //
        // For SYNC_FILE_RANGE_WRITE, we want to sync from
        // lastCacheManagementOffset to a position "two windows ago"
        //
        //                         <========= sync ===========>
        // +-----------------------O--------------------------X
        // start                  last                      curPos
        // of file
        //
        if (syncBehindWrites) {
          if (syncBehindWritesInBackground) {
            this.dn.getFSDataset().submitBackgroundSyncFileRangeRequest(
                block, outFd, lastCacheManagementOffset,
                offsetInBlock - lastCacheManagementOffset,
                NativeIO.POSIX.SYNC_FILE_RANGE_WRITE);
          } else {
            NativeIO.POSIX.syncFileRangeIfPossible(outFd,
                lastCacheManagementOffset, offsetInBlock
                    - lastCacheManagementOffset,
                NativeIO.POSIX.SYNC_FILE_RANGE_WRITE);
          }
        }
        //
        // For POSIX_FADV_DONTNEED, we want to drop from the beginning
        // of the file to a position prior to the current position.
        //
        // <=== drop =====>
        //                 <---W--->
        // +--------------+--------O--------------------------X
        // start        dropPos   last                      curPos
        // of file
        //
        long dropPos = lastCacheManagementOffset - CACHE_DROP_LAG_BYTES;
        if (dropPos > 0 && dropCacheBehindWrites) {
          NativeIO.POSIX.getCacheManipulator().posixFadviseIfPossible(
              block.getBlockName(), outFd, 0, dropPos,
              NativeIO.POSIX.POSIX_FADV_DONTNEED);
        }
        lastCacheManagementOffset = offsetInBlock;
        long duration = Time.monotonicNow() - begin;
        if (duration > datanodeSlowLogThresholdMs) {
          LOG.warn("Slow manageWriterOsCache took " + duration
              + "ms (threshold=" + datanodeSlowLogThresholdMs + "ms)");
        }
      }
    } catch (Throwable t) {
      LOG.warn("Error managing cache for writer of block " + block, t);
    }
  }

  /**
   * Cleanup a partial block
   */
  private void cleanupBlock() throws IOException {
      dn.data.unfinalizeBlock(block);
  }




}

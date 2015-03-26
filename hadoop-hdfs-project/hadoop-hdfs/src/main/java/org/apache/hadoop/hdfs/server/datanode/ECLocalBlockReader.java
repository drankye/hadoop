package org.apache.hadoop.hdfs.server.datanode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.LengthInputStream;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.util.DataChecksum;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Read a block from  the local of this datanode.
 *
 */
public class ECLocalBlockReader extends ECBlockReaderBase{

  static final Log LOG = LogFactory.getLog(ECLocalBlockReader.class);
  private InputStream blockIn;
  private DataInputStream checksumIn;
  private int maxChunksPerPacket;
  protected ByteBuffer curChecksumBuffer = null;
  private long blockOffset;
  private ByteBuffer curPacketBuf;
  CachingStrategy cachingStrategy;

  public ECLocalBlockReader(DataNode dn, ExtendedBlock block) {
    this(dn, block, 0, block.getNumBytes(), true);
  }
  public ECLocalBlockReader(DataNode dn, ExtendedBlock block, long startOffset,
                            long bytesToRead, boolean verifyChecksum) {
    super(dn, block, startOffset, bytesToRead, verifyChecksum);
  }

  @Override
  public void connect() throws IOException {
    try{
    final Replica replica;
    final long replicaVisibleLength;
    synchronized (dn.data) {
      replica = getReplica(block, dn);
      replicaVisibleLength = replica.getVisibleLength();
    }
    // if there is a write in progress
    if (replica instanceof ReplicaBeingWritten) {
      final ReplicaBeingWritten rbw = (ReplicaBeingWritten) replica;
      if (rbw.getBytesOnDisk() < startOffset + userSpecifiedBytesToRead )
        throw new IOException("Replica" + replica + "is being written and the length is not qualified.");
    }

    if (replica.getGenerationStamp() < block.getGenerationStamp()) {
      throw new IOException("Replica gen stamp < block genstamp, block="
          + block + ", replica=" + replica);
    }
    if (replicaVisibleLength < 0) {
      throw new IOException("Replica is not readable, block="
          + block + ", replica=" + replica);
    }
    if (DataNode.LOG.isDebugEnabled()) {
      DataNode.LOG.debug("block=" + block + ", replica=" + replica);
    }

    cachingStrategy = new CachingStrategy(true, dn.getDnConf().readaheadLength);

    DataChecksum csum = null;
    if (verifyChecksum) {
      LengthInputStream metaIn = null;
      boolean keepMetaInOpen = false;
      try {
        metaIn = dn.data.getMetaDataInputStream(block);
        if (metaIn == null) {
          //need checksum but meta-data not found
          throw new FileNotFoundException("Meta-data not found for " + block);
        }

          // The meta file will contain only the header if the NULL checksum
          // type was used, or if the replica was written to transient storage.
          // Checksum verification is not performed for replicas on transient
          // storage.  The header is important for determining the checksum
          // type later when lazy persistence copies the block to non-transient
          // storage and computes the checksum.
          if (metaIn.getLength() > BlockMetadataHeader.getHeaderSize()) {
            checksumIn = new DataInputStream(new BufferedInputStream(
                metaIn, HdfsConstants.IO_FILE_BUFFER_SIZE));

            csum = BlockMetadataHeader.readDataChecksum(checksumIn, block);
            keepMetaInOpen = true;
          }
        } finally {
        if (!keepMetaInOpen) {
          IOUtils.closeStream(metaIn);
        }
      }
    }

    if (csum == null) {
      // The number of bytes per checksum here determines the alignment
      // of reads: we always start reading at a checksum chunk boundary,
      // even if the checksum type is NULL. So, choosing too big of a value
      // would risk sending too much unnecessary data. 512 (1 disk sector)
      // is likely to result in minimal extra IO.
      csum = DataChecksum.newDataChecksum(DataChecksum.Type.NULL, 512);
    }

     /*
       * If chunkSize is very large, then the metadata file is mostly corrupted.
       */
    int size = csum.getBytesPerChecksum();
    if (size > 10 * 1024 * 1024 && size > replicaVisibleLength) {
      throw new IOException("Metadata file seems to be corrupted due to chunkSize = "
              + size + ", block = "+block);

    }
    checksum = csum;
    int chunkSize = checksum.getBytesPerChecksum();



    // Ensure read offset is position at the beginning of chunk
    firstChunkOffset = startOffset - (startOffset % chunkSize);
    blockOffset = firstChunkOffset;
    bytesNeededToFinish = userSpecifiedBytesToRead + (startOffset - firstChunkOffset);
    if( firstChunkOffset + bytesNeededToFinish > replicaVisibleLength){
      bytesNeededToFinish = replicaVisibleLength - firstChunkOffset;
    }

    if(checksumIn != null) {
      long checksumSkip = (firstChunkOffset / chunkSize) * checksum.getChecksumSize();
      // note blockInStream is seeked when created below
      if (checksumSkip > 0) {
        // Should we use seek() for checksum file as well?
        IOUtils.skipFully(checksumIn, checksumSkip);
      }
    }
      blockIn = dn.data.getBlockInputStream(block, firstChunkOffset); // seek to firstChunkOffset

      maxChunksPerPacket = Math.max(1,
          (HdfsConstants.IO_FILE_BUFFER_SIZE + chunkSize - 1)/chunkSize);

      int bufSize = maxChunksPerPacket * chunkSize;
      curPacketBuf = ByteBuffer.allocate(bufSize);
      curPacketBuf.limit(0);
      curDataSlice =curPacketBuf.slice();
      curPacketBuf.limit(bufSize);

      if(verifyChecksum && checksumIn != null
          && checksum.getChecksumSize() > 0)
        curChecksumBuffer = ByteBuffer.allocate(bufSize / chunkSize
            * checksum.getChecksumSize());

    }catch (IOException ioe) {
       try{
            close();
       } catch(IOException e){
            LOG.warn("Error in close(): ", e);
       }
       throw ioe;
     }
  }

  @Override
  protected void readNextPacket() throws IOException {
    curPacketBuf.clear();
    if(curChecksumBuffer != null)
        curChecksumBuffer.clear();
    if(bytesNeededToFinish <=0)
      return;

    int dataLen = (int) Math.min(bytesNeededToFinish, curPacketBuf.remaining());
    IOUtils.readFully(blockIn, curPacketBuf.array(),
        curPacketBuf.arrayOffset() + curPacketBuf.position(),
        dataLen);
    curPacketBuf.limit(curPacketBuf.position() + dataLen);
    curDataSlice = curPacketBuf.slice();
    curPacketBuf.position(curPacketBuf.position() + dataLen);


    if(verifyChecksum && checksumIn != null && curChecksumBuffer != null
                      && checksum.getChecksumSize() > 0){
      int checksumLen = (dataLen + checksum.getBytesPerChecksum() -1)
              /checksum.getBytesPerChecksum() * checksum.getChecksumSize();

      checksumIn.readFully(curChecksumBuffer.array(),
          curChecksumBuffer.arrayOffset() + curChecksumBuffer.position(),
          checksumLen);
       curChecksumBuffer.limit(curChecksumBuffer.position() + checksumLen);

      checksum.verifyChunkedSums(curDataSlice, curChecksumBuffer, "", blockOffset);
    }

    bytesNeededToFinish -= dataLen;
    blockOffset += dataLen;


  }

  private static Replica getReplica(ExtendedBlock block, DataNode datanode)
      throws ReplicaNotFoundException {
    Replica replica = datanode.data.getReplica(block.getBlockPoolId(),
        block.getBlockId());
    if (replica == null) {
      throw new ReplicaNotFoundException(block);
    }
    return replica;
  }


  /**
   * close opened files.
   */
  @Override
  public void close() throws IOException {
    super.close();
    IOException ioe = null;
    if(checksumIn!=null) {
      try {
        checksumIn.close(); // close checksum file
      } catch (IOException e) {
        ioe = e;
      }
      checksumIn = null;
    }
    if(blockIn!=null) {
      try {
        blockIn.close(); // close data file
      } catch (IOException e) {
        ioe = e;
      }
      blockIn = null;
    }
    // throw IOException if there is any
    if(ioe!= null) {
      throw ioe;
    }
  }

}

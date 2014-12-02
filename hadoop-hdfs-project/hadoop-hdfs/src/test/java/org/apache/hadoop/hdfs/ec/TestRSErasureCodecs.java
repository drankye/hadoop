package org.apache.hadoop.hdfs.ec;

import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureDecoder;
import org.apache.hadoop.hdfs.ec.coder.ErasureEncoder;
import org.apache.hadoop.hdfs.ec.grouper.BlockGrouper;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.server.datanode.DataNodeTestUtils;
import org.apache.hadoop.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static org.junit.Assert.assertTrue;

public class TestRSErasureCodecs extends TestErasureCodecs {


    @Test
    public void testCodec() throws Exception {
        final int DATA_SIZE = 10;
        final int PARITY_SIZE = 4;
        getConf().set(EC_CONF_PREFIX + "RS-Java", "org.apache.hadoop.hdfs.ec.codec.JavaRSErasureCodec");
        ECSchema schema = TestUtils.makeRSSchema("RS-Java", DATA_SIZE, PARITY_SIZE, getConf(), SCHEMA_FILE);
        testCodec(schema);
    }

    @Override
    protected void encode(ECSchema schema, BlockGroup blockGroup) throws Exception {
        assertTrue(blockGroup.getSchemaName().equals(schema.getSchemaName()));

        ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
        ErasureEncoder encoder = codec.createEncoder();
        BlockGrouper grouper = codec.createBlockGrouper();
        
        ECBlock[] dataEcBlocks = blockGroup.getSubGroups().get(0).getDataBlocks();
        ECChunk[] dataChunks = getChunks(dataEcBlocks);
        ECChunk[] parityChunks = new ECChunk[grouper.getParityBlocks()];
        for (int i = 0; i < parityChunks.length; i++) {
            parityChunks[i] = new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]));
        }
        
        encoder.encode(dataChunks, parityChunks);

        //write
        ECBlock[] parityBlocks = blockGroup.getSubGroups().get(0).getParityBlocks();
        for (int i = 0; i < parityChunks.length; i++) {
            ECBlock ecBlock = parityBlocks[i];
            File blockFile = getBlockFile(ecBlock);
            ByteBuffer byteBuffer = parityChunks[i].getChunkBuffer();

            RandomAccessFile raf = new RandomAccessFile(blockFile, "rw");
            FileChannel fc = raf.getChannel();
            IOUtils.writeFully(fc, byteBuffer);
        }
    }

    @Override
    protected ECChunk decode(ECSchema schema, BlockGroup blockGroup) throws Exception {
        ECBlock[] dataEcBlocks = blockGroup.getSubGroups().get(0).getDataBlocks();
        ECBlock[] parityEcBlocks = blockGroup.getSubGroups().get(0).getParityBlocks();
        ECChunk[] dataChunks = getChunks(dataEcBlocks);
        ECChunk[] parityChunks = getChunks(parityEcBlocks);
        ECChunk outputChunk = new ECChunk(ByteBuffer.wrap(new byte[CHUNK_SIZE]));

        ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
        ErasureDecoder decoder = codec.createDecoder();
        decoder.decode(dataChunks, parityChunks, outputChunk);
        return outputChunk;
    }

    private ECChunk[] getChunks(ECBlock[] dataEcBlocks) throws IOException {
        ECChunk[] chunks = new ECChunk[dataEcBlocks.length];
        for (int i = 0; i < dataEcBlocks.length; i++) {
            ECBlock ecBlock = dataEcBlocks[i];
            File blockFile = getBlockFile(ecBlock);
            byte[] buffer = new byte[BLOCK_SIZE];
            IOUtils.readFully(new FileInputStream(blockFile), buffer, 0, CHUNK_SIZE);
            chunks[i] = new ECChunk(ByteBuffer.wrap(buffer), ecBlock.isMissing());
        }
        return chunks;
    }

    private File getBlockFile(ECBlock ecBlock) throws IOException {
        Block block = DataNodeTestUtils.getFSDataset(getDataNode()).getStoredBlock(ecBlock.getBlockPoolId(), ecBlock.getBlockId());
        File blockFile = DataNodeTestUtils.getBlockFile(getDataNode(), ecBlock.getBlockPoolId(), block);
        return blockFile;
    }
}

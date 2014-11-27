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
package org.apache.hadoop.hdfs.ec;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.ExtendedBlockId;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.JavaRSErasureCoder;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.DataNodeTestUtils;
import org.apache.hadoop.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

public class TestErasureCodes {
	final Random RAND = new Random();
	
	private static final String TEST_DIR = new File(System.getProperty(
			"test.build.data", "/Users")).getAbsolutePath();
	private final static String DATA_FILE = new File(TEST_DIR, "data").getAbsolutePath();
	private final static String PARITY_FILE = new File(TEST_DIR, "parity").getAbsolutePath();
	
	private Configuration conf;
	private FileSystem fileSys;
	private DataNode dataNode;
	
	private static final int BLOCK_SIZE = 1024;
	private static final int BUFFER_SIZE = BLOCK_SIZE;
	private static final int DATA_SIZE = 10;
	private static final int PARITY_SIZE = 4;
	
	private ByteBuffer[] message = new ByteBuffer[DATA_SIZE];
	
	@Before
	public void init() throws IOException {
		int numDataNodes = 1;
		conf = new HdfsConfiguration();
		conf.setInt(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, BLOCK_SIZE);
		MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDataNodes).build();
		cluster.waitActive();
		fileSys = cluster.getFileSystem();
		dataNode = cluster.getDataNodes().get(0);
	}
	
	@Test
	public void verifyEncodeDecode() throws Exception {
		ECSchema schema = TestSchemaLoader.loadRSJavaSchema(DATA_SIZE, PARITY_SIZE);
		ErasureCodec codec = ErasureCodec.createErasureCodec(schema);
		ErasureCoder ec = codec.createErasureCoder();
		int erasedLocation = RAND.nextInt(DATA_SIZE);
		
		//write wrong message and correct parity to blocks
		List<LocatedBlock> dataBlocks = write(DATA_SIZE, ((JavaRSErasureCoder)ec).symbolSize(), erasedLocation);
		assertTrue(dataBlocks.size() == DATA_SIZE);
		List<LocatedBlock> parityBlocks = encode(ec);
		assertTrue(parityBlocks.size() == PARITY_SIZE);
		
		//make block group
		List<ExtendedBlockId> dataBlockIds = getBlockIds(dataBlocks);
		List<ExtendedBlockId> parityBlockIds = getBlockIds(parityBlocks);
		BlockGroup blockGroup = codec.createBlockGrouper().makeBlockGroup(dataBlockIds, parityBlockIds);
		
		//decode
		BlockGroup groupUseToRepairData = codec.createBlockGrouper().makeRecoverableGroup(blockGroup);
		groupUseToRepairData.setAnnotation(generateAnnotation(erasedLocation));
		ECChunk repairedData = decode(ec, groupUseToRepairData);
		ByteBuffer copy = message[erasedLocation];
		assertTrue(copy.equals(repairedData.getChunkBuffer()));
	}

	private List<LocatedBlock> write(int blockCount, int symbolMax, int erasedLocation) throws IOException {
		//create
		DFSTestUtil.createFile(fileSys, new Path(DATA_FILE), 0, (short)1, 0);
		//write
		for (int i = 0; i < blockCount; i++) {
			byte[] byteArray = new byte[BLOCK_SIZE];
			for (int j = 0; j < BLOCK_SIZE; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = ByteBuffer.wrap(byteArray);
			if (i == erasedLocation) {
				byte[] wrongMessage = new byte[BLOCK_SIZE];
				DFSTestUtil.appendFile(fileSys, new Path(DATA_FILE), wrongMessage);
			} else {
				DFSTestUtil.appendFile(fileSys, new Path(DATA_FILE), byteArray);
			}
		}
		List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(DATA_FILE));
		
		return blocks;
	}
	
	private List<LocatedBlock> encode(ErasureCoder ec) throws IllegalArgumentException, IOException {
		//encode
		ECChunk[] messageChunks = new ECChunk[DATA_SIZE];
		for (int i = 0; i < DATA_SIZE; i++) {
			messageChunks[i] = new ECChunk(message[i]);
		}
		ECChunk[] parityChunks = new ECChunk[PARITY_SIZE];
		for (int i = 0; i < PARITY_SIZE; i++) {
			parityChunks[i] = new ECChunk(ByteBuffer.wrap(new byte[BUFFER_SIZE]));
		}
		ec.encode(messageChunks, parityChunks);
		//write
		DFSTestUtil.createFile(fileSys, new Path(PARITY_FILE), 0, (short)1, 0);
		for (int i = 0; i < PARITY_SIZE; i++) {
			byte[] byteArray = parityChunks[i].getChunkBuffer().array();
			DFSTestUtil.appendFile(fileSys, new Path(PARITY_FILE), byteArray);
		}
		List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(PARITY_FILE));
		return blocks;
	}
	
	private List<ExtendedBlockId> getBlockIds(List<LocatedBlock> dataBlocks) {
		List<ExtendedBlockId> ids = new ArrayList<>();
		for (LocatedBlock block : dataBlocks) {
			ids.add(ExtendedBlockId.fromExtendedBlock(block.getBlock()));
		}
		return ids;
	}
	
	private ECChunk decode(ErasureCoder ec, BlockGroup groupUseToRepairData) throws IOException {
		ECBlock[] dataEcBlocks = groupUseToRepairData.getSubGroups().get(0).getDataBlocks();
		ECBlock[] parityEcBlocks = groupUseToRepairData.getSubGroups().get(0).getDataBlocks();
		ECChunk[] dataChunks = getChunks(dataEcBlocks);
		ECChunk[] parityChunks = getChunks(parityEcBlocks);
		
		ECChunk outputChunk = new ECChunk(ByteBuffer.wrap(new byte[BUFFER_SIZE])); 
		ec.decode(dataChunks, parityChunks, groupUseToRepairData.getAnnotation(), outputChunk);
		return outputChunk;
	}

	private ECChunk[] getChunks(ECBlock[] dataEcBlocks) throws IOException {
		ECChunk[] chunks = new ECChunk[dataEcBlocks.length];
		for (int i = 0; i < dataEcBlocks.length; i++) {
			ECBlock ecBlock = dataEcBlocks[i];
			Block block = DataNodeTestUtils.getFSDataset(dataNode).getStoredBlock(ecBlock.getBlockPoolId(), ecBlock.getBlockId());
			File blockFile = DataNodeTestUtils.getBlockFile(dataNode, ecBlock.getBlockPoolId(), block);
			byte[] buffer = new byte[BLOCK_SIZE];
			IOUtils.readFully(new FileInputStream(blockFile), buffer, 0, BUFFER_SIZE);
			chunks[i] = new ECChunk(ByteBuffer.wrap(buffer));
		}
		return chunks;
	}

	public void verifyRSEncodeDecode(int dataSize, int paritySize) {
		ErasureCodec codec = null;
		try {
			ECSchema schema = TestSchemaLoader.loadRSJavaSchema(dataSize, paritySize);
			codec = ErasureCodec.createErasureCodec(schema);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (codec == null) {
			assertTrue(false);
		}
		
		ErasureCoder ec = codec.createErasureCoder();

		int symbolMax = (int) Math.pow(2,
				((JavaRSErasureCoder) ec).symbolSize());
		ECChunk[] message = new ECChunk[dataSize];
		ECChunk[] cpMessage = new ECChunk[dataSize];
		int bufsize = 1024 * 1024 * 10;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			ByteBuffer buffer = ByteBuffer.wrap(byteArray);
			message[i] = new ECChunk(buffer);
			cpMessage[i] = message[i].clone();
		}
		ECChunk[] parity = new ECChunk[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}

		// encode.
		ec.encode(cpMessage, parity);

		int erasedLocation = RAND.nextInt(dataSize);
		String annotation = generateAnnotation(erasedLocation);
		ECChunk copy = message[erasedLocation].clone();
		message[erasedLocation].setChunkBuffer(ByteBuffer
				.wrap(new byte[bufsize]));

		// test decode
		ECChunk[] output = new ECChunk[1];
		output[0] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		ec.decode(message, parity, annotation, output);
		assertTrue("Decode failed", copy.equals(output[0]));
	}

	private String generateAnnotation(int erasedLocation) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < erasedLocation; i++) {
			sb.append("XX");
		}
		sb.append("__");
		return sb.toString();
	}
}

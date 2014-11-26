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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.ec.ECChunk;
import org.apache.hadoop.hdfs.ec.ECSchema;
import org.apache.hadoop.hdfs.ec.codec.ErasureCodec;
import org.apache.hadoop.hdfs.ec.coder.ErasureCoder;
import org.apache.hadoop.hdfs.ec.coder.JavaRSErasureCoder;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.server.datanode.DataNodeTestUtils;
import org.apache.hadoop.net.NetUtils;
import org.junit.Test;

import com.sun.xml.bind.v2.runtime.output.Encoded;

public class TestErasureCodes {
//	final int TEST_CODES = 100;
//	final int TEST_TIMES = 1000;
	final Random RAND = new Random();
	
	private static final String TEST_DIR = new File(System.getProperty(
			"test.build.data", "/Users")).getAbsolutePath();
	private static final int BLOCK_COUNT = 10;
	private static final int BLOCK_SIZE = 1024;
	private static final int BUFFER_SIZE = BLOCK_SIZE / 4;
	private static final int DATA_SIZE = 10;
	private static final int PARITY_SIZE = 4;
	
	@Test
	public void verifyEncodeDecode() throws IOException {
		ErasureCoder ec = createEc();
		
		Configuration conf = new HdfsConfiguration();
		List<LocatedBlock> blocks = write(conf, BLOCK_COUNT, ((JavaRSErasureCoder)ec).symbolSize());
		assertTrue(blocks.size() == BLOCK_COUNT);
		
		//TODO
		List<LocatedBlock> parityBlocks = encode(blocks);
		
	}

	private ErasureCoder createEc() {
		ErasureCodec codec = null;
		try {
			ECSchema schema = TestSchemaLoader.loadRSJavaSchema(DATA_SIZE, PARITY_SIZE);
			codec = ErasureCodec.createErasureCodec(schema);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (codec == null) {
			assertTrue(false);
		}
		
		ErasureCoder ec = codec.createErasureCoder();
		return ec;
	}

	private List<LocatedBlock> write(Configuration conf, int blockCount, int symbolMax) throws IOException {
		int numDataNodes = 1;
		conf.setInt(DFSConfigKeys.DFS_BLOCK_SIZE_KEY, BLOCK_SIZE);
		MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(numDataNodes).build();
		cluster.waitActive();
		FileSystem fileSys = cluster.getFileSystem();
		//create
		DFSTestUtil.createFile(fileSys, new Path(TEST_DIR), BLOCK_SIZE * blockCount, (short)1, 0);
		//write
		for (int i = 0; i < BLOCK_COUNT; i++) {
			byte[] byteArray = new byte[BLOCK_SIZE];
			for (int j = 0; j < BUFFER_SIZE; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			
			DFSTestUtil.writeFile(fileSys, new Path(TEST_DIR), byteArray);
		}
		
		List<LocatedBlock> blocks = DFSTestUtil.getAllBlocks(fileSys, new Path(TEST_DIR));
		blocks.get(0).getBlock().getLocalBlock().write(out);
		return blocks;
	}
	
	private List<LocatedBlock> encode(List<LocatedBlock> blocks) {
		for (int i = 0; i < blocks.size(); i++) {
			ByteBuffer buffer = DFSTestUtil.readByteFile(f, bufferSize)
		}
		// TODO Auto-generated method stub
		return null;
	}
	
	public void testRSPerformance() {
		int dataSize = 10;
		int paritySize = 4;
		
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

		int symbolMax = (int) Math.pow(2, ((JavaRSErasureCoder)ec).symbolSize());
		ECChunk[] message = new ECChunk[dataSize];
		int bufsize = 1024 * 1024 * 10;
		for (int i = 0; i < dataSize; i++) {
			byte[] byteArray = new byte[bufsize];
			for (int j = 0; j < bufsize; j++) {
				byteArray[j] = (byte) RAND.nextInt(symbolMax);
			}
			message[i] = new ECChunk(ByteBuffer.wrap(byteArray));
		}
		ECChunk[] parity = new ECChunk[paritySize];
		for (int i = 0; i < paritySize; i++) {
			parity[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		long encodeStart = System.currentTimeMillis();

		ECChunk[] tmpIn = new ECChunk[dataSize];
		ECChunk[] tmpOut = new ECChunk[paritySize];
		for (int i = 0; i < tmpOut.length; i++) {
			tmpOut[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		for (int j = 0; j < dataSize; j++)
			tmpIn[j] = message[j].clone();
		ec.encode(tmpIn, tmpOut);
		// Copy parity.
		for (int j = 0; j < paritySize; j++)
			parity[j] = tmpOut[j].clone();
		long encodeEnd = System.currentTimeMillis();
		float encodeMSecs = (encodeEnd - encodeStart);
		System.out.println("Time to encode rs = " + encodeMSecs + "msec ("
				+ message[0].getChunkBuffer().array().length
				/ (1000 * encodeMSecs) + " MB/s)");

		
		
		
		
		String erasedAnnotation = "__D2__D4D5__D7";
		ECChunk[] erasedValues = new ECChunk[4];
		for (int i = 0; i < erasedValues.length; i++) {
			erasedValues[i] = new ECChunk(ByteBuffer.wrap(new byte[bufsize]));
		}
		ECChunk copy = message[0].clone();
		message[0].setChunkBuffer(ByteBuffer.wrap(new byte[bufsize]));

		long decodeStart = System.currentTimeMillis();
		ec.decode(message, parity, erasedAnnotation, erasedValues);
		message[0] = erasedValues[0];
		long decodeEnd = System.currentTimeMillis();
		float decodeMSecs = (decodeEnd - decodeStart);
		System.out.println("Time to decode = " + decodeMSecs + "msec ("
				+ message[0].getChunkBuffer().array().length
				/ (1000 * decodeMSecs) + " MB/s)");
		assertTrue("Decode failed", copy.equals(message[0]));
	}

	public void testRSEncodeDecodeBulk() {
		// verify the production size.
		verifyRSEncodeDecode(10, 4);

		// verify a test size
		verifyRSEncodeDecode(3, 3);
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

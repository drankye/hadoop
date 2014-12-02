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

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ECChunk {

	private ByteBuffer chunkBuffer;
	private boolean isMissing;

	public ECChunk(ByteBuffer buffer) {
		this.chunkBuffer = buffer;
	}

	public  ECChunk(ByteBuffer buffer, boolean isMissing) {
		this(buffer);
		this.isMissing = isMissing;
	}

	public ByteBuffer getChunkBuffer() {
		return chunkBuffer;
	}

	public boolean isMissing() {
		return isMissing;
	}

	public void fillZero() {
		byte[] array = chunkBuffer.array();
		Arrays.fill(array, (byte)0);
		chunkBuffer = ByteBuffer.wrap(array);
	}

	@Override
	public ECChunk clone() {
		byte[] oldBytes = chunkBuffer.array();
	    byte[] copiedBytes = new byte[oldBytes.length];
	    System.arraycopy(oldBytes, 0, copiedBytes, 0, oldBytes.length);
	    ByteBuffer duplicate = ByteBuffer.wrap(copiedBytes);
	    return new ECChunk(duplicate);
	}
	
	@Override
	public boolean equals(Object object) {
		if (object == null || !(object instanceof ECChunk)) {
			return false;
		}
		ECChunk annotherChunk = (ECChunk)object;
		return annotherChunk.chunkBuffer.equals(this.chunkBuffer);
	};
	
	@Override
	public int hashCode() {
		return chunkBuffer.hashCode() + 1;
	};
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < chunkBuffer.array().length; i++) {
			sb.append(chunkBuffer.array()[i] + " ");
		}
		return sb.toString();
	}
}

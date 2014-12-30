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
package org.apache.hadoop.hdfs.ec.rawcoder;

import org.apache.hadoop.hdfs.ec.rawcoder.util.RSUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class IsaRSRawDecoder extends AbstractRawErasureDecoder {
    private int[] matrix;

    public IsaRSRawDecoder(int dataSize, int paritySize, int chunkSize) {
        super(dataSize, paritySize, chunkSize);

        matrix = RSUtil.initMatrix(dataSize, paritySize);
        init(dataSize, paritySize, matrix);
    }

    @Override
    public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
        if (erasedIndexes.length == 0) {
            return;
        }

        decode(inputs, erasedIndexes, chunkSize());

        byte[][] correctData = getData(inputs);

        byte[][] outputsData = new byte[outputs.length][outputs[0].limit()];
        // cleanup the write buffer
        for (int i = 0; i < outputsData.length; i++) {
            Arrays.fill(outputsData[i], (byte) 0);
        }

        for (int i = 0; i < erasedIndexes.length; i++) {
            int errorLocation = erasedIndexes[i];
            outputsData[i] = correctData[errorLocation];
        }
        writeBuffer(outputs, outputsData);
    }

    static {
        System.loadLibrary("isajni");
    }

    private native static int init(int dataSize, int paritySize, int[] matrix);

    public native static int decode(ByteBuffer[] allData, int[] erasured, int chunkSize);

    private native static int destroy();

    public void end() {
        destroy();
    }

    @Override
    protected void finalize() {
        destroy();
    }


}

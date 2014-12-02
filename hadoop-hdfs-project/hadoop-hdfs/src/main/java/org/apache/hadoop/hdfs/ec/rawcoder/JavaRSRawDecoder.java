package org.apache.hadoop.hdfs.ec.rawcoder;

import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.rawcoder.util.RSUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class JavaRSRawDecoder extends  AbstractRawDecoder{
    private GaloisField GF = GaloisField.getInstance();

    private int PRIMITIVE_ROOT = 2;
    private int[] errSignature;
    private int[] primitivePower;

    public JavaRSRawDecoder(int dataSize, int paritySize, int chunkSize) {
        super(dataSize, paritySize, chunkSize);
        init();
    }

    private void init() {
        assert (dataSize() + paritySize() < GF.getFieldSize());
        this.errSignature = new int[paritySize()];
        this.primitivePower = RSUtil.getPrimitivePower(dataSize(), paritySize());
    }

    @Override
    public void decode(ByteBuffer[] inputs, ByteBuffer[] outputs, int[] erasedIndexes) {
        if (erasedIndexes.length == 0) {
            return;
        }

        // cleanup the write buffer
        for (int i = 0; i < outputs.length; i++) {
            Arrays.fill(outputs[i].array(), (byte) 0);
        }

        for (int i = 0; i < erasedIndexes.length; i++) {
            errSignature[i] = primitivePower[erasedIndexes[i]];
            GF.substitute(inputs, outputs[i], primitivePower[i]);
        }

        GF.solveVandermondeSystem(errSignature, outputs,
                erasedIndexes.length, inputs[0].array().length);
    }
}

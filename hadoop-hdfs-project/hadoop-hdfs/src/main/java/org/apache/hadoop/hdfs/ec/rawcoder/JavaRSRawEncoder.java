package org.apache.hadoop.hdfs.ec.rawcoder;

import org.apache.hadoop.hdfs.ec.coder.JavaRSEncoder;
import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;
import org.apache.hadoop.hdfs.ec.rawcoder.util.JavaRSRawUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class JavaRSRawEncoder extends AbstractRawEncoder{
    private GaloisField GF = GaloisField.getInstance();
    private int[] generatingPolynomial;

    public JavaRSRawEncoder(int dataSize, int paritySize, int chunkSize) {
        super(dataSize, paritySize, chunkSize);
        init();
    }

    private void init() {
        assert (dataSize() + paritySize() < GF.getFieldSize());
        int[] primitivePower = JavaRSRawUtil.getPrimitivePower(dataSize(), paritySize());
        // compute generating polynomial
        int[] gen = { 1 };
        int[] poly = new int[2];
        for (int i = 0; i < paritySize(); i++) {
            poly[0] = primitivePower[i];
            poly[1] = 1;
            gen = GF.multiply(gen, poly);
        }
        // generating polynomial has all generating roots
        generatingPolynomial = gen;
    }

    @Override
    public void encode(ByteBuffer[] inputs, ByteBuffer[] outputs) {
        assert (dataSize() == inputs.length);
        assert (paritySize() == outputs.length);

        for (int i = 0; i < outputs.length; i++) {
            Arrays.fill(outputs[i].array(), (byte) 0);
        }

        byte[][] data = new byte[dataSize() + paritySize()][];

        for (int i = 0; i < paritySize(); i++) {
            data[i] = outputs[i].array();
        }
        for (int i = 0; i < dataSize(); i++) {
            data[i + paritySize()] = inputs[i].array();
        }
        // Compute the remainder
        GF.remainder(data, generatingPolynomial);

    }
}

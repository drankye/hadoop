package org.apache.hadoop.hdfs.ec.rawcoder.util;

import org.apache.hadoop.hdfs.ec.coder.util.GaloisField;

public class RSUtil {

    private static final  int PRIMITIVE_ROOT = 2;
    private static GaloisField GF = GaloisField.getInstance();

    public static int[] getPrimitivePower(int dataSize, int paritySize) {
        int[] primitivePower = new int[dataSize + paritySize];
        // compute powers of the primitive root
        for (int i = 0; i < dataSize + paritySize; i++) {
            primitivePower[i] = GF.power(PRIMITIVE_ROOT, i);
        }
        return primitivePower;
    }


}

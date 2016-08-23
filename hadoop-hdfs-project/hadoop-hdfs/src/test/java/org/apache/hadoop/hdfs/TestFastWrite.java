package org.apache.hadoop.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertTrue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class TestFastWrite {

    Configuration conf;
    MiniDFSCluster cluster;
    DistributedFileSystem fs;
    int factor = 20;
    int bufferLen = 102400;
    int fileLen = factor * bufferLen;

    @Before
    public void setup() throws IOException {
        conf = new HdfsConfiguration();
        conf.set("dfs.client.read.shortcircuit","true");
        conf.set("dfs.domain.socket.path","/root/dn_socket_PORT");
        conf.set("dfs.client.localwrite.use.domain.socket","true");
        conf.set("dfs.blocksize","67108864000");
        conf.set("dfs.checksum.type","NULL");
        cluster = new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
        fs = cluster.getFileSystem();
    }

    @Test
    public void testFastWrite() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(fileLen);
        byte[] toWriteBytes = generateBytes(fileLen,0);
        buffer.put(toWriteBytes);
        buffer.flip();

        try {
            Path myFile = new Path("/test/dir/file");
            FSDataOutputStream out = fs.create(myFile, (short)3);
            out.write(buffer.array(),buffer.position(),buffer.arrayOffset()+buffer.remaining());
            out.close();

            assertTrue(fs.exists(myFile));

            long writenFileLen = fs.getFileStatus(myFile).getLen();
            Assert.assertEquals(fileLen, writenFileLen);

            byte[] readBytes = new byte[fileLen];
            FSDataInputStream in = fs.open(myFile);
            IOUtils.readFully(in, readBytes, 0, readBytes.length);

            Assert.assertArrayEquals(toWriteBytes, readBytes);

        } finally {
            cluster.shutdown();
        }
    }
    @Test
    public void testFastAppend() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(fileLen);
        byte[] toWriteBytes = generateBytes(fileLen,0);
        buffer.put(toWriteBytes);
        buffer.flip();

        try {
            Path myFile = new Path("/test/dir/file");
            FSDataOutputStream out = fs.create(myFile, (short)3);
            out.write(buffer.array(),buffer.position(),buffer.arrayOffset()+buffer.remaining());
            out.close();

            assertTrue(fs.exists(myFile));

            buffer.clear();
            buffer.put(toWriteBytes);
            buffer.flip();
            out = fs.append(myFile);
            out.write(buffer.array(),buffer.position(),buffer.arrayOffset()+buffer.remaining());
            out.close();

            long writenFileLen = fs.getFileStatus(myFile).getLen();
            Assert.assertEquals(fileLen*2, writenFileLen);

            byte[] readBytes = new byte[(int)writenFileLen];
            FSDataInputStream in = fs.open(myFile);
            IOUtils.readFully(in, readBytes, 0, readBytes.length);

            byte[] toBytes = new byte[fileLen*2];
            System.arraycopy(toWriteBytes,0,toBytes,0,toWriteBytes.length);
            System.arraycopy(toWriteBytes,0,toBytes,toWriteBytes.length,toWriteBytes.length);
            Assert.assertArrayEquals(toBytes, readBytes);

        } finally {
            cluster.shutdown();
        }
    }

    @Test
    public void testFastWriteMultipleTimes() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(bufferLen);

        try {
            Path myFile = new Path("/test/dir/file");
            FSDataOutputStream out = fs.create(myFile, (short)3);
            byte[] toWriteBytes = new byte[fileLen];
            int start=0;
            byte[] toWriteBytesEach;
            for(int i = 0; i < factor;i++){
                buffer.clear();
                toWriteBytesEach = generateBytes(bufferLen,i);
                System.arraycopy(toWriteBytesEach,0,toWriteBytes,start,toWriteBytesEach.length);
                buffer.put(toWriteBytesEach);
                buffer.flip();
                out.hflush();
                out.write(buffer.array(),buffer.position(),buffer.arrayOffset()+buffer.remaining());
                start+=bufferLen;
            }
            out.close();
            assertTrue(fs.exists(myFile));

            long writenFileLen = fs.getFileStatus(myFile).getLen();
            Assert.assertEquals(fileLen, writenFileLen);
            FSDataInputStream in = fs.open(myFile);
            byte[] readBytes = new byte[(int)writenFileLen];
            IOUtils.readFully(in, readBytes, 0, readBytes.length);
            Assert.assertArrayEquals(toWriteBytes,readBytes);
        } finally {
            cluster.shutdown();
        }
    }

    public static byte[] generateBytes(int cnt,int times) {
        byte[] bytes = new byte[cnt];
        /*for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (times & 0xff);
        }*/
        new Random().nextBytes(bytes);
        return bytes;
    }
}

package org.apache.hadoop.hdfs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FsShell;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by cuixuan on 6/30/16.
 */
public class TestWriteDBB {

    private static final Log LOG = LogFactory.getLog(TestWriteDBB.class);
    static Path writeFile(FileSystem fs, Path f) throws IOException {
        LOG.info("Begin to create new File.");
        FSDataOutputStream out = fs.create(f, (short)2);
//        out.writeBytes("dhruba: " + f);
        ByteBuffer buf = ByteBuffer.allocate(("dhruba: " + f).length());
        buf.put(("dhruba: " + f).getBytes());
        buf.flip();
        LOG.info("Begin to write ByteBuffer.");
        out.write(buf);
//        out.close();
        LOG.info("Begin to close the File.");
        out.closeFile();
        LOG.info("End File");
        assertTrue(fs.exists(f));
        Long len = fs.getLength(f);
        return f;
    }
    @Test
    public void testDu() throws IOException {
        Configuration conf = new HdfsConfiguration();
        MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(2).build();
        DistributedFileSystem fs = cluster.getFileSystem();

        try {
            Path myFile = new Path("/test/dir/file");
            writeFile(fs, myFile);
            assertTrue(fs.exists(myFile));
            Long myFileLength = fs.getFileStatus(myFile).getLen();

        } finally {
            cluster.shutdown();
        }

    }
}

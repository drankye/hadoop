package org.apache.hadoop.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;


public class TestDFSGetFilesAccessInfo {
    @Test(timeout=60000)
    public void testMultiAccess() throws IOException {
        Configuration conf = new Configuration();
        MiniDFSCluster cluster =
                new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
        DistributedFileSystem fs = cluster.getFileSystem();
        String filePath = "/testfile";
        DFSTestUtil.createFile(fs, new Path(filePath), 1024, (short) 3, 0);

        int numOpen = 4;
        for (int i = 0; i < numOpen; i++) {
            DFSInputStream fin = fs.dfs.open(filePath);
            fin.close();
        }
        try {
            FilesAccessInfo info = fs.dfs.getFilesAccessInfo(false);
            Map<String, Integer> accessMap = info.toHashMap();
            assertEquals(numOpen, accessMap.get(filePath).intValue());
        } finally {
            cluster.shutdown();
        }
    }

    @Test(timeout=60000)
    public void testMultiAccessMultiFiles() throws IOException {
        Configuration conf = new Configuration();
        MiniDFSCluster cluster =
                new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
        DistributedFileSystem fs = cluster.getFileSystem();
        String[] files = new String[]{"/B1", "/B2", "/A1", "/A2"};
        for(String file : files) {
            DFSTestUtil.createFile(fs, new Path(file), 1024, (short) 3, 0);
        }

        int[] numAccess = new int[files.length];
        for (int i = 0; i < numAccess.length; i++) {
            numAccess[i] = ThreadLocalRandom.current().nextInt(0, 8 + 1);
        }

        for (int i = 0; i < files.length; i++) {
            for (int j = 0; j < numAccess[i]; j++) {
                DFSInputStream fin = fs.dfs.open(files[i]);
                fin.close();
            }
        }

        try {
            FilesAccessInfo info = fs.dfs.getFilesAccessInfo(false);
            Map<String, Integer> accessMap = info.toHashMap();
            for (int i = 0; i < files.length; i++) {
                Integer acc = accessMap.get(files[i]);
                assertEquals(numAccess[i], acc == null ? 0 : acc.intValue());
            }
        } finally {
            cluster.shutdown();
        }
    }

    @Test(timeout=60000)
    public void testMultiAccessMultiFilesMultiRounds() throws IOException {
        Configuration conf = new Configuration();
        MiniDFSCluster cluster =
                new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
        DistributedFileSystem fs = cluster.getFileSystem();
        String[] files = new String[]{"/B1", "/B2", "/A1", "/A2"};
        for(String file : files) {
            DFSTestUtil.createFile(fs, new Path(file), 1024, (short) 3, 0);
        }

        int[] numAccess = new int[files.length];
        for (int i = 0; i < numAccess.length; i++) {
            numAccess[i] = ThreadLocalRandom.current().nextInt(0, 8 + 1);
        }

        for (int i = 0; i < files.length; i++) {
            for (int j = 0; j < numAccess[i]; j++) {
                DFSInputStream fin = fs.dfs.open(files[i]);
                fin.close();
            }
        }

        FilesAccessInfo info;
        Map<String, Integer> accessMap;
        try {
            info = fs.dfs.getFilesAccessInfo(false);
            accessMap = info.toHashMap();
            for (int i = 0; i < files.length; i++) {
                Integer acc = accessMap.get(files[i]);
                assertEquals(numAccess[i], acc == null ? 0 : acc.intValue());
            }

            for (int i = 0; i < files.length; i++) {
                for (int j = 0; j < numAccess[i]; j++) {
                    DFSInputStream fin = fs.dfs.open(files[i]);
                    fin.close();
                }
            }

            info = fs.dfs.getFilesAccessInfo(false);
            accessMap = info.toHashMap();
            for (int i = 0; i < files.length; i++) {
                Integer acc = accessMap.get(files[i]);
                assertEquals(numAccess[i] * 2, acc == null ? 0 : acc.intValue());
            }
        } finally {
            cluster.shutdown();
        }
    }

    @Test(timeout=60000)
    public void testMultiAccessMultiFilesMultiRoundsWithReset()
            throws IOException {
        Configuration conf = new Configuration();
        MiniDFSCluster cluster =
                new MiniDFSCluster.Builder(conf).numDataNodes(3).build();
        DistributedFileSystem fs = cluster.getFileSystem();
        String[] files = new String[]{"/B1", "/B2", "/A1", "/A2"};
        for(String file : files) {
            DFSTestUtil.createFile(fs, new Path(file), 1024, (short) 3, 0);
        }

        int[] numAccess = new int[files.length];
        for (int i = 0; i < numAccess.length; i++) {
            numAccess[i] = ThreadLocalRandom.current().nextInt(0, 8 + 1);
        }

        for (int i = 0; i < files.length; i++) {
            for (int j = 0; j < numAccess[i]; j++) {
                DFSInputStream fin = fs.dfs.open(files[i]);
                fin.close();
            }
        }

        FilesAccessInfo info;
        Map<String, Integer> accessMap;
        try {
            info = fs.dfs.getFilesAccessInfo(true);
            accessMap = info.toHashMap();
            for (int i = 0; i < files.length; i++) {
                Integer acc = accessMap.get(files[i]);
                assertEquals(numAccess[i], acc == null ? 0 : acc.intValue());
            }

            for (int i = 0; i < files.length; i++) {
                for (int j = 0; j < numAccess[i]; j++) {
                    DFSInputStream fin = fs.dfs.open(files[i]);
                    fin.close();
                }
            }

            info = fs.dfs.getFilesAccessInfo(false);
            accessMap = info.toHashMap();
            for (int i = 0; i < files.length; i++) {
                Integer acc = accessMap.get(files[i]);
                assertEquals(numAccess[i], acc == null ? 0 : acc.intValue());
            }
        } finally {
            cluster.shutdown();
        }
    }
}

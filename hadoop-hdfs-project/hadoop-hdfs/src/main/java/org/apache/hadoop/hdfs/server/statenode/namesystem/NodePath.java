package org.apache.hadoop.hdfs.server.statenode.namesystem;

/**
 * Created by root on 8/1/16.
 */
public interface NodePath {
    public boolean isDir();
    public String getPath();
    public byte[] toByteArray();
}

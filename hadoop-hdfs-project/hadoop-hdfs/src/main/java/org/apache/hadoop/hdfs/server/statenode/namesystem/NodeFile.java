package org.apache.hadoop.hdfs.server.statenode.namesystem;


/**
 * Created by root on 8/2/16.
 */
public class NodeFile implements NodePath {
    private String path;
    public NodeFile(String path){
        this.path = path;
    }
    @Override
    public boolean isDir() {
        return false;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] toByteArray() {
        FileSystemProtocol.Node.Builder builder = FileSystemProtocol.Node.newBuilder();
        FileSystemProtocol.IsDir isDir = FileSystemProtocol.IsDir.NORMAL_FILE;
        builder.setIsdir(isDir);
        builder.setPath(path);
        FileSystemProtocol.Node file = builder.build();
        byte[] buf = file.toByteArray();
        return buf;
    }
    @Override
    public String toString() {
        return "{"+path+" , "+isDir()+"}";
    }
}

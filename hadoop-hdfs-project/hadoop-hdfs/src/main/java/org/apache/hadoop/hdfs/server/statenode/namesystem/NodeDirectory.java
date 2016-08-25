package org.apache.hadoop.hdfs.server.statenode.namesystem;


import static org.apache.hadoop.hdfs.server.statenode.namesystem.FileSystemProtocol.*;

/**
 * Created by root on 8/2/16.
 */
public class NodeDirectory implements NodePath {
    private String path;
    public NodeDirectory(String path){
        this.path = path;
    }
    @Override
    public boolean isDir() {
        return true;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] toByteArray() {
        /*FileSystemProtocol.Node.Builder builder = FileSystemProtocol.Node.newBuilder();
        FileSystemProtocol.IsDir isDir = FileSystemProtocol.IsDir.DIRECTORY;
        builder.setIsdir(isDir);
        builder.setPath(path);
        FileSystemProtocol.Node file = builder.build();
        byte[] buf = file.toByteArray();*/

        NodeList.Builder builder = NodeList.newBuilder();
        Node.Builder nodeBuilder = Node.newBuilder();
        IsDir isDir = IsDir.DIRECTORY;
        nodeBuilder.setIsdir(isDir);
        nodeBuilder.setPath(path);
        Node file = nodeBuilder.build();
        builder.addPaths(file);
        NodeList list = builder.build();
        byte[] buf = list.toByteArray();
        return buf;
    }

    @Override
    public String toString() {
        return "{"+path+" , "+isDir()+"}";
    }
}

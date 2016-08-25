package org.apache.hadoop.hdfs.server.statenode.namesystem;

import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by root on 8/2/16.
 */
public class FileNameSystem {
    private static final String db_path = "/tmp/rocks.db";
    private Options options;
    private RocksDB db;

    /**
     * Constructor about the FileNameSystem, which opens the rocksdb for reading and writing.
     * @throws RocksDBException
     */
    public FileNameSystem() throws RocksDBException {
        options = new Options();
        options.setCreateIfMissing(true)
                .createStatistics()
                .setWriteBufferSize(8 * SizeUnit.KB)
                .setMaxWriteBufferNumber(3)
                .setMaxBackgroundCompactions(10)
                .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
                .setCompactionStyle(CompactionStyle.UNIVERSAL);
        db = RocksDB.open(options, db_path);
    }

    /**
     * create a file on the FileSystem based on src
     * @param src the file path which need to create
     * @return
     * @throws Exception
     */
    public boolean createFile(String src) throws Exception {
        return createPath(src,false);
    }

    /**
     * create a file or directory based on src and isDir
     * @param src the path which need to create
     * @param isDir if this path is a directory
     * @return
     * @throws Exception
     */
    private boolean createPath(String src,boolean isDir) throws Exception{
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        src = resolvePath(src);
        String parentDir = src.substring(0,src.lastIndexOf("/")+1);
        StringBuffer buf = new StringBuffer();
        if(!db.keyMayExist(parentDir.getBytes(),buf)) {
            throw new IOException("Parent Directory \""+parentDir+"\" not exist.");
        }
        if(db.keyMayExist(src.getBytes(),buf)){
            throw new FileAlreadyExistsException("\""+src+"\"");
        }
        if(db.keyMayExist((src+"/").getBytes(),buf)){
            throw new FileAlreadyExistsException("\""+src+"/\"");
        }
        if(isDir){
            src = src + "/";
        }
        byte[] valdata = db.get(parentDir.getBytes());
        FileSystemProtocol.NodeList list = FileSystemProtocol.NodeList.parseFrom(valdata);
        FileSystemProtocol.NodeList.Builder listBuilder = list.toBuilder();
        FileSystemProtocol.Node.Builder nodeBuilder = FileSystemProtocol.Node.newBuilder();
        nodeBuilder.setIsdir(isDir?FileSystemProtocol.IsDir.DIRECTORY: FileSystemProtocol.IsDir.NORMAL_FILE);
        nodeBuilder.setPath(src);
        listBuilder.addPaths(nodeBuilder.build());
        FileSystemProtocol.NodeList nodeList = listBuilder.build();
        WriteBatch batch = new WriteBatch();
        WriteOptions options = new WriteOptions();
        batch.remove(parentDir.getBytes());
        batch.put(parentDir.getBytes(),nodeList.toByteArray());

        NodePath path;
        if(isDir){
            path = new NodeDirectory(src);
        }else{
            path = new NodeFile(src);
        }
        batch.put(src.getBytes(),path.toByteArray());
        db.write(options,batch);
        System.out.println(String.format("Create Directory \"%s\" Success.",src));
        return true;
    }

    /**
     * format the path string
     * @param src input for the path name
     * @return return a formatted path
     * @throws Exception
     */
    private String resolvePath(String src) throws Exception {
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        int i=src.length()-1;
        while(i>=0 && src.charAt(i)=='/')i--;
        return src.substring(0,i+1);
    }

    /**
     * create a directory for a given path
     * @param src the path of the directory
     * @return
     * @throws Exception
     */
    public boolean createDirectory(String src) throws Exception {
        return createPath(src,true);
    }

    /**
     * delete a path from the FileSystem
     * @param src path which needed to delete
     * @param isDir if this path is directory or not
     * @return
     * @throws Exception
     */
    public boolean deletePath(String src,boolean isDir) throws Exception {
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        src = resolvePath(src);
        String parentDir = src.substring(0,src.lastIndexOf("/")+1);
        StringBuffer buf = new StringBuffer();
        if(!db.keyMayExist(parentDir.getBytes(),buf)) {
            throw new IOException("Parent Directory \""+parentDir+"\" not exist.");
        }
        if(isDir){
            src = src+"/";
        }
        if(!db.keyMayExist(src.getBytes(),buf)) {
            throw new IOException("File \""+src+"\" not exist.");
        }
        byte[] valdata = db.get(parentDir.getBytes());
        FileSystemProtocol.NodeList list = FileSystemProtocol.NodeList.parseFrom(valdata);
        FileSystemProtocol.NodeList.Builder listBuilder = list.toBuilder();
        FileSystemProtocol.NodeList.Builder newListBuilder = FileSystemProtocol.NodeList.newBuilder();
        List<FileSystemProtocol.Node> lists = listBuilder.getPathsList();
        for(int i=0;i<lists.size();i++){
            FileSystemProtocol.Node node = lists.get(i);
            if(!node.getPath().equals(src)){
                newListBuilder.addPaths(node);
            }
        }

        FileSystemProtocol.NodeList nodeList = newListBuilder.build();
        WriteBatch batch = new WriteBatch();
        WriteOptions options = new WriteOptions();
        batch.remove(parentDir.getBytes());
        batch.put(parentDir.getBytes(),nodeList.toByteArray());
        if(isDir){
            RocksIterator iterator = db.newIterator();
            for(iterator.seek(src.getBytes());iterator.isValid();iterator.next()){
                String key = new String(iterator.key());
                if(key!=null && key.startsWith(src)){
                    batch.remove(iterator.key());
                }else break;
            }
        }else{
            batch.remove(src.getBytes());
        }
        db.write(options,batch);
        return true;
    }

    /**
     * delete a file for a given path
     * @param src path needed to delete
     * @return
     * @throws Exception
     */
    public boolean deleteFile(String src) throws Exception{
        return deletePath(src,false);
    }
    /**
     * delete a directory for a given path
     * @param src path needed to delete
     * @return
     * @throws Exception
     */
    public boolean deleteDirectory(String src) throws Exception {
        return deletePath(src,true);
    }

    /**
     * change a name for a given path
     * @param src the path needed to change
     * @param dst the destination path to change
     * @param isDir if this path is directory or not.
     * @return
     * @throws Exception
     */
    public boolean modifyPath(String src,String dst,boolean isDir) throws Exception{
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        src = resolvePath(src);
        dst = resolvePath(dst);

        String srcParentDir = src.substring(0,src.lastIndexOf("/")+1);
        String dstParentDir = dst.substring(0,dst.lastIndexOf("/")+1);
        if(isDir){
            src = src+"/";
            dst = dst+"/";
        }
        StringBuffer buf = new StringBuffer();
        if(!db.keyMayExist(srcParentDir.getBytes(),buf)) {
            throw new IOException("Parent Directory \""+srcParentDir+"\" not exist.");
        }
        if(!db.keyMayExist(src.getBytes(),buf)) {
            throw new IOException("Path \""+src+"\" not exist.");
        }
        if(!db.keyMayExist(dstParentDir.getBytes(),buf)) {
            throw new IOException("Parent Directory \""+srcParentDir+"\" not exist.");
        }
        if(db.keyMayExist(dst.getBytes(),buf)) {
            throw new IOException("Path \""+dst+"\" already exist.");
        }
        //delete the path from its parent
        byte[] srcvaldata = db.get(srcParentDir.getBytes());
        FileSystemProtocol.NodeList srcList = FileSystemProtocol.NodeList.parseFrom(srcvaldata);
        FileSystemProtocol.NodeList.Builder srcListBuilder = srcList.toBuilder();
        FileSystemProtocol.NodeList.Builder srcNewListBuilder = FileSystemProtocol.NodeList.newBuilder();
        for(int i=0;i<srcListBuilder.getPathsCount();i++){
            FileSystemProtocol.Node.Builder nodeBuilder = srcListBuilder.getPaths(i).toBuilder();
            if(nodeBuilder.getPath().equals(src)){
                nodeBuilder.setPath(dst);
                srcNewListBuilder.addPaths(nodeBuilder.build());
            }else{
                srcNewListBuilder.addPaths(nodeBuilder.build());
            }
        }
        //add the dst path to its parent
        WriteOptions options = new WriteOptions();
        WriteBatch batch = new WriteBatch();
        FileSystemProtocol.NodeList.Builder dstListBuilder;
        if(!dstParentDir.equals(srcParentDir)){
            FileSystemProtocol.NodeList srcNodeList = srcNewListBuilder.build();
            batch.remove(srcParentDir.getBytes());
            batch.put(srcParentDir.getBytes(),srcNodeList.toByteArray());
            byte[] dstvaldata = db.get(dstParentDir.getBytes());
            FileSystemProtocol.NodeList dstList = FileSystemProtocol.NodeList.parseFrom(dstvaldata);
            dstListBuilder = dstList.toBuilder();
        }else{
            dstListBuilder = srcNewListBuilder;
        }

        FileSystemProtocol.Node.Builder nodeBuilder = FileSystemProtocol.Node.newBuilder();
        nodeBuilder.setIsdir(isDir?FileSystemProtocol.IsDir.DIRECTORY: FileSystemProtocol.IsDir.NORMAL_FILE);
        nodeBuilder.setPath(src);
        dstListBuilder.addPaths(nodeBuilder.build());

        FileSystemProtocol.NodeList dstNodeList = dstListBuilder.build();
        batch.remove(dstParentDir.getBytes());
        batch.put(dstParentDir.getBytes(),dstNodeList.toByteArray());
        if(isDir){
            RocksIterator iterator = db.newIterator();
            for(iterator.seek(src.getBytes());iterator.isValid();iterator.next()){
                String key = new String(iterator.key());
                if(key!=null && key.startsWith(src)){
                    key = key.replaceFirst(src,dst);
                    byte[] val = iterator.value();
                    if(key.endsWith("/")) {//if this item is a directory, change its list in val
                        FileSystemProtocol.NodeList list = FileSystemProtocol.NodeList.parseFrom(val);
                        FileSystemProtocol.NodeList.Builder listBuilder = list.toBuilder();
                        FileSystemProtocol.NodeList.Builder newListBuilder = FileSystemProtocol.NodeList.newBuilder();
                        for (int i = 0; i < listBuilder.getPathsCount(); i++) {
                            FileSystemProtocol.Node node = listBuilder.getPaths(i);
                            FileSystemProtocol.Node.Builder newNodeBuilder = node.toBuilder();
                            String path = node.getPath();
                            path = path.replaceFirst(src, dst);
                            newNodeBuilder.setPath(path);
                            newListBuilder.addPaths(newNodeBuilder.build());
                        }
                        FileSystemProtocol.NodeList newList = newListBuilder.build();
                        batch.remove(iterator.key());
                        batch.put(key.getBytes(), newList.toByteArray());
                    }else{//else only change itself
                        FileSystemProtocol.Node node = FileSystemProtocol.Node.parseFrom(val);
                        FileSystemProtocol.Node.Builder builder = node.toBuilder();
                        builder.setPath(key);
                        node = builder.build();
                        batch.remove(iterator.key());
                        batch.put(key.getBytes(), node.toByteArray());
                    }
                }else break;
            }
        }else{
            String key = src;
            byte[] val = db.get(key.getBytes());
            key = key.replaceFirst(src,dst);
            FileSystemProtocol.Node node = FileSystemProtocol.Node.parseFrom(val);
            FileSystemProtocol.Node.Builder builder = node.toBuilder();
            builder.setPath(key);
            node = builder.build();
            batch.remove(src.getBytes());
            batch.put(key.getBytes(), node.toByteArray());
        }
        db.write(options,batch);
        return true;
    }
    /**
     * change a name for a given File
     * @param src the path needed to change
     * @param dst the destination path to change
     * @return
     * @throws Exception
     */
    public boolean modifyFile(String src,String dst) throws Exception {
        return modifyPath(src,dst,false);
    }
    /**
     * change a name for a given Directory
     * @param src the path needed to change
     * @param dst the destination path to change
     * @return
     * @throws Exception
     */
    public boolean modifyDirectory(String src,String dst) throws Exception {
        return modifyPath(src,dst,true);
    }

    /**
     * search the information about a given file
     * @param src file path needed to search
     * @return a NodeFile object which describes the file
     * @throws Exception
     */
    public NodeFile searchFile(String src) throws Exception {
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        src = resolvePath(src);
        StringBuffer buf = new StringBuffer();
        if(!db.keyMayExist(src.getBytes(),buf)) {
            throw new IOException("File \""+src+"\" not exist.");
        }
        byte[] value = db.get(src.getBytes());
        FileSystemProtocol.Node file = FileSystemProtocol.Node.parseFrom(value);
        NodeFile nodefile = new NodeFile(file.getPath());
        return nodefile;
    }
    /**
     * search the information about a given directory
     * @param src directory path needed to search
     * @return a list of NodePath object which describes the files in this directory
     * @throws Exception
     */
    public List<NodePath> searchDirectory(String src) throws Exception {
        if(src==null || !src.startsWith("/")){
            throw new Exception("src must be not null and start with '/'.");
        }
        src = resolvePath(src)+"/";
        StringBuffer buf = new StringBuffer();
        if(!db.keyMayExist(src.getBytes(),buf)) {
            throw new IOException("Directory \""+src+"\" not exist.");
        }
        FileSystemProtocol.NodeList nodeList = FileSystemProtocol.NodeList.parseFrom(db.get(src.getBytes()));
        List<NodePath> nodes = new ArrayList<>();
        for(int i=0;i<nodeList.getPathsCount();i++){
            FileSystemProtocol.Node node = nodeList.getPaths(i);
            NodePath path;
            if(node.getIsdir() == FileSystemProtocol.IsDir.NORMAL_FILE){
                path= new NodeFile(node.getPath());
            }else{
                path= new NodeDirectory(node.getPath());
            }
            nodes.add(path);
        }
        return nodes;
    }
    public void format() throws IOException, RocksDBException {
        RocksIterator iterator = db.newIterator();
        for(iterator.seekToFirst();iterator.isValid();iterator.next()){
            db.remove(iterator.key());
        }
        NodePath root = new NodeDirectory("/");
        db.put("/".getBytes(),root.toByteArray());
    }
    public void printAll(){
        System.out.println("################File System##############################");
        RocksIterator iterator = db.newIterator();
        for(iterator.seekToFirst();iterator.isValid();iterator.next()){
            String key = new String(iterator.key());
            System.out.println(key);
        }
        System.out.println("########################END##############################");
    }
    public static void main(String[] args) throws Exception {
        FileNameSystem fs = new FileNameSystem();
        fs.format();
        fs.createDirectory("/DIR_a");
        fs.createFile("/FILE_a");
        fs.createFile("/DIR_a/FILE_b///");
        fs.createDirectory("/DIR_a/DIR_b");

        fs.deleteFile("/FILE_a");
        fs.deleteDirectory("/DIR_a");

        fs.createDirectory("/DIR_a");
        fs.createFile("/FILE_a");
        fs.createFile("/DIR_a/FILE_b///");
        fs.createDirectory("/DIR_a/DIR_b");

        List<NodePath> paths = fs.searchDirectory("/DIR_a");
        System.out.println(paths);
        fs.modifyDirectory("/DIR_a/","/DIR_A");
        fs.modifyFile("/FILE_a","/FILE_A");
        fs.modifyFile("/FILE_A","/DIR_A/FILE_A");
        List<NodePath> nodes = fs.searchDirectory("/");
        for(NodePath node:nodes){
            System.out.println(node.getPath()+" isDir:"+node.isDir());
        }

        paths = fs.searchDirectory("/DIR_A");
        System.out.println(paths);
        fs.searchFile("/DIR_A/FILE_A");

        fs.printAll();
    }
}

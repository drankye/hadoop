package org.apache.hadoop.hdfs.server.statenode;


import org.apache.hadoop.hdfs.server.statenode.namesystem.FileNameSystem;
import org.rocksdb.RocksDBException;

import java.io.IOException;

/**
 * Created by root on 8/23/16.
 */
public class StateNode {
    private FileNameSystem fs;
    private OpRecvServer server;
    private boolean isRunning;
    private StateNode(){
        isRunning = false;
    }
    public static void main(String[] args) {
        StateNode stateNode = new StateNode();
        stateNode.run();
    }
    private void run(){
        if(!isRunning){
            init();
        }
        try{
            server = new OpRecvServer(fs);
            server.run();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void init() {
        try {
            fs = new FileNameSystem();
            fs.format();
        } catch (RocksDBException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isRunning = true;
        }
    }
}

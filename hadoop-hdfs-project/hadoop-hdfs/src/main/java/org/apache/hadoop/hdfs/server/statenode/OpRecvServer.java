package org.apache.hadoop.hdfs.server.statenode;

import org.apache.hadoop.hdfs.server.statenode.namesystem.FileNameSystem;

import java.io.File;
import java.io.IOException;

/**
 * Created by root on 8/23/16.
 */
public class OpRecvServer extends Thread{

    private FileNameSystem fs;
    private OpReader reader;
    private File editLogPath;
    public OpRecvServer(FileNameSystem fs){
        this.fs = fs;
    }
    private void init(){
        editLogPath=new File("/tmp/edits_0000000000000002617-0000000000000002927");
    }
    @Override
    public void run(){
        init();
        while(true){
            try {
                reader = new OpReader(fs,editLogPath);
                reader.run();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
   }
}

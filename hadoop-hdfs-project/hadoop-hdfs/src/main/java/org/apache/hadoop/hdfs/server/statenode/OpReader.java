package org.apache.hadoop.hdfs.server.statenode;

import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.namenode.EditLogFileInputStream;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogLoader;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOp;
import org.apache.hadoop.hdfs.server.statenode.namesystem.FileNameSystem;

import java.io.File;
import java.io.IOException;

/**
 * Created by root on 8/23/16.
 */
public class OpReader extends Thread {

    private FileNameSystem fs;
    private EditLogFileInputStream in;
    private boolean supportEditLogLength = true;

    public OpReader(FileNameSystem fs, File f) throws IOException {
        this.fs = fs;
        this.in = new EditLogFileInputStream(f, HdfsConstants.INVALID_TXID, HdfsConstants.INVALID_TXID, false);
    }

    @Override
    public void run() {
        while (true) {
            try {
                FSEditLogOp op;
                op = in.readOp();
                if(op==null)continue;
                applyEditLogOp(op);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void applyEditLogOp(FSEditLogOp op) throws Exception {
        switch (op.opCode){
            case OP_ADD: {
                FSEditLogOp.AddCloseOp addCloseOp = (FSEditLogOp.AddCloseOp) op;
                final String path = addCloseOp.getPath();
                fs.createFile(path);
                break;
            }
            case OP_RENAME: {
                FSEditLogOp.RenameOp renameOp = (FSEditLogOp.RenameOp) op;
                final String src = renameOp.src;
                final String dst = renameOp.dst;
                fs.modifyDirectory(src, dst);
                break;
            }
            case OP_MKDIR: {
                FSEditLogOp.MkdirOp mkdirOp = (FSEditLogOp.MkdirOp) op;
                final String src = mkdirOp.path;
                fs.createDirectory(src);
                break;
            }
            case OP_DELETE:{
                FSEditLogOp.DeleteOp deleteOp = (FSEditLogOp.DeleteOp)op;
                final String src = deleteOp.path;
                fs.deleteDirectory(src);
                break;
            }
        }
    }
}

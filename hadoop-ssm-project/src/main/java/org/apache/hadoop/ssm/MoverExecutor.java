package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

/**
 * Created by root on 11/10/16.
 */
public class MoverExecutor implements Runnable{
  private DFSClient dfsClient;
  Configuration conf;
  private String fileName;
  private Action action;

  public MoverExecutor(DFSClient dfsClient, Configuration conf, String fileName, Action action) {
    this.dfsClient = dfsClient;
    this.conf = conf;
    this.fileName = fileName;
    this.action = action;
  }

  public void run() {
    switch (action) {
      case ARCHIVE:
        runArchive();
        break;
      case CACHE:
        break;
      case SSD:
        runSSD();
        break;
      default:
    }
  }

  private void runArchive() {
    try {
      dfsClient.setStoragePolicy(fileName, "COLD");
    } catch (Exception e) {
      return;
    }
    try {
      ToolRunner.run(conf, new Mover.Cli(),
              new String[]{"-p", fileName});
    } catch (Exception e) {
      return;
    }
  }

  private void runSSD() {
    try {
      dfsClient.setStoragePolicy(fileName, "ALL_SSD");
    } catch (Exception e) {
      return;
    }
    try {
      ToolRunner.run(conf, new Mover.Cli(),
              new String[]{"-p", fileName});
    } catch (Exception e) {
      return;
    }
  }
}

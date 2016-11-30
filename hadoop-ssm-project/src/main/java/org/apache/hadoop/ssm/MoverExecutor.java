package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by root on 11/10/16.
 */
public class MoverExecutor {
  private static MoverExecutor instance;

  private DFSClient dfsClient;
  Configuration conf;
  private LinkedBlockingQueue<FileAction> actionEvents;

  class FileAction {
    String fileName;
    Action action;

    FileAction(String fileName, Action action) {
      this.fileName = fileName;
      this.action = action;
    }
  }

  private MoverExecutor(DFSClient dfsClient, Configuration conf) {
    this.dfsClient = dfsClient;
    this.conf = conf;
    this.actionEvents = new LinkedBlockingQueue<>();
  }

  public static synchronized MoverExecutor getInstance(DFSClient dfsClient, Configuration conf) {
    if (instance == null) {
      instance = new MoverExecutor(dfsClient, conf);
    }
    return instance;
  }

  public static synchronized MoverExecutor getInstance() {
    return instance;
  }

  public void addActionEvent(String fileName, Action action) {
    try {
      actionEvents.put(new FileAction(fileName, action));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public FileAction getActionEvent() {
    try {
      return actionEvents.take();
    } catch (Exception e){
      throw new RuntimeException(e);
    }
  }

  public void run() {
    ExecutorService exec = Executors.newCachedThreadPool();
    exec.execute(new ExecutorRunner());
  }

  class ExecutorRunner implements Runnable {

    public void run() {
      while (true) {
        FileAction fileAction = instance.getActionEvent();
        String fileName = fileAction.fileName;
        Action action = fileAction.action;
        switch (action) {
          case SSD:
            runSSD(fileName);
            break;
          case ARCHIVE:
            runArchive(fileName);
            break;
          case CACHE:
            break;
          default:
        }
      }
    }

    private void runArchive(String fileName) {
      System.out.println("*" + System.currentTimeMillis() + " : " + fileName + " -> " + "archive");
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

    private void runSSD(String fileName) {
      System.out.println("*" + System.currentTimeMillis() + " : " + fileName + " -> " + "ssd");
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


}

package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by root on 11/10/16.
 */
public class MoverExecutor {
  private static MoverExecutor instance;

  private DFSClient dfsClient;
  Configuration conf;
  Map<String, Action> actionEvents;

  private MoverExecutor(DFSClient dfsClient, Configuration conf) {
    this.dfsClient = dfsClient;
    this.conf = conf;
    actionEvents = new HashMap<String, Action>();
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

  public synchronized void addActionEvent(String fileName, Action action) {
    actionEvents.put(fileName, action);
  }

  public synchronized Map<String, Action> getActionEventsWithClear() {
    try {
      return new HashMap<String, Action>(actionEvents);
    } finally {
      actionEvents.clear();
    }
  }

  public void run() {
    ExecutorService exec = Executors.newCachedThreadPool();
    exec.execute(new ExecutorRunner());
  }

  class ExecutorRunner implements Runnable {

    public void run() {
      while (true) {
        Map<String, Action> eventsMap = instance.getActionEventsWithClear();
        for (Map.Entry<String, Action> entry : eventsMap.entrySet()) {
          String fileName = entry.getKey();
          Action action = entry.getValue();
          System.out.println("execute action : fileName = " + fileName + "; action = " + action);
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
    }

    private void runArchive(String fileName) {
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

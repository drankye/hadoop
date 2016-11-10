package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  private FileAccessMap fileMap;
  private HashMap<Long, RuleContainer> ruleMaps;
  private DFSClient dfsClient;
  private Configuration conf;

  public DecisionMaker(DFSClient dfsClient, Configuration conf) {
    fileMap = new FileAccessMap();
    ruleMaps = new HashMap<Long, RuleContainer>();
    this.dfsClient = dfsClient;
    this.conf = conf;
  }

  /**
   * Read FilesAccessInfo to refresh file information of DecisionMaker
   * @param filesAccessInfo
   */
  private void getFilesAccess(FilesAccessInfo filesAccessInfo){
    // update fileMap
    fileMap.updateFileMap(filesAccessInfo);
    // process nnEvent
    fileMap.processNnEvents(filesAccessInfo);

    // update ruleMaps
    for (Map.Entry<Long, RuleContainer> entry : ruleMaps.entrySet()) {
      entry.getValue().update(filesAccessInfo);
    }
  }

  /**
   * Run Mover tool to move a file.
   * @param fileActions
   * @return true if move succeed; else false
   */
  private void runMover(HashMap<String, Action> fileActions) {
    ExecutorService exec = Executors.newCachedThreadPool();
    for (Map.Entry<String, Action> fileAction : fileActions.entrySet()) {
      String fileName = fileAction.getKey();
      Action action = fileAction.getValue();
      switch (action) {
        case ARCHIVE:
          if (!fileMap.get(fileName).isOnArchive) {
            fileMap.get(fileName).isOnArchive = true;
            exec.execute(new MoverExecutor(dfsClient, conf, fileName, action));
          }
          break;
        case CACHE:
          if (!fileMap.get(fileName).isOnCache) {
            fileMap.get(fileName).isOnCache = true;
            exec.execute(new MoverExecutor(dfsClient, conf, fileName, action));
          }
          break;
        default:
      }
    }
  }


  public void execution(DFSClient dfsClient, Configuration conf, FilesAccessInfo filesAccessInfo) {
    // update information
    getFilesAccess(filesAccessInfo);

    // run executor
    HashMap<String, Action> fileActions = new HashMap<String, Action>();
    for (Map.Entry<Long, RuleContainer> entry : ruleMaps.entrySet()) {
      fileActions.putAll(entry.getValue().actionEvaluator(fileMap));
    }
    runMover(fileActions);
  }
}


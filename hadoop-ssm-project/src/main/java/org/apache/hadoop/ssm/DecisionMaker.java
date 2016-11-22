package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.ssm.api.Expression.SSMRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  public static final Logger LOG = LoggerFactory.getLogger(DecisionMaker.class);

  private FileAccessMap fileMap;
  private HashMap<Long, RuleContainer> ruleMaps;

  private DFSClient dfsClient;
  private Configuration conf;
  private Long updateDuration; // second
  private Boolean isInitialized;

  private HashMap<Long, SSMRule> newRules;

  public DecisionMaker(DFSClient dfsClient, Configuration conf, Long updateDuration) {
    fileMap = new FileAccessMap();
    ruleMaps = new HashMap<Long, RuleContainer>();
    newRules = new HashMap<Long, SSMRule>();
    this.dfsClient = dfsClient;
    this.conf = conf;
    this.updateDuration = updateDuration;
    isInitialized = false;
  }

  /**
   * Add new rules to this class
   * @param ssmRule
   */
  synchronized public void addRule(SSMRule ssmRule) {
    newRules.put(ssmRule.getId(), ssmRule);
  }

  /**
   * Update rules each time doing execution
   */
  synchronized private Integer updateRules() {
    for (Map.Entry<Long, SSMRule> entry : newRules.entrySet()) {
      Long ruleId = entry.getKey();
      SSMRule ssmRule = entry.getValue();
      RuleContainer ruleContainer = new RuleContainer(ssmRule, updateDuration, dfsClient);
      ruleMaps.put(ruleId, ruleContainer);
    }
    try {
      return newRules.size();
    } finally {
      newRules.clear();
    }
  }

  /**
   * Read FilesAccessInfo to refresh file information of DecisionMaker
   * @param filesAccessInfo
   */
  public void getFilesAccess(FilesAccessInfo filesAccessInfo){
    // update fileMap
    fileMap.updateFileMap(filesAccessInfo);
    // process nnEvent
    fileMap.processNnEvents(filesAccessInfo);

    // update ruleMaps
    updateRules();
    if (!isInitialized) {
      isInitialized = true;
    }
    else {
      for (Map.Entry<Long, RuleContainer> entry : ruleMaps.entrySet()) {
        entry.getValue().update(filesAccessInfo);
      }
    }
  }

  /**
   * Run Mover tool to move a file.
   * @param fileActions
   * @return true if move succeed; else false
   */
  private void runExecutor(HashMap<String, Action> fileActions) {
    //ExecutorService exec = Executors.newCachedThreadPool();
    for (Map.Entry<String, Action> fileAction : fileActions.entrySet()) {
      String fileName = fileAction.getKey();
      Action action = fileAction.getValue();
      switch (action) {
        case ARCHIVE:
          /*if (!fileMap.get(fileName).isOnArchive()) {
            fileMap.get(fileName).setArchive();
            exec.execute(new MoverExecutor(dfsClient, conf, fileName, action));
          }*/
          break;
        case CACHE:
          /*if (!fileMap.get(fileName).isOnCache()) {
            fileMap.get(fileName).setCache();
            exec.execute(new MoverExecutor(dfsClient, conf, fileName, action));
          }*/
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
    for (Map.Entry<String, Action> entry : fileActions.entrySet()) {
      LOG.info("fileActions : fileName = " + entry.getKey() + "; action = " + entry.getValue());
    }
    runExecutor(fileActions);
  }

  public HashMap<Long, RuleContainer> getRuleMaps() {
    return ruleMaps;
  }

  public FileAccessMap getFileMap() {
    return fileMap;
  }
}


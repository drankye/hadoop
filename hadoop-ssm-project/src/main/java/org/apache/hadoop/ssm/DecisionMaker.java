package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.hdfs.protocol.NNEvent;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

import java.util.HashMap;
import java.util.HashSet;

import static org.apache.hadoop.hdfs.protocol.NNEvent.EV_DELETE;
import static org.apache.hadoop.hdfs.protocol.NNEvent.EV_RENAME;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  private FileAccessMap fileMap;
  private HashMap<Long, RuleContainer> ruleMaps;
  private int threshold; // the threshold of access number to move file to SSD
  private String storagePolicy; // the storagePolicy to run Mover
  private HashSet<String> newFilesExceedThreshold;

  public DecisionMaker(int threshold) {
    this(threshold, "ONE_SSD");
  }

  public DecisionMaker(int threshold, String storagePolicy) {
    fileMap = new FileAccessMap();
    newFilesExceedThreshold = new HashSet<String>();
    this.threshold = threshold;
    this.storagePolicy = storagePolicy;
  }

  public FileAccessMap getFileMap() {
    return fileMap;
  }

  public String getStoragePolicy() {return storagePolicy;}

  public void setStoragePolicy(String storagePolicy) {this.storagePolicy = storagePolicy;}

  /**
   * Read FilesAccessInfo to refresh file information of DecisionMaker
   * @param filesAccessInfo
   */
  private void getFilesAccess(FilesAccessInfo filesAccessInfo){
    newFilesExceedThreshold.clear();

    // update fileMap
    fileMap.updateFileMap(filesAccessInfo);

    // process nnEvent
    fileMap.processNnEvents(filesAccessInfo);
  }

  /**
   * Run Mover tool to move a file.
   * @param dfsClient
   * @param conf
   * @param fileName
   * @return true if move succeed; else false
   */
  private boolean runMover(DFSClient dfsClient, Configuration conf, String fileName) {
    FileAccess fileAccess = fileMap.get(fileName);
    if (fileAccess == null) {
      return false;
    }
    fileAccess.isMoving = true;
    fileAccess.isOnSSD = false;

    try {
      dfsClient.setStoragePolicy(fileName, storagePolicy);
    } catch (Exception e) {
      return false;
    }
    int rc = 0;
//    int rc = ToolRunner.run(conf, new Mover.Cli(),
//            new String[] {"-p", fileName});
    if (rc == 0) { // Mover success
      fileAccess.isMoving = false;
      fileAccess.isOnSSD = true;
      return true;
    }
    else {
      fileAccess.isMoving = false;
      fileAccess.isOnSSD = false;
      return false;
    }
  }

  private void calculateMover(DFSClient dfsClient, Configuration conf) {
    for (String fileName : newFilesExceedThreshold) {
      runMover(dfsClient, conf, fileName);
    }
  }

  public void execution(DFSClient dfsClient, Configuration conf, FilesAccessInfo filesAccessInfo) {
    getFilesAccess(filesAccessInfo);
    calculateMover(dfsClient, conf);
  }
}


package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  private HashMap<String, FileAccess> fileMap;
  private HashMap<String, FileAccess> onSSDMap;
  private int threshold; // the threshold of access number to move file to SSD
  private HashSet<String> newFilesExceedThreshold;

  class FileAccess implements Comparable<FileAccess>{
    String fileName;
    Integer accessCount;
    Boolean isOnSSD;
    Boolean isMoving;

    public FileAccess(String fileName, Integer accessCount) {
      this(fileName, accessCount, false);
    }

    public FileAccess(String fileName, Integer accessCount, Boolean isOnSSD) {
      this.fileName = fileName;
      this.accessCount = accessCount;
      this.isOnSSD = isOnSSD;
      isMoving = false;
    }

    public int compareTo(FileAccess other) {
      int result;
      result = this.accessCount.compareTo(other.accessCount);
      if (result == 0) {
        result = this.fileName.compareTo(other.fileName);
      }
      return result;
    }
  }


  public DecisionMaker(int threshold) {
    fileMap = new HashMap<String, FileAccess>();
    onSSDMap = new HashMap<String, FileAccess>();
    newFilesExceedThreshold = new HashSet<String>();
    this.threshold = threshold;
  }

  public HashMap<String, FileAccess> getFileMap() {
    return fileMap;
  }

  public HashMap<String, FileAccess> getOnSSDMap() {
    return onSSDMap;
  }

  /**
   * Read FilesAccessInfo to refresh file information of DecisionMaker
   * @param filesAccessInfo
   */
  private void getFilesAccess(FilesAccessInfo filesAccessInfo){
    newFilesExceedThreshold.clear();
    for (int i = 0; i < filesAccessInfo.getFilesAccessed().size(); i++) {
      String fileName = filesAccessInfo.getFilesAccessed().get(i);
      Integer fileAccessCount = filesAccessInfo.getFilesAccessCounts().get(i);
      FileAccess fileAccess = fileMap.get(fileName);
      if (fileAccess != null) {
        fileAccess.accessCount += fileAccessCount;
      }
      else {
        fileMap.put(fileName, new FileAccess(fileName, fileAccessCount));
      }
      // check if the file exceeds threshold
      if (fileAccess.accessCount >= threshold && !fileAccess.isMoving && !fileAccess.isOnSSD) {
        newFilesExceedThreshold.add(fileName);
      }
    }
  }

  private boolean moveToSSD(DFSClient dfsClient, Configuration conf, String fileName) {
    FileAccess fileAccess = fileMap.get(fileName);
    if (fileAccess == null) {
      return false;
    }
    fileAccess.isMoving = true;
    fileAccess.isOnSSD = false;
    onSSDMap.put(fileName, fileAccess);
    try {
      dfsClient.setStoragePolicy(fileName, "ONE_SSD");
    } catch (Exception e) {
      return false;
    }
    int rc = ToolRunner.run(conf, new Mover.Cli(),
            new String[] {"-p", fileName});
    if (rc == 0) {
      fileAccess.isMoving = false;
      fileAccess.isOnSSD = true;
    }
    else {
      fileAccess.isMoving = false;
      fileAccess.isOnSSD = false;
    }
    return true;
  }

  private void calculateMover(DFSClient dfsClient, Configuration conf) {
    for (String fileName : newFilesExceedThreshold) {
      moveToSSD(dfsClient, conf, fileName);
    }
  }

  public void execution(DFSClient dfsClient, Configuration conf, FilesAccessInfo filesAccessInfo) {
    getFilesAccess(filesAccessInfo);
    calculateMover(dfsClient, conf);
  }
}


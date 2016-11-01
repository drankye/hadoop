package org.apache.hadoop.ssm;

import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;

import java.util.HashMap;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  private HashMap<String, FileAccess> fileMap;
  private HashMap<String, FileAccess> onSSDMap;
  private int threshold; // the threshold of access number to move file to SSD

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
  public void getFilesAccess(FilesAccessInfo filesAccessInfo){
    for (int i = 0; i < filesAccessInfo.getFilesAccessed().size(); i++) {
      String fileName = filesAccessInfo.getFilesAccessed().get(i);
      Integer fileAccessCount = filesAccessInfo.getFilesAccessCounts().get(i);
      FileAccess fileAccess = fileMap.get(fileName);
      if (fileAccess != null) {
        fileAccess.accessCount = fileAccessCount;
      }
      else {
        fileMap.put(fileName, new FileAccess(fileName, fileAccessCount));
      }
    }
  }



}


package org.apache.hadoop.ssm;

import java.sql.Timestamp;

/**
 * Created by root on 11/4/16.
 */
class FileAccess implements Comparable<FileAccess>{
  String fileName;
  Integer accessCount;
  Timestamp createTime;
  Boolean isOnSSD;
  Boolean isMoving;

  public FileAccess(String fileName, Integer accessCount) {
    this(fileName, accessCount, null, false);
  }

  public FileAccess(String fileName, Timestamp createTime) { this(fileName, 0, createTime, false);}

  public FileAccess(String fileName, Integer accessCount, Timestamp createTime) {
    this(fileName, accessCount, createTime, false);
  }

  public FileAccess(String fileName, Integer accessCount, Timestamp createTime, Boolean isOnSSD) {
    this.fileName = fileName;
    this.accessCount = accessCount;
    this.createTime = createTime;
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

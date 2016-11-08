package org.apache.hadoop.ssm;

/**
 * Created by root on 11/4/16.
 */
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

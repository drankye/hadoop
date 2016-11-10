package org.apache.hadoop.ssm;

/**
 * Created by root on 11/4/16.
 */
class FileAccess implements Comparable<FileAccess>{
  String fileName;
  Integer accessCount;
  Long createTime;
  Boolean isOnSSD;
  Boolean isOnArchive;
  Boolean isOnCache;

  public FileAccess(String fileName, Integer accessCount) {
    this(fileName, accessCount, null, false, false, false);
  }

  public FileAccess(String fileName, Long createTime) { this(fileName, 0, createTime, false, false, false);}

  public FileAccess(String fileName, Integer accessCount, Long createTime) {
    this(fileName, accessCount, createTime, false, false, false);
  }

  public FileAccess(String fileName, Integer accessCount, Long createTime,
                    Boolean isOnSSD, Boolean isOnArchive, Boolean isOnCache) {
    this.fileName = fileName;
    this.accessCount = accessCount;
    this.createTime = createTime;
    this.isOnSSD = isOnSSD;
    this.isOnArchive = isOnArchive;
    this.isOnCache = isOnCache;
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

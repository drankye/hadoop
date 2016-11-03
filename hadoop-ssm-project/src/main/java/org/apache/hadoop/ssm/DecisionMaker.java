package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.hdfs.server.mover.Mover;
import org.apache.hadoop.util.ToolRunner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by root on 10/31/16.
 */
public class DecisionMaker {
  private HashMap<String, FileAccess> fileMap;
  private HashMap<String, FileAccess> onSSDMap;
  private int threshold; // the threshold of access number to move file to SSD
  private String storagePolicy; // the storagePolicy to run Mover
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
    this(threshold, "ONE_SSD");
  }

  public DecisionMaker(int threshold, String storagePolicy) {
    fileMap = new HashMap<String, FileAccess>();
    onSSDMap = new HashMap<String, FileAccess>();
    newFilesExceedThreshold = new HashSet<String>();
    this.threshold = threshold;
    this.storagePolicy = storagePolicy;
  }

  public HashMap<String, FileAccess> getFileMap() {
    return fileMap;
  }

  public HashMap<String, FileAccess> getOnSSDMap() {
    return onSSDMap;
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
    if (filesAccessInfo.getFilesAccessed() != null) {
      for (int i = 0; i < filesAccessInfo.getFilesAccessed().size(); i++) {
        String fileName = filesAccessInfo.getFilesAccessed().get(i);
        Integer fileAccessCount = filesAccessInfo.getFilesAccessCounts().get(i);
        FileAccess fileAccess = fileMap.get(fileName);
        if (fileAccess != null) {
          fileAccess.accessCount += fileAccessCount;
        } else {
          fileAccess = new FileAccess(fileName, fileAccessCount);
          fileMap.put(fileName, fileAccess);
        }
        // check if the file exceeds threshold
        if (fileAccess.accessCount >= threshold && !fileAccess.isMoving && !fileAccess.isOnSSD) {
          newFilesExceedThreshold.add(fileName);
        }
      }
    }

    // process rename
    if (filesAccessInfo.getFilesRenamedSrc() != null) {
      for (int i = 0; i < filesAccessInfo.getFilesRenamedSrc().size(); i++) {
        String srcName = filesAccessInfo.getFilesRenamedSrc().get(i);
        String dstName = filesAccessInfo.getFilesRenamedDst().get(i);
        // update fileMap, onSSDMap and newFilesExceedThreshold
        FileAccess srcFileAccess = fileMap.get(srcName);
        FileAccess dstFileAccess = fileMap.get(dstName);
        if (srcFileAccess != null) { // src must be a file
          // update fileMap
          fileMap.remove(srcName);
          srcFileAccess.fileName = dstName;
          if (dstFileAccess != null) {
            srcFileAccess.accessCount += dstFileAccess.accessCount;
          }
          fileMap.put(dstName, srcFileAccess);
          // update onSSDMap
          if (srcFileAccess.isOnSSD) {
            onSSDMap.remove(srcName);
            onSSDMap.put(dstName, srcFileAccess);
          }
          // update newFilesExceedThreshold
          newFilesExceedThreshold.remove(srcName);
          if (srcFileAccess.accessCount >= threshold && !srcFileAccess.isMoving && !srcFileAccess.isOnSSD) {
            newFilesExceedThreshold.add(dstName);
          }
        } else { // search all files under this directory
          HashMap<String, FileAccess> renameMap = new HashMap<String, FileAccess>();
          for (Iterator<Map.Entry<String, FileAccess>> it = fileMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, FileAccess> entry = it.next();
            String fileName = entry.getKey();
            FileAccess fileAccess = entry.getValue();
            if (fileName.startsWith(srcName) &&
                    (srcName.charAt(srcName.length() - 1) == '/' || fileName.charAt(srcName.length()) == '/')) {
              // update fileMap
              fileAccess.fileName = dstName + fileName.substring(srcName.length());
              dstFileAccess = fileMap.get(fileAccess.fileName);
              if (dstFileAccess != null) {
                fileAccess.accessCount += dstFileAccess.accessCount;
              }
              it.remove();
              renameMap.put(fileAccess.fileName, fileAccess);
              // update onSSDMap
              if (fileAccess.isOnSSD) {
                onSSDMap.remove(fileName);
                onSSDMap.put(fileAccess.fileName, fileAccess);
              }
              // update newFilesExceedThreshold
              newFilesExceedThreshold.remove(fileName);
              if (fileAccess.accessCount >= threshold && !fileAccess.isMoving && !fileAccess.isOnSSD) {
                newFilesExceedThreshold.add(fileAccess.fileName);
              }
            }
          }
          fileMap.putAll(renameMap);
        }
      }
    }

    // process delete
    if (filesAccessInfo.getFilesDeleted() != null) {
      for (String deleteFileName : filesAccessInfo.getFilesDeleted()) {
        fileMap.remove(deleteFileName);
        onSSDMap.remove(deleteFileName);
        newFilesExceedThreshold.remove(deleteFileName);
      }
    }
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
    int rc = ToolRunner.run(conf, new Mover.Cli(),
            new String[] {"-p", fileName});
    if (rc == 0) { // Mover success
      fileAccess.isMoving = false;
      fileAccess.isOnSSD = true;
      onSSDMap.put(fileName, fileAccess);
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


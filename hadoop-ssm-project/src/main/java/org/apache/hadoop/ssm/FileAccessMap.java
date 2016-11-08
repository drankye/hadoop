package org.apache.hadoop.ssm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by root on 11/4/16.
 */
public class FileAccessMap {
  private HashMap<String, FileAccess> fileMap;

  public FileAccessMap() {
    fileMap = new HashMap<String, FileAccess>();
  }

  public HashMap<String, FileAccess> getMap() {return fileMap;}

  public FileAccess get(String fileName) {
    return fileMap.get(fileName);
  }

  public FileAccess put(String fileName, FileAccess fileAccess) {
    return fileMap.put(fileName, fileAccess);
  }

  public FileAccess remove(String fileName) {
    return fileMap.remove(fileName);
  }

  public boolean containsKey(String fileName) {
    return fileMap.containsKey(fileName);
  }

  public void rename(String srcName, String dstName){
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
    }
    else { // search all files under this directory
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
        }
      }
      fileMap.putAll(renameMap);
    }
  }

  public void delete(String name) {
    if (fileMap.containsKey(name)) {
      fileMap.remove(name);
    }
    else {
      for (Iterator<Map.Entry<String, FileAccess>> it = fileMap.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<String, FileAccess> entry = it.next();
        String fileName = entry.getKey();
        if (fileName.startsWith(name) &&
                (name.charAt(name.length() - 1) == '/' || fileName.charAt(name.length()) == '/')) {
          it.remove();
        }
      }
    }
  }
}

package org.apache.hadoop.hdfs.protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FilesAccessInfo {
    private List<String> filesAccessed;
    private List<Integer> filesAccessCounts;

    public FilesAccessInfo(Map<String, Integer> countsMap) {
        this.filesAccessed = new ArrayList<>(countsMap.keySet());
        this.filesAccessCounts = new ArrayList<>(countsMap.values());
    }

    public FilesAccessInfo(List<String> files, List<Integer> counts) {
        filesAccessed = files;
        filesAccessCounts = counts;
    }

    public Map<String, Integer> toHashMap() {
        if (filesAccessed == null || filesAccessCounts == null) {
            return null;
        }
        Map<String, Integer> ret = new HashMap<>();
        for (int i = 0; i < filesAccessed.size(); i++) {
            ret.put(filesAccessed.get(i), filesAccessCounts.get(i));
        }
        return ret;
    }

    public List<String> getFilesAccessed() {
        return filesAccessed;
    }

    public List<Integer> getFilesAccessCounts() {
        return filesAccessCounts;
    }
}

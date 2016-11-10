package org.apache.hadoop.ssm;

import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.ssm.api.Expression.*;

import java.time.Duration;
import java.util.*;

/**
 * Created by root on 11/8/16.
 */
public class RuleContainer {
  private long id;
  private Property property;
  private FileFilterRule fileFilterRule;
  private PropertyFilterRule propertyFilterRule;
  private Action action;

  private long updateDuration;
  private DFSClient dfsClient;
  // Window maps to store access count
  private WindowMap windowMap;

  // Age map
  private AgeMap ageMap;

  public RuleContainer(SSMRule ruleObject, long updateDuration, DFSClient dfsClient) {
    this.id = ruleObject.getId();
    this.property = ((PropertyFilterRule)ruleObject.root().value()).property();
    this.action = ruleObject.action();
    this.fileFilterRule = ruleObject.fileFilterRule();
    this.propertyFilterRule = (PropertyFilterRule)ruleObject.root().value();
    this.updateDuration = updateDuration;
    this.dfsClient = dfsClient;
    switch (property) {
      case ACCESSCOUNT:
        if (propertyFilterRule.propertyManipulation() instanceof Window) {
          long windowSize = ((Window)propertyFilterRule.propertyManipulation()).size().getSeconds();
          long windowStep = ((Window)propertyFilterRule.propertyManipulation()).step().getSeconds();
          windowMap = new WindowMap(windowStep, windowSize, updateDuration);
        }
        break;
      case AGE:
        ageMap = new AgeMap((Long) propertyFilterRule.threshold());
        break;
      default:
    }
  }

  /**
   * Update information with filesAccessInfo
   * @param filesAccessInfo
   */
  public void update(FilesAccessInfo filesAccessInfo) {
    switch (property) {
      case ACCESSCOUNT:
        accessCountUpdate(filesAccessInfo);
        break;
      case AGE:
        ageUpdate(filesAccessInfo);
        break;
      default:
    }
  }

  private void accessCountUpdate(FilesAccessInfo filesAccessInfo) {
    if (propertyFilterRule.propertyManipulation() instanceof Window) {
      windowMap.update(filesAccessInfo);
    }
  }

  private void ageUpdate(FilesAccessInfo filesAccessInfo) {
    ageMap.update(filesAccessInfo);
  }

  /**
   * Evaluate which files should take action
   * @return List of file names which need to take action
   */
  public HashMap<String, Action> actionEvaluator(FileAccessMap fileMap) {
    switch (property) {
      case ACCESSCOUNT:
        return accessCountActionEvaluator(fileMap);
      case AGE:
        return ageActionEvaluator();
      default:
        return null;
    }
  }

  private HashMap<String, Action> accessCountActionEvaluator(FileAccessMap fileMap) {
    HashMap<String, Action> result = new HashMap<String, Action>();
    if (propertyFilterRule.propertyManipulation() instanceof Window) {
      result = windowMap.evaluate();
    }
    else if (propertyFilterRule.propertyManipulation() instanceof Historical$) {
      for (Map.Entry<String, FileAccess> entry : fileMap.entrySet()) {
        String fileName = entry.getKey();
        FileAccess fileAccess = entry.getValue();
        if (fileFilterRule.meetCondition(fileName) && propertyFilterRule.meetCondition(fileAccess.accessCount)) {
          result.put(fileName, action);
        }
      }
    }
    return result;
  }

  private HashMap<String, Action> ageActionEvaluator() {
    return ageMap.evaluate();
  }

  /**
   * WindowMap class to maintain information for windowed access count
   */
  class WindowMap {
    private LinkedList<FileAccessMap> windowMaps;
    private int mapNumber;
    private FileAccessMap fileAccessMapInWindow;
    private long windowStep;
    private long windowSize;
    private long updateDuration;
    private State state;

    public WindowMap(long windowStep, long windowSize, long updateDuration) {
      this.windowStep = windowStep;
      this.windowSize = windowSize;
      this.updateDuration = updateDuration;
      this.mapNumber = (int)(windowSize/windowStep);
      windowMaps = new LinkedList<FileAccessMap>();
      fileAccessMapInWindow = new FileAccessMap();
      state = new State();
    }

    class State {
      private int current;

      State() { this.current = 0;}

      // if a new map should be created into windowMaps
      boolean createNewMap() {
        return current == 0;
      }

      // if the newest map should be added to fileAccessMapInWindow
      boolean addNewMap() {
        return current >= windowStep/updateDuration - 1;
      }

      // if the oldest map should be removed from fileAccessMapInWindow and windowMaps
      boolean removeOldMap() {
        return (current >= windowStep/updateDuration - 1) && (windowMaps.size() > mapNumber);
      }

      // if it is ready for windowed access count evaluate
      boolean readyForEvaluate() {
        return (current == 0) && (windowMaps.size() == mapNumber);
      }

      // update state at the end of each update
      void updateState() {
        current ++;
        if (current >= windowStep/updateDuration - 1) {
          current = 0;
        }
      }
    }

    public void update(FilesAccessInfo filesAccessInfo) {
      FileAccessMap currentMap;
      if (state.createNewMap()) {
        currentMap = new FileAccessMap();
        windowMaps.addLast(currentMap);
      }
      else {
        currentMap = windowMaps.getLast();
      }
      currentMap.updateFileMap(filesAccessInfo, fileFilterRule);
      // process NnEvents in all maps
      for (Iterator<FileAccessMap> it = windowMaps.iterator(); it.hasNext(); ) {
        FileAccessMap fileAccessMap = it.next();
        fileAccessMap.processNnEvents(filesAccessInfo, fileFilterRule);
      }
      fileAccessMapInWindow.processNnEvents(filesAccessInfo, fileFilterRule);
      // add the new map to fileAccessMapInWindow when a windowStep is reached
      // meanwhile remove the first map if mapNumber is reached
      if (state.addNewMap()) {
        for (Map.Entry<String, FileAccess> entry : currentMap.entrySet()) {
          String fileName = entry.getKey();
          FileAccess fileAccess = entry.getValue();
          FileAccess fileAccessTotal = fileAccessMapInWindow.get(fileName);
          if (fileAccessTotal != null) {
            fileAccessTotal.accessCount += fileAccess.accessCount;
          }
          else {
            fileAccessMapInWindow.put(fileName, fileAccess);
          }
        }
      }
      if (state.removeOldMap()) {
        for (Map.Entry<String, FileAccess> entry : windowMaps.getFirst().entrySet()) {
          String fileName = entry.getKey();
          FileAccess fileAccess = entry.getValue();
          FileAccess fileAccessTotal = fileAccessMapInWindow.get(fileName);
          if (fileAccessTotal != null) {
            fileAccessTotal.accessCount -= fileAccess.accessCount;
          }
        }
      }
      state.updateState();
    }

    public HashMap<String, Action> evaluate() {
      HashMap<String, Action> result = new HashMap<String, Action>();
      if (state.readyForEvaluate()) {
        for (Map.Entry<String, FileAccess> entry : fileAccessMapInWindow.entrySet()) {
          if (propertyFilterRule.meetCondition(entry.getValue().accessCount)) {
            result.put(entry.getKey(), action);
          }
        }
      }
      return result;
    }
  }

  /**
   * AgeMap class to maintain information for age map
   */
  class AgeMap {
    private FileAccessMap ageMap;
    private Long lastUpdateTime;
    private Long ageThreshold;

    public AgeMap(long ageThreshold) {
      this.ageThreshold = ageThreshold;
      ageMap = new FileAccessMap();
      lastUpdateTime = null;
    }

    public void update(FilesAccessInfo filesAccessInfo) {
      ageMap.processNnEvents(filesAccessInfo, fileFilterRule);
    }

    public HashMap<String, Action> evaluate() {
      HashMap<String, Action> result = new HashMap<String, Action>();
      if (lastUpdateTime == null || System.currentTimeMillis() - lastUpdateTime > ageThreshold) {
        lastUpdateTime = System.currentTimeMillis();
        List<String> fileNames = null;
        List<Long> createTimes = null;
        for (int i = 0; i <  fileNames.size(); i++) {
          String fileName = fileNames.get(i);
          Long createTime = createTimes.get(i);
          if (fileFilterRule.meetCondition(fileName)) {
            ageMap.put(fileName, new FileAccess(fileName, createTime));
          }
          if (propertyFilterRule.meetCondition(System.currentTimeMillis() - createTime)) {
            result.put(fileName, action);
          }
        }
      }
      else {
        for (Map.Entry<String, FileAccess> entry : ageMap.entrySet()) {
          if (propertyFilterRule.meetCondition(System.currentTimeMillis() - entry.getValue().createTime)) {
            result.put(entry.getKey(), action);
          }
        }
      }
      return result;
    }
  }

}

package org.apache.hadoop.ssm;

import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.hdfs.protocol.NNEvent;
import org.apache.hadoop.ssm.api.Expression.*;

import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

import static org.apache.hadoop.hdfs.protocol.NNEvent.EV_DELETE;
import static org.apache.hadoop.hdfs.protocol.NNEvent.EV_RENAME;

/**
 * Created by root on 11/8/16.
 */
public class RuleContainer {
  private long id;
  private Property property;
  private FileFilterRule fileFilterRule;
  private PropertyFilterRule propertyFilterRule;
  private Action action;

  // Window maps to store access count
  static int MAX_MAP_NUMBER = 50;
  private LinkedList<FileAccessMap> windowMaps;
  private int mapNumber;
  private FileAccessMap fileAccessMapInWindow;
  private long windowStep;
  private long windowSize;
  private long updateDuration;

  // Age map
  private FileAccessMap ageMap;
  private Timestamp lastUpdateTime;
  private long ageThreshold;

  public RuleContainer(SSMRule ruleObject, long updateDuration) {
    this.updateDuration = updateDuration;
    this.id = ruleObject.getId();
    this.property = ((PropertyFilterRule)ruleObject.root().value()).property();
    this.action = ruleObject.action();
    this.fileFilterRule = ruleObject.fileFilterRule();
    this.propertyFilterRule = (PropertyFilterRule)ruleObject.root().value();
    switch (property) {
      case ACCESSCOUNT:
        if (propertyFilterRule.propertyManipulation() instanceof Window) {
          windowSize = ((Window)propertyFilterRule.propertyManipulation()).size().getSeconds();
          windowStep = ((Window)propertyFilterRule.propertyManipulation()).step().getSeconds();
          /*if (windowStep < updateDuration) {
            throw new RuleParameterException("step is smaller than update duration");
          }
          if (windowSize%windowStep != 0) {
            throw new RuleParameterException("size cannot be divided by step");
          }*/
          mapNumber = (int)(windowSize/windowStep);
          /*if (mapNumber > MAX_MAP_NUMBER) {
            throw new RuleParameterException("step is too small");
          }*/
          windowMaps = new LinkedList<FileAccessMap>();
        }
        break;
      case AGE:
        ageMap = new FileAccessMap();
        lastUpdateTime = null;
        break;
      default:
    }
  }

  public void setUpdateDuration(long updateDuration) { this.updateDuration = updateDuration;}

  public long getUpdateDuration() { return updateDuration;}

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
      FileAccessMap newWindow = new FileAccessMap();
      newWindow.updateFileMap(filesAccessInfo, fileFilterRule);
      windowMaps.addLast();
    }
  }

  private void ageUpdate(FilesAccessInfo filesAccessInfo) {
    ageMap.processNnEvents(filesAccessInfo, fileFilterRule);
  }

  /**
   * Evaluate which files should take action
   * @return List of file names which need to take action
   */
  public List<String> actionEvaluator(FileAccessMap fileMap) {
    switch (property) {
      case ACCESSCOUNT:
        return accessCountActionEvaluator(fileMap);
      case AGE:
        return ageActionEvaluator();
      default:
        return null;
    }
  }

  private List<String> accessCountActionEvaluator(FileAccessMap fileMap) {
    List<String> result = new ArrayList<String>();
    if (propertyFilterRule.propertyManipulation() instanceof Window) {
      for (Map.Entry<String, FileAccess> entry : fileAccessMapInWindow.entrySet()) {
        String fileName = entry.getKey();
        FileAccess fileAccess = entry.getValue();
        if (propertyFilterRule.meetCondition(fileAccess.accessCount)) {
          result.add(fileName);
        }
      }
    }
    else if (propertyFilterRule.propertyManipulation() instanceof Historical$) {
      for (Map.Entry<String, FileAccess> entry : fileMap.entrySet()) {
        String fileName = entry.getKey();
        FileAccess fileAccess = entry.getValue();
        if (fileFilterRule.meetCondition(fileName) && propertyFilterRule.meetCondition(fileAccess.accessCount)) {
          result.add(fileName);
        }
      }
    }
    return result;
  }

  private List<String> ageActionEvaluator() {

  }

}

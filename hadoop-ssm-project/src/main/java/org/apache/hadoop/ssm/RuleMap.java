package org.apache.hadoop.ssm;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by root on 11/4/16.
 */
public class RuleMap {
  private long id;
  private LinkedList<FileAccessMap> ruleMaps;
  private RuleObject ruleObject;
  private int slideStep;

  public RuleMap(RuleObject ruleObject) {
    this.ruleObject = ruleObject;
    this.id = ruleObject.getId();
    ruleMaps = new FileAccessMap[ruleObject];
  }

}

package org.apache.hadoop.ssm;

import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by root on 11/1/16.
 */
public class DecisionMakerTest {
  @Test
  public void testDecisionMaker() {
    DecisionMaker decisionMaker = new DecisionMaker(50);
    Map<String, Integer> countMap = new HashMap<String, Integer>();
    // a0 ~ a9, 100,90,80,...,10
    for (int i = 0; i < 10; i++) {
      countMap.put("a" + i, 10*i);
    }
    FilesAccessInfo filesAccessInfo = new FilesAccessInfo(countMap);
    decisionMaker.execution(filesAccessInfo);
  }
}

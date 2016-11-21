package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.ssm.api.Expression.*;
import org.apache.hadoop.ssm.parse.SSMRuleParser;
import org.junit.Test;

/**
 * Created by root on 11/10/16.
 */
public class RuleContainerTest {
  public static final Configuration conf;
  static {
    conf = new HdfsConfiguration();
  }

  @Test
  public void test1() {
    SSMRule ruleObject = SSMRuleParser.parseAll("file.path matches('/A/[a-z]*') : accesscount(10 min) >= 10 | cache").get();
    long updateDuration = 1*60*1000;
    DFSClient dfsClient = null;
    try {
      dfsClient = new DFSClient(DFSUtilClient.getNNAddress(conf), conf);
    } catch (Exception e) {

    }
    RuleContainer ruleContainer = new RuleContainer(ruleObject, updateDuration, dfsClient);

  }
}

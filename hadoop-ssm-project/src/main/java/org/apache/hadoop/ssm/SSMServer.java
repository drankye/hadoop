package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSUtilClient;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.protocol.FilesAccessInfo;
import org.apache.hadoop.ssm.api.Expression.*;
import org.apache.hadoop.ssm.parse.SSMRuleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by root on 11/1/16.
 */
public class SSMServer {
  public static final Configuration conf;
  static {
    conf = new HdfsConfiguration();
  }
  public static final Logger LOG = LoggerFactory.getLogger(DFSClient.class);

  static class DecisionMakerTask extends TimerTask {
    private DFSClient dfsClient;
    private DecisionMaker decisionMaker;

    public DecisionMakerTask(DFSClient dfsClient, DecisionMaker decisionMaker) {
      this.dfsClient = dfsClient;
      this.decisionMaker = decisionMaker;
    }

    @Override
    public void run() {
      LOG.info("Update all information:");
      FilesAccessInfo filesAccessInfo;
      try {
        filesAccessInfo = dfsClient.getFilesAccessInfo();
      } catch (Exception e) {
        LOG.warn("getFilesAccessInfo exception");
        return;
      }
      decisionMaker.execution(dfsClient, conf, filesAccessInfo);
    }
  }

  public static void main(String[] args) throws Exception {
    DFSClient dfsClient = new DFSClient(DFSUtilClient.getNNAddress(conf), conf);
    long updateDuration = 1*60;

    DecisionMaker decisionMaker = new DecisionMaker(dfsClient, conf, updateDuration);
    SSMRule ruleObject = SSMRuleParser.parseAll("file.path matches('/A/[a-z]*') : accessCount (10 min) >= 50 | cache").get();
    decisionMaker.addRule(ruleObject);

    Timer timer = new Timer();
    timer.schedule(new DecisionMakerTask(dfsClient, decisionMaker), 2*1000L, updateDuration*1000L);
  }
}

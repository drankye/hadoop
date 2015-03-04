/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.web;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.net.NetUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * This test verifies that WebHDFS can recognize the set of configuration
 * properties, started with "hadoop.http.authentication.*".
 */
public class TestWebHdfsWithAuthFilterPlusConfig {

  private static final String COOKIE_DOMAIN_PROPERTY =
      "hadoop.http.authentication.cookie.domain";
  private static final String TEST_COOKIE_DOMAIN = "from-hadoop-auth";

  public static final class MyFilter extends AuthFilter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

      /**
       * If it gets the two test properties then it's assumed to be authorized,
       * otherwise, rejected. The logic is just for the test purpose.
       */
      String cookieDomain = getCookieDomain();
      String cookiePath = getCookiePath();
      boolean isAuthorized = cookieDomain != null &&
          cookieDomain.equals(TEST_COOKIE_DOMAIN);
      if (isAuthorized) {
        // assume it's authorized owing to the presence of the properties.
        chain.doFilter(request, response);
      } else {
        ((HttpServletResponse) response)
            .sendError(HttpServletResponse.SC_FORBIDDEN);
      }
    }
  }

  private Configuration conf;
  private MiniDFSCluster cluster;
  private FileSystem fs;

  @Test
  public void testWebHdfsAuthFilterAuthorized() throws IOException {
    test(true);
  }

  @Test
  public void testWebHdfsAuthFilterRejected() throws IOException {
    test(false);
  }

  private void test(boolean withHadoopAuthProperty) throws IOException {
    conf = new Configuration();
    conf.set(DFSConfigKeys.DFS_WEBHDFS_AUTHENTICATION_FILTER_KEY,
        MyFilter.class.getName());
    conf.set(DFSConfigKeys.DFS_NAMENODE_HTTP_ADDRESS_KEY, "localhost:0");

    if (withHadoopAuthProperty) {
      // As test properties from hadoop-auth related.
      conf.set(COOKIE_DOMAIN_PROPERTY, TEST_COOKIE_DOMAIN);
    }

    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(1).build();
    InetSocketAddress addr = cluster.getNameNode().getHttpAddress();
    fs = FileSystem.get(URI.create("webhdfs://" +
        NetUtils.getHostPortString(addr)), conf);
    cluster.waitActive();

    if (withHadoopAuthProperty) {
      // It's supposed to pass through this since required property is set.
      boolean isPassed = true;
      try {
        fs.getFileStatus(new Path("/"));
      } catch (IOException e) {
        isPassed = false;
      }
      Assert.assertTrue("With HadoopAuth property check", isPassed);
    } else {
      // It's supposed not to pass through this since no property is set.
      try {
        fs.getFileStatus(new Path("/"));
        Assert.fail("The filter fails to block the request");
      } catch (IOException e) {
      }
    }

    fs.close();
    cluster.shutdown();
  }
}

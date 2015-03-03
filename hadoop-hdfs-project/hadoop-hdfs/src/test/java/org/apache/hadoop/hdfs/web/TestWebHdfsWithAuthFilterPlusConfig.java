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

public class TestWebHdfsWithAuthFilterPlusConfig {

  private static final String COOKIE_DOMAIN =
      "hadoop-auth.com";

  public static final class MyFilter extends AuthFilter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
      String cookieDomain = getCookieDomain();
      if (cookieDomain != null && cookieDomain.equals(COOKIE_DOMAIN)) {
        // assume it's authorized.
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
    conf = new Configuration();
    conf.set(DFSConfigKeys.DFS_WEBHDFS_AUTHENTICATION_FILTER_KEY,
        MyFilter.class.getName());
    // This is the test property as one of hadoop-auth related properties.
    conf.set("hadoop.http.authentication.cookie.domain", COOKIE_DOMAIN);
    conf.set(DFSConfigKeys.DFS_NAMENODE_HTTP_ADDRESS_KEY, "localhost:0");
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(1).build();
    InetSocketAddress addr = cluster.getNameNode().getHttpAddress();
    fs = FileSystem.get(
        URI.create("webhdfs://" + NetUtils.getHostPortString(addr)), conf);
    cluster.waitActive();

    // getFileStatus() is supposed to pass through this.
    boolean isPassed = true;
    try {
      fs.getFileStatus(new Path("/"));
    } catch (IOException e) {
      isPassed = false;
    }

    fs.close();
    cluster.shutdown();

    Assert.assertTrue(isPassed);
  }

  @Test
  public void testWebHdfsAuthFilterRejected() throws IOException {
    conf = new Configuration();
    conf.set(DFSConfigKeys.DFS_WEBHDFS_AUTHENTICATION_FILTER_KEY,
        MyFilter.class.getName());
    conf.set(DFSConfigKeys.DFS_NAMENODE_HTTP_ADDRESS_KEY, "localhost:0");
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(1).build();
    InetSocketAddress addr = cluster.getNameNode().getHttpAddress();
    fs = FileSystem.get(
        URI.create("webhdfs://" + NetUtils.getHostPortString(addr)), conf);
    cluster.waitActive();

    // getFileStatus() is supposed to pass through with the default filter.
    try {
      fs.getFileStatus(new Path("/"));
      Assert.fail("The filter fails to block the request");
    } catch (IOException e) {
    }

    fs.close();
    cluster.shutdown();
  }
}

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
package org.apache.hadoop.hdfs.server.datanode.erasurecode;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.protocol.BlockECRecoveryCommand.BlockECRecoveryInfo;
import org.apache.hadoop.util.Daemon;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ErasureCodingWorker handles the erasure coding recovery work commands. These
 * commands would be issued from Namenode as part of Datanode's heart beat
 * response. BPOfferService delegates the work to this class for handling EC
 * commands.
 */
@InterfaceAudience.Private
public final class ErasureCodingWorker {
  private static final Logger LOG = DataNode.LOG;
  
  protected final DataNode datanode;
  protected final Configuration conf;

  private ThreadPoolExecutor STRIPED_BLK_RECOVERY_THREAD_POOL;
  protected ThreadPoolExecutor STRIPED_READ_THREAD_POOL;
  protected final int STRIPED_READ_TIMEOUT_MILLIS;
  protected final int STRIPED_READ_BUFFER_SIZE;

  public ErasureCodingWorker(Configuration conf, DataNode datanode) {
    this.datanode = datanode;
    this.conf = conf;

    STRIPED_READ_TIMEOUT_MILLIS = conf.getInt(
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_TIMEOUT_MILLIS_KEY,
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_TIMEOUT_MILLIS_DEFAULT);
    initializeStripedReadThreadPool(conf.getInt(
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_THREADS_KEY, 
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_THREADS_DEFAULT));
    STRIPED_READ_BUFFER_SIZE = conf.getInt(
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_BUFFER_SIZE_KEY,
        DFSConfigKeys.DFS_DATANODE_STRIPED_READ_BUFFER_SIZE_DEFAULT);

    initializeStripedBlkRecoveryThreadPool(conf.getInt(
        DFSConfigKeys.DFS_DATANODE_STRIPED_BLK_RECOVERY_THREADS_KEY,
        DFSConfigKeys.DFS_DATANODE_STRIPED_BLK_RECOVERY_THREADS_DEFAULT));
  }

  private void initializeStripedReadThreadPool(int num) {
    LOG.debug("Using striped reads; pool threads=" + num);

    STRIPED_READ_THREAD_POOL = new ThreadPoolExecutor(1, num, 60,
        TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
        new Daemon.DaemonFactory() {
      private final AtomicInteger threadIndex = new AtomicInteger(0);

      @Override
      public Thread newThread(Runnable r) {
        Thread t = super.newThread(r);
        t.setName("stripedRead-" + threadIndex.getAndIncrement());
        return t;
      }
    }, new ThreadPoolExecutor.CallerRunsPolicy() {
      @Override
      public void rejectedExecution(Runnable runnable, ThreadPoolExecutor e) {
        LOG.info("Execution for striped reading rejected, "
            + "Executing in current thread");
        // will run in the current thread
        super.rejectedExecution(runnable, e);
      }
    });
    STRIPED_READ_THREAD_POOL.allowCoreThreadTimeOut(true);
  }

  private void initializeStripedBlkRecoveryThreadPool(int num) {
    LOG.debug("Using striped block recovery; pool threads=" + num);
    STRIPED_BLK_RECOVERY_THREAD_POOL = new ThreadPoolExecutor(2, num, 60,
        TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
        new Daemon.DaemonFactory() {
          private final AtomicInteger threadIdx = new AtomicInteger(0);

          @Override
          public Thread newThread(Runnable r) {
            Thread t = super.newThread(r);
            t.setName("stripedBlockRecovery-" + threadIdx.getAndIncrement());
            return t;
          }
        });
    STRIPED_BLK_RECOVERY_THREAD_POOL.allowCoreThreadTimeOut(true);
  }

  /**
   * Handles the Erasure Coding recovery work commands.
   * 
   * @param ecTasks
   *          BlockECRecoveryInfo
   */
  public void processErasureCodingTasks(Collection<BlockECRecoveryInfo> ecTasks) {
    for (BlockECRecoveryInfo recoveryInfo : ecTasks) {
      final StripedReconstructor reconstructor =
          new StripedReconstructor(this, recoveryInfo);
      try {
        STRIPED_BLK_RECOVERY_THREAD_POOL
            .submit(new Runnable() {
              @Override
              public void run() {
                reconstructor.run();
              }
            });
      } catch (Throwable e) {
        LOG.warn("Failed to recover striped block "
            + recoveryInfo.getExtendedBlock().getLocalBlock(), e);
      }
    }
  }
}

package org.apache.hadoop.hdfs.server.namenode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.HdfsConstants;
import org.apache.hadoop.hdfs.server.namenode.snapshot.Snapshot;
import org.apache.hadoop.hdfs.util.ReadOnlyList;

import java.io.IOException;

public class StoragePolicyEngine {
	public static final Log LOG = LogFactory.getLog(StoragePolicyEngine.class);
	public static final int HOT_THRESHOLD = 10000;
	
	private FSNamesystem namesystem;
	
	StoragePolicyEngine(FSNamesystem ns, Configuration conf) {
		this.namesystem = namesystem;
	}
	
	void activate() {
		Thread monitor = new Thread(new INodeStateMonitor());
		monitor.start();
	}
	
	void visitCountSetNotify(String src, int clickCount) throws IOException {
		correctStateInt(src, clickCount);
	}
	
	class INodeStateMonitor implements Runnable {

		@Override
		public void run() {
			while(namesystem.isRunning()) {
				INode rootDir = namesystem.dir.rootDir;
				try {
					traversalInodes(rootDir);
				} catch (IOException e1) {
					e1.printStackTrace();
				}	
			
				try {
					Thread.sleep(1000 * 60 * 15);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}
	
	private void traversalInodes(INode iNode) throws IOException {
		final boolean isDir = iNode.isDirectory();
		if (isDir) {
			final INodeDirectory dir = iNode.asDirectory();  
			
			ReadOnlyList<INode> children = dir.getChildrenList(Snapshot.CURRENT_STATE_ID);
			for (INode child : children) {
				traversalInodes(child);
			}
		} else {
			correctState(iNode);
		}
	}
	
	private void correctState(INode iNode) throws IOException {
		int clickCount = namesystem.getClickCount(iNode);
		byte storagePolicyId = iNode.getStoragePolicyID();
		correctStateInt(iNode.getFullPathName(), clickCount);
	}
	
	private void correctStateInt(String src, int clickCount) throws IOException {
		if (clickCount > HOT_THRESHOLD) {
			namesystem.setStoragePolicy(src, HdfsConstants.HOT_STORAGE_POLICY_NAME);
		}
	}
} 

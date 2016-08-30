#!/bin/sh
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
cp $1 ~/hadoop-2.7.2_tmp.tar.gz
cd ~/
tar -xzf hadoop-2.7.2_tmp.tar.gz
rm -f hadoop-2.7.2_tmp.tar.gz
$HADOOP_HOME/sbin/stop-all.sh
cd $SCRIPT_DIR
SCRIPT_DIR/deployHadoop.sh /root/hadoop-2.7.2 /home/sorttest/bins/hadoop-current_running_packet

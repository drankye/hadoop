#!/usr/bin/env bash
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
SORT_TEST=
SORT_TEST_BINS=
HADOOP_LINK=
HADOOP_SRC_DIR=
HADOOP_TARGET_DIR=
HADOOP_CONF_TEMP_DIR=$SORT_TEST_BINS/hadoopconf

HOST=`hostname`

# do the same works on datanodes
WORKLOAD=$SCRIPT_DIR
CLUSTER_UTIL=${WORKLOAD}/util
EXEC_CMD=${CLUSTER_UTIL}/exec-cmd

if [ $# -ge 2 ]
then
      HADOOP_SRC_DIR=$1
      HADOOP_TARGET_DIR=$2
      $SORT_TEST_BINS=$(dirname $(2))
      $SORT_TEST=$(dirname $(SORT_TEST_BINS))
      if [ ! -d $HADOOP_SRC_DIR ]
      then
            echo "$HADOOP_SRC_DIR must both be a directory!!"
            exit -1
      fi
      if [ "${HADOOP_SRC_DIR:0:1}" != "/" -o "${HADOOP_TARGET_DIR:0:1}" != "/" ]
      then
            echo "Path must be absolute!!!"
            exit -2
      fi
      if [ ! -e $HADOOP_SRC_DIR ]
      then
            echo "$HADOOP_SRC_DIR does not exist!!!!"
            exit -3
      fi
      if [ ! -e $HADOOP_CONF_TEMP_DIR ]
      then
	    echo "$HADOOP_CONF_TEMP_DIR doea not exist!!!!"
            exit -4
      fi
      echo "hadoop src location is: $HADOOP_SRC_DIR"
      echo "hadoop target location is: $HADOOP_TARGET_DIR"
else
      echo "Please figure out the absolute source path to a hadoop version, and the target"
      echo "path to be moved to!"
      echo "Usage like: ./deployHadoop.sh <src> <target> "
      exit
fi

if [ -d ${SORT_TEST} -a -e ${SORT_TEST} ]
then
      HADOOP_LINK=${SORT_TEST}/hadoop
else
      echo "DIR $HADOOP_LINK does not exit! Quit!!!"
      exit
fi

echo -e "Delete ${HADOOP_TARGET_DIR}?(y/Y/n/N)\b"
read del
if [ "$del" == "y" -o "$del" == "Y" ]
then
      echo "Copying file to target directory."
      if [ "$HADOOP_TARGET_DIR" == ""${SORT_TEST}"/bins" -o  "$HADOOP_TARGET_DIR" == ""${SORT_TEST}"/bins/" ]; then
          echo "FATAL ERROR: can not delete this file."
          exit
      fi
      if [ -e $HADOOP_TARGET_DIR ]; then
          echo "Deleting Target Dir:$HADOOP_TARGET_DIR"
          rm -rf $HADOOP_TARGET_DIR
      fi
      cp -r $HADOOP_SRC_DIR $HADOOP_TARGET_DIR
      ls -l $HADOOP_LINK
      rm $HADOOP_LINK
      echo "Deleted ${HADOOP_LINK} on $HOST"
      ln -s $HADOOP_TARGET_DIR $HADOOP_LINK
      echo "Created ${HADOOP_LINK} on $HOST"
      ls -l $HADOOP_LINK
      cp ${HADOOP_CONF_TEMP_DIR}/* ${HADOOP_TARGET_DIR}/etc/hadoop/
      echo "------------------ Do All on datanodes--------------------"
      echo "Removing file $HADOOP_TARGET_DIR"
      $EXEC_CMD "rm -rf $HADOOP_TARGET_DIR"
      echo "Copying file from $HOST:$HADOO_TARGETP_DIR into datanodes:$SORT_TEST_BINS"
      $EXEC_CMD "scp -r $HOST:$HADOOP_TARGET_DIR $SORT_TEST_BINS"
      $EXEC_CMD "export HADOOP_LINK=$HADOOP_LINK"
      $EXEC_CMD "ls -l $HADOOP_LINK"
      $EXEC_CMD "rm $HADOOP_LINK"
      $EXEC_CMD "echo "Deleted $HADOOP_LINK on $HOST""
      $EXEC_CMD "ln -s $HADOOP_TARGET_DIR $HADOOP_LINK"
      $EXEC_CMD "echo "Created $HADOOP_LINK on $HOST""
      $EXEC_CMD "ls -l $HADOOP_LINK"
      $EXEC_CMD "cp $HADOOP_CONF_TEMP_DIR/* $HADOOP_TARGET_DIR/etc/hadoop/"
      echo "---------------------- Done ---------------------------"
else
      echo "Cancel creating a new hadoop link.Quit!!"
      exit
fi
echo -e "ReFormat HDFS ?(y/Y/n/N)\b"
read format
if [ "$format" == "y" -o "$format" == "Y" ]
then
      echo "Droping hdfs datanode input"
      $CLUSTER_UTIL/clean-input-up
      echo "Formating hdfs namenode"
      $HADOOP_TARGET_DIR/bin/hadoop namenode -format
      /home/sorttest/hadoop/sbin/start-all.sh
      echo "Starting HDFS Success."
      jps
      $EXEC_CMD jps
else
      echo "WARNNING: HDFS is not being formatted!!!"
      exit
fi

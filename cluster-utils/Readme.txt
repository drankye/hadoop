This file is help you to use the auto deploy tools.

before run this tool you need to setup the $HADOOP_HOME environment.


if you want to deploy a new hadoop tar file from a local path, run:
./autoBuild.sh <Given tar.gz file>
the script will auto deploy the hadoop binary into /home/sorttest/bins/hadoop-current_running_packet

if you want to deploy a new hadoop dir from a local path, run:
./deployHadoop.sh <hadoop src dir> <hadoop target dir>

which means you want to put the hadoop as a given target dir.

NOTICE: you must first to put the conf files into a hadoopconf which should be put into same parent directory with the <hadoop target dir>

after ran each hadoop job, you can run ./utils/drop-cache.sh to clean the cache in the os.
if you want to clean the hdfs file syste, you can run ./utils/clean-input-up. but before clean you need to set every directory you want to clean into ./utils/files
this tool also give you a way to check the existence of the directory in ./utils/files, by which you can run ./utils/check-file
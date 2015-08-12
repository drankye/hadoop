bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool
testDir=/root/zk

echo "Testing with 1G data, chunk size 1MB"
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 0 1024 1024
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 0 1024 1024
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 1 1024 1024
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool  1 1024 1024
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 2 1024 1024
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 2 1024 1024

rm -rf $testDir/*coded*coder*.dat

echo "Testing with 10G data, chunk size 8MB"
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 0 10240 8192
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 0 10240 8192
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 1 10240 8192
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 1 10240 8192
echo bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 2 10240 8192
bin/hadoop org.apache.hadoop.io.erasurecode.BenchmarkTool $testDir 2 10240 8192



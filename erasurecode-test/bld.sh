rm -rf sample
gcc -g -ldl erasure_code.c coder_common.c sample.c -o sample
if [ $? = 0 ]; then
  ./sample
fi

g++ -o libisajni.so -g -O3 -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -I../include -lpthread erasure_code.so -shared -fPIC -Wl,-soname,isajni.so  isa_encoder_jni.c isa_decoder_jni.c

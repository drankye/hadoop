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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"

static void usage() {
  fprintf(stderr, "Usage: check_native_lib <native-lib-defined-in-config.h>\n");
  exit(1);
}

/*
 * This tool tells if a specified native library is available or not by
 * looking at config.h file to see the corresponding def is defined or not.
 */
int main(int argc, char **argv) {
  if (argc > 1 && strlen(argv[1]) > 0) {
    const char* libDef = argv[1];
    char* libValue = NULL;

    if (strcmp(libDef, "HADOOP_ZLIB_LIBRARY") == 0) {
      #ifdef HADOOP_ZLIB_LIBRARY
      libValue = HADOOP_ZLIB_LIBRARY;
      #endif
    } else if (strcmp(libDef, "HADOOP_BZIP2_LIBRARY") == 0) {
      #ifdef HADOOP_BZIP2_LIBRARY
      libValue = HADOOP_BZIP2_LIBRARY;
      #endif
    } else if (strcmp(libDef, "HADOOP_SNAPPY_LIBRARY") == 0) {
      #ifdef HADOOP_SNAPPY_LIBRARY
      libValue = HADOOP_SNAPPY_LIBRARY;
      #endif
    } else if (strcmp(libDef, "HADOOP_OPENSSL_LIBRARY") == 0) {
      #ifdef HADOOP_OPENSSL_LIBRARY
      libValue = HADOOP_OPENSSL_LIBRARY;
      #endif
    } else if (strcmp(libDef, "HADOOP_ERASURECODE_LIBRARY") == 0) {
      #ifdef HADOOP_ERASURECODE_LIBRARY
      libValue = HADOOP_ERASURECODE_LIBRARY;
      #endif
    }

    if (libValue != NULL && strlen(libValue) > 0) {
      fprintf(stdout, "%s is available as:%s\n", libDef, libValue);
      exit(0);
    } else {
      fprintf(stdout, "%s isn't available\n", libDef);
      exit(1);
    }
  } else {
    usage();
  }
}

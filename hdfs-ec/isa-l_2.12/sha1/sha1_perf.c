/**********************************************************************
  Copyright(c) 2011-2014 Intel Corporation All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions 
  are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.
    * Neither the name of Intel Corporation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**********************************************************************/

#include<stdio.h>
#include<stdint.h>
#include<string.h>
#include<stdlib.h>
#include <openssl/sha.h>
#include "sha.h"
#include "test.h"

//#define CACHED_TEST
#ifdef CACHED_TEST
// Loop many times over same 
# define TEST_LEN     16*1024
# define TEST_LOOPS   20000
# define TEST_TYPE_STR "_warm"
#else
// Uncached test.  Pull from large mem base.
# define TEST_LEN     64*1024*1024
# define TEST_LOOPS   4
# define TEST_TYPE_STR "_cold"
#endif

#define TEST_MEM TEST_LEN

int main(int argc, char *argv[])
{
	int i, fail = 0;
	unsigned int hash_tst[5];
	unsigned char hash_ssl[5 * 8];

	printf("Test sha1 performance\n");

	unsigned char *buff = malloc(TEST_LEN + 64);

	//Make rand data
	memset(buff, 0, TEST_LEN);

	struct perf start, stop;

	// OpenSSL version
	SHA1((unsigned char *)buff, 55, hash_ssl);	// run one to warm up
	perf_start(&start);
	for (i = 0; i < TEST_LOOPS; i++) {
		SHA1((unsigned char *)buff, TEST_LEN, hash_ssl);
	}
	perf_stop(&stop);
	printf("sha1_openssl" TEST_TYPE_STR ": ");
	perf_print(stop, start, (long long)TEST_MEM * i);

	// Internal test
	sha1_opt(buff, hash_tst, TEST_LEN);
	perf_start(&start);
	for (i = 0; i < TEST_LOOPS; i++) {
		sha1_opt(buff, hash_tst, TEST_LEN);
	}
	perf_stop(&stop);
	printf("sha1_opt" TEST_TYPE_STR ": ");
	perf_print(stop, start, (long long)TEST_MEM * i);

	// Check results
	for (i = 0; i < 5 * 4; i++)
		if (hash_ssl[i] != (0xff & (hash_tst[i >> 2] >> (24 - 8 * (i & 3)))))
			fail++;

	if (fail)
		printf("Test failed function test%d\n", fail);
	else
		printf("Pass func check\n");

	return fail;
}

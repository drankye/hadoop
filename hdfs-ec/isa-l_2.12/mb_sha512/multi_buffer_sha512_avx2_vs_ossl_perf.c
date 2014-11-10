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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <openssl/sha.h>
#include "mb_sha512.h"
#include "test.h"

// Set number of outstanding jobs
#define TEST_BUFS 32

#ifdef CACHED_TEST
// Loop many times over same data
#  define TEST_LEN     4*1024
#  define TEST_LOOPS   1000
#  define TEST_TYPE_STR "_warm"
#else
// Uncached test.  Pull from large mem base.
#  define GT_L3_CACHE  32*1024*1024	/* some number > last level cache */
#  define TEST_LEN     (GT_L3_CACHE / TEST_BUFS)
#  define TEST_LOOPS   20
#  define TEST_TYPE_STR "_cold"
#endif

#define TEST_MEM TEST_LEN * TEST_BUFS * TEST_LOOPS

/* Reference digest global to reduce stack usage */
static UINT8 digest_ssl[TEST_BUFS][8 * NUM_SHA512_DIGEST_WORDS];

inline UINT64 byteswap64(UINT64 x)
{
#if defined (__ICC)
	return _bswap64(x);
#elif defined (__GNUC__) && (__GNUC__ > 4 || (__GNUC__ == 4 && __GNUC_MINOR__ >= 3))
	return __builtin_bswap64(x);
#else
	return (((x & (0xffull << 0)) << 56)
		| ((x & (0xffull << 8)) << 40)
		| ((x & (0xffull << 16)) << 24)
		| ((x & (0xffull << 24)) << 8)
		| ((x & (0xffull << 32)) >> 8)
		| ((x & (0xffull << 40)) >> 24)
		| ((x & (0xffull << 48)) >> 40)
		| ((x & (0xffull << 56)) >> 56));
#endif
}

int main()
{
	JOB_SHA512 job[TEST_BUFS];
	UINT32 i, j, t, fail = 0;
	SHA512_MB_MGR_X4 mb_mgr;
	struct perf start, stop;

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (UINT8 *) malloc(TEST_LEN);
		if (job[i].buffer == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		memset(job[i].buffer, 0, TEST_LEN);
		job[i].len = TEST_LEN;
		job[i].len_total = TEST_LEN;
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha512_init_mb_mgr_x4(&mb_mgr);

	// Start OpenSSL tests
	perf_start(&start);
	for (t = 0; t < TEST_LOOPS; t++) {
		for (i = 0; i < TEST_BUFS; i++)
			SHA512(job[i].buffer, job[i].len, digest_ssl[i]);
	}
	perf_stop(&stop);

	printf("sha512_openssl" TEST_TYPE_STR ": ");
	perf_print(stop, start, (long long)TEST_LEN * i * t);

	// Start mb tests
	perf_start(&start);
	for (t = 0; t < TEST_LOOPS; t++) {
		for (i = 0; i < TEST_BUFS; i++)
			sha512_submit_job_avx2(&mb_mgr, &job[i]);

		while (sha512_flush_job_avx2(&mb_mgr)) ;
	}
	perf_stop(&stop);

	printf("sha512_mb_avx2" TEST_TYPE_STR ": ");
	perf_print(stop, start, (long long)TEST_LEN * i * t);

	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {
			if (job[i].result_digest[j] !=
			    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
				fail++;
				printf("Test%d, digest%d fail %8lX <=> %8lX\n",
				       i, j, job[i].result_digest[j],
				       byteswap64(((UINT64 *) digest_ssl[i])[j]));
			}
		}
	}

	printf("Multi-buffer sha512_avx2 test complete %d buffers of %d B with "
	       "%d iterations\n", TEST_BUFS, TEST_LEN, TEST_LOOPS);

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf("Pass functional check\n");

	return fail;
}

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

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 200
#ifndef RANDOMS
# define RANDOMS  10
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

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

// Generates pseudo-random data

void rand_buffer(unsigned char *buf, long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

int main()
{
	JOB_SHA512 job[TEST_BUFS];
	UINT32 i, j, fail = 0;
	SHA512_MB_MGR mb_mgr;
	unsigned int joblen, jobs, t;
	UINT8 *tmp_buf;

	printf("mb_sha512 test, %d sets of %dx%d max: ", RANDOMS, TEST_BUFS, TEST_LEN);

	srand(TEST_SEED);

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (unsigned char *)malloc(TEST_LEN + 64);	//msgs[i];
		if (job[i].buffer == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		rand_buffer(job[i].buffer, TEST_LEN);
		job[i].len = TEST_LEN;
		job[i].len_total = TEST_LEN;
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha512_init_mb_mgr(&mb_mgr);

	// Run OpenSSL tests
	for (i = 0; i < TEST_BUFS; i++)
		SHA512(job[i].buffer, job[i].len, digest_ssl[i]);

	// Run mb_sha512 tests
	for (i = 0; i < TEST_BUFS; i++)
		sha512_submit_job(&mb_mgr, &job[i]);

	while (sha512_flush_job(&mb_mgr)) ;

	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {
			if (job[i].result_digest[j] !=
			    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
				fail++;
				printf("Test%d, len=0x%x, digest%d fail %8lX <=> %8lX\n",
				       i, job[i].len, j, job[i].result_digest[j],
				       byteswap64(((UINT64 *) digest_ssl[i])[j]));
			}
		}
	}
	putchar('.');

	if (fail) {
		printf("Test failed function check %d\n", fail);
		return fail;
	}
	// Run tests with random size and number of jobs

	for (t = 0; t < RANDOMS; t++) {
		jobs = rand() % (TEST_BUFS);

		for (i = 0; i < jobs; i++) {
			joblen = rand() % (TEST_LEN);
			rand_buffer(job[i].buffer, joblen);
			job[i].len = joblen;
			job[i].len_total = joblen;
		}

		sha512_init_mb_mgr(&mb_mgr);

		// Run OpenSSL jobs
		for (i = 0; i < jobs; i++)
			SHA512(job[i].buffer, job[i].len, digest_ssl[i]);

		// Run mb_sha512 jobs
		for (i = 0; i < jobs; i++)
			sha512_submit_job(&mb_mgr, &job[i]);

		while (sha512_flush_job(&mb_mgr)) ;

		for (i = 0; i < jobs; i++) {
			for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {
				if (job[i].result_digest[j] !=
				    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
					fail++;
					printf
					    ("Rand%d test%d len=%d, digest%d fail %8lX <=> %8lX\n",
					     t, i, job[i].len, j, job[i].result_digest[j],
					     byteswap64(((UINT64 *) digest_ssl[i])[j]));
				}
			}
		}
		if (fail) {
			printf("Test failed rand #%d function check %d\n", t, fail);
			return fail;
		}
		putchar('.');
		fflush(0);
	}			// random test t

	// Test at the end of buffer
	jobs = rand() % TEST_BUFS;
	tmp_buf = (UINT8 *) malloc(sizeof(UINT8) * jobs);
	if (!tmp_buf) {
		printf("malloc failed, end test aborted.\n");
		return 1;
	}

	rand_buffer(tmp_buf, jobs);

	// Entend to the end of allocated buffer to construct jobs
	for (i = 0; i < jobs; i++) {
		job[i].buffer = (UINT8 *) & tmp_buf[i];
		job[i].len = jobs - i;
		job[i].len_total = job[i].len;
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha512_init_mb_mgr(&mb_mgr);

	// Store reference results from SSL
	for (i = 0; i < jobs; i++)
		SHA512(job[i].buffer, job[i].len, digest_ssl[i]);

	for (i = 0; i < jobs; i++)
		sha512_submit_job(&mb_mgr, &job[i]);

	while (sha512_flush_job(&mb_mgr)) ;

	for (i = 0; i < jobs; i++) {
		for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {
			if (job[i].result_digest[j] !=
			    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
				fail++;
				printf("End test failed at offset %d - result: %8lX"
				       ", ssl_res: %8lX\n", i, job[i].result_digest[j],
				       byteswap64(((UINT64 *) digest_ssl[i])[j]));
			}
		}
	}

	putchar('.');

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" mb_sha512 rand: Pass\n");

	return fail;
}

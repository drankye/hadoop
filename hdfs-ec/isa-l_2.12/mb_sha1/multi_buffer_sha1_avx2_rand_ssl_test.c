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
#include "mb_sha1.h"

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 100
#ifndef RANDOMS
# define RANDOMS  10
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

/* Reference digest global to reduce stack usage */
static UINT8 digest_ssl[TEST_BUFS][4 * NUM_SHA1_DIGEST_WORDS];

// Generates pseudo-random data

void rand_buffer(unsigned char *buf, long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

inline unsigned int byteswap(unsigned int x)
{
	return (x >> 24) | (x >> 8 & 0xff00) | (x << 8 & 0xff0000) | (x << 24);
}

int main()
{
	JOB_SHA1 job[TEST_BUFS];
	UINT32 i, j, fail = 0;
	SHA1_MB_MGR_X8 mb_mgr;
	unsigned int joblen, jobs, t;

	printf("mb_sha1_avx2 test, %d sets of %dx%d max: ", RANDOMS, TEST_BUFS, TEST_LEN);

	srand(TEST_SEED);

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (UINT8 *) malloc(TEST_LEN + 64);
		if (job[i].buffer == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		rand_buffer(job[i].buffer, TEST_LEN);
		job[i].len = TEST_LEN;
		job[i].len_total = TEST_LEN;
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha1_init_mb_mgr_x8(&mb_mgr);

	// Run OpenSSL tests
	for (i = 0; i < TEST_BUFS; i++)
		SHA1(job[i].buffer, job[i].len, digest_ssl[i]);

	// Run mb_sha1 tests
	for (i = 0; i < TEST_BUFS; i++)
		sha1_submit_job_avx2(&mb_mgr, &job[i]);

	while (sha1_flush_job_avx2(&mb_mgr)) ;

	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < NUM_SHA1_DIGEST_WORDS; j++) {

			DEBUG_PRINT(("Test %d, digest %d is %8x %8x\n", i, j,
				     job[i].result_digest[j],
				     byteswap(((UINT32 *) digest_ssl[i])[j])));

			if (job[i].result_digest[j] != byteswap(((UINT32 *) digest_ssl[i])[j])) {
				fail++;
				printf("Test%d, digest%d fail %08X <=> %08X\n",
				       i, j, job[i].result_digest[j],
				       byteswap(((UINT32 *) digest_ssl[i])[j]));
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

		sha1_init_mb_mgr_x8(&mb_mgr);

		// Run OpenSSL jobs
		for (i = 0; i < jobs; i++)
			SHA1(job[i].buffer, job[i].len, digest_ssl[i]);

		// Run mb_sha1 jobs
		for (i = 0; i < jobs; i++)
			sha1_submit_job_avx2(&mb_mgr, &job[i]);

		while (sha1_flush_job_avx2(&mb_mgr)) ;

		for (i = 0; i < jobs; i++) {
			for (j = 0; j < NUM_SHA1_DIGEST_WORDS; j++) {

				DEBUG_PRINT(("Test %d, digest %d is %8x %8x\n", i, j,
					     job[i].result_digest[j],
					     byteswap(((UINT32 *) digest_ssl[i])[j])));

				if (job[i].result_digest[j] !=
				    byteswap(((UINT32 *) digest_ssl[i])[j])) {
					fail++;
					printf("Test%d, digest%d fail %08X <=> %08X\n",
					       i, j, job[i].result_digest[j],
					       byteswap(((UINT32 *) digest_ssl[i])[j]));
				}
			}
		}
		if (fail) {
			printf("Test failed function check %d\n", fail);
			return fail;
		}

		putchar('.');
		fflush(0);
	}			// random test t

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" mb_sha1_avx2 rand: Pass\n");

	return fail;
}

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
#include "mb_sha256.h"

typedef UINT32 DigestSHA256[NUM_SHA256_DIGEST_WORDS];

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 100
#ifndef RANDOMS
# define RANDOMS  10
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

// Compare against reference function
extern void sha256_ref(UINT8 * input_data, UINT32 * digest, UINT32 len);

// Generates pseudo-random data

void rand_buffer(unsigned char *buf, long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

UINT32 digest_ref[TEST_BUFS][NUM_SHA256_DIGEST_WORDS];

int main()
{
	JOB_SHA256 job[TEST_BUFS];
	UINT32 i, j, fail = 0;
	SHA256_MB_MGR mb_mgr;
	unsigned int joblen, jobs, t;
	UINT8 *tmp_buf;

	printf("mb_sha256 test, %d sets of %dx%d max: ", RANDOMS, TEST_BUFS, TEST_LEN);

	srand(TEST_SEED);

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (UINT8 *) malloc(TEST_LEN);
		if (job[i].buffer == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		rand_buffer(job[i].buffer, TEST_LEN);
		job[i].len = TEST_LEN;
		job[i].len_total = TEST_LEN;
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha256_init_mb_mgr(&mb_mgr);

	// Run reference tests
	for (i = 0; i < TEST_BUFS; i++)
		sha256_ref(job[i].buffer, digest_ref[i], job[i].len);

	// Run mb_sha256 tests
	for (i = 0; i < TEST_BUFS; i++)
		sha256_submit_job(&mb_mgr, &job[i]);

	while (sha256_flush_job(&mb_mgr)) ;

	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
			if (job[i].result_digest[j] != digest_ref[i][j]) {
				fail++;
				printf("Test%d fixed size, digest%d fail %08X <=> %08X",
				       i, j, job[i].result_digest[j], digest_ref[i][j]);
				printf(" len=%d, len_total=%d\n", job[i].len,
				       job[i].len_total);
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

		sha256_init_mb_mgr(&mb_mgr);

		// Run ref jobs
		for (i = 0; i < jobs; i++)
			sha256_ref(job[i].buffer, digest_ref[i], job[i].len);

		// Run mb_sha256 jobs
		for (i = 0; i < jobs; i++)
			sha256_submit_job(&mb_mgr, &job[i]);

		while (sha256_flush_job(&mb_mgr)) ;

		for (i = 0; i < jobs; i++) {
			for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
				if (job[i].result_digest[j] != digest_ref[i][j]) {
					fail++;
					printf("Test%d, digest%d fail %08X <=> %08X\n",
					       i, j, job[i].result_digest[j],
					       digest_ref[i][j]);
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

	sha256_init_mb_mgr(&mb_mgr);

	// Store reference results
	for (i = 0; i < jobs; i++)
		sha256_ref(job[i].buffer, digest_ref[i], job[i].len);

	for (i = 0; i < jobs; i++)
		sha256_submit_job(&mb_mgr, &job[i]);

	while (sha256_flush_job(&mb_mgr)) ;

	for (i = 0; i < jobs; i++) {
		for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
			if (job[i].result_digest[j] != digest_ref[i][j]) {
				fail++;
				printf("End test failed at offset %d - result: %08X"
				       ", ref: %08X\n", i, job[i].result_digest[j],
				       digest_ref[i][j]);
			}
		}
	}

	putchar('.');

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" mb_sha256 rand: Pass\n");

	return fail;
}

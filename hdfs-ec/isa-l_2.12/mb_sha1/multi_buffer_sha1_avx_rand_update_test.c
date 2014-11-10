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
#include "mb_sha1.h"
#include "types.h"

typedef UINT32 DigestSHA1[5];

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 100
#ifndef RANDOMS
# define RANDOMS  10
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif
#ifdef DEBUG
# define debug_char(x) putchar(x)
#else
# define debug_char(x)
#endif

#define UPDATE_SIZE            13*SHA1_BLOCK_SIZE
#define MAX_RAND_UPDATE_BLOCKS (TEST_LEN/(16*SHA1_BLOCK_SIZE))

extern void sha1_ref(UINT8 * input_data, UINT32 * digest, UINT32 len);

// Generates pseudo-random data

void rand_buffer(unsigned char *buf, long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

UINT32 digest_ref[TEST_BUFS][NUM_SHA1_DIGEST_WORDS];

int main()
{
	JOB_SHA1 job[TEST_BUFS], *p_job;
	UINT32 i, j, fail = 0;
	SHA1_MB_MGR mb_mgr;
	int len_done, len_rem, len_rand;
	unsigned int joblen, jobs, t;

	printf("mb_sha1_avx_update test, %d sets of %dx%d max: ", RANDOMS, TEST_BUFS,
	       TEST_LEN);

	srand(TEST_SEED);

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (UINT8 *) malloc(TEST_LEN + 64);
		if (job[i].buffer == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		rand_buffer(job[i].buffer, TEST_LEN);
		job[i].len = UPDATE_SIZE;
		job[i].len_total = TEST_LEN;
		job[i].user_data = job[i].buffer;
		job[i].flags = HASH_MB_FIRST;
	}

	sha1_init_mb_mgr(&mb_mgr);

	// Run reference tests
	for (i = 0; i < TEST_BUFS; i++)
		sha1_ref(job[i].buffer, digest_ref[i], job[i].len_total);

	// Run mb_sha1 tests
	for (i = 0, p_job = &job[0]; i < TEST_BUFS;) {
		p_job = sha1_submit_job_avx(&mb_mgr, p_job);

		// Add jobs while available or finished
		if (p_job == NULL) {
			p_job = &job[++i];
			continue;
		}
		if (p_job->flags & HASH_MB_LAST) {
			p_job = &job[++i];
			continue;
		}
		// Resubmit unfinished job
		p_job->buffer += p_job->len;
		len_done =
		    (int)((unsigned long)p_job->buffer - (unsigned long)p_job->user_data);
		len_rem = p_job->len_total - len_done;

		if (len_rem <= UPDATE_SIZE) {
			p_job->flags = HASH_MB_LAST;
			p_job->len = len_rem;
			continue;
		}
		p_job->flags = HASH_MB_NO_FLAGS;
	}

	// Start flushing finished jobs, end on last flushed
	p_job = sha1_flush_job_avx(&mb_mgr);
	while (p_job) {
		if (p_job->flags & HASH_MB_LAST) {
			debug_char('-');
			p_job = sha1_flush_job_avx(&mb_mgr);
			continue;
		}
		// Resubmit unfinished job
		p_job->buffer += p_job->len;
		len_done =
		    (int)((unsigned long)p_job->buffer - (unsigned long)p_job->user_data);
		len_rem = p_job->len_total - len_done;

		if (len_rem <= UPDATE_SIZE) {
			p_job->flags = HASH_MB_LAST;
			p_job->len = len_rem;
		} else
			p_job->flags = HASH_MB_NO_FLAGS;

		p_job = sha1_submit_job_avx(&mb_mgr, p_job);
		if (p_job == NULL)
			p_job = sha1_flush_job_avx(&mb_mgr);
	}

	// Check digests
	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < 5; j++) {
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
			len_rand =
			    SHA1_BLOCK_SIZE +
			    SHA1_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);
			job[i].buffer = job[i].user_data;
			rand_buffer(job[i].buffer, joblen);
			job[i].len_total = joblen;

			if (joblen > len_rand) {
				job[i].len = len_rand;
				job[i].flags = HASH_MB_FIRST;
			} else {
				job[i].len = joblen;
				job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
			}
		}

		sha1_init_mb_mgr(&mb_mgr);

		// Run reference jobs
		for (i = 0; i < jobs; i++)
			sha1_ref(job[i].buffer, digest_ref[i], job[i].len_total);

		// Run mb_sha1 jobs
		i = 0;
		p_job = &job[0];

		while (i < jobs) {
			p_job = sha1_submit_job_avx(&mb_mgr, p_job);
			if (p_job == NULL) {
				p_job = &job[++i];
				debug_char('*');
				continue;
			}
			if (p_job->flags & HASH_MB_LAST) {
				p_job = &job[++i];
				debug_char('-');
				continue;
			}
			// Resubmit unfinished job
			p_job->buffer += p_job->len;
			len_done =
			    (int)((unsigned long)p_job->buffer -
				  (unsigned long)p_job->user_data);
			len_rem = p_job->len_total - len_done;
			len_rand =
			    SHA1_BLOCK_SIZE +
			    SHA1_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);

			debug_char('+');
			if (len_rem <= len_rand) {
				p_job->flags = HASH_MB_LAST;
				p_job->len = len_rem;
				continue;
			} else
				p_job->len = len_rand;

			p_job->flags = HASH_MB_NO_FLAGS;
		}

		// Start flushing finished jobs, end on last flushed
		p_job = sha1_flush_job_avx(&mb_mgr);
		while (p_job) {
			if (p_job->flags & HASH_MB_LAST) {
				debug_char('-');
				p_job = sha1_flush_job_avx(&mb_mgr);
				continue;
			}
			// Resubmit unfinished job
			p_job->buffer += p_job->len;
			len_done =
			    (int)((unsigned long)p_job->buffer -
				  (unsigned long)p_job->user_data);
			len_rem = p_job->len_total - len_done;
			len_rand =
			    SHA1_BLOCK_SIZE +
			    SHA1_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);

			debug_char('+');
			if (len_rem <= len_rand) {
				p_job->flags = HASH_MB_LAST;
				p_job->len = len_rem;
			} else {
				p_job->flags = HASH_MB_NO_FLAGS;
				p_job->len = len_rand;
			}

			p_job = sha1_submit_job_avx(&mb_mgr, p_job);
			if (p_job == NULL)
				p_job = sha1_flush_job_avx(&mb_mgr);
		}

		// Check result digest
		for (i = 0; i < jobs; i++) {
			for (j = 0; j < 5; j++) {
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

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" mb_sha1_avx_update rand: Pass\n");

	return fail;
}

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
#ifdef DEBUG
# include <stddef.h>
#endif

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 200
#ifndef RANDOMS
# define RANDOMS  10
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

#define UPDATE_SIZE		13*SHA512_BLOCK_SIZE
#define MAX_RAND_UPDATE_BLOCKS (TEST_LEN/(16*SHA512_BLOCK_SIZE))

#ifdef DEBUG
# define debug_char(x) putchar(x)
#else
# define debug_char(x)
#endif

/* Reference digest global to reduce stack usage */
static UINT8 digest_ssl[TEST_BUFS][8 * NUM_SHA512_DIGEST_WORDS];

inline unsigned int byteswap(unsigned int x)
{
	return (x >> 24) | (x >> 8 & 0xff00) | (x << 8 & 0xff0000) | (x << 24);
}

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
	JOB_SHA512 job[TEST_BUFS], *p_job;
	UINT32 i, j, fail = 0;
	SHA512_MB_MGR mb_mgr;
	int len_done, len_rem, len_rand;
	unsigned int joblen, jobs, t;

	printf("mb_sha512_avx_update test, %d sets of %dx%d max: ", RANDOMS, TEST_BUFS,
	       TEST_LEN);

	srand(TEST_SEED);

	for (i = 0; i < TEST_BUFS; i++) {
		job[i].buffer = (unsigned char *)malloc(TEST_LEN + 64);
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

	sha512_init_mb_mgr(&mb_mgr);

	// Run OpenSSL tests
	for (i = 0; i < TEST_BUFS; i++)
		SHA512(job[i].buffer, job[i].len_total, digest_ssl[i]);

	// Run mb_sha512 tests
	for (i = 0, p_job = &job[0]; i < TEST_BUFS;) {
		p_job = sha512_submit_job_avx(&mb_mgr, p_job);

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
	p_job = sha512_flush_job_avx(&mb_mgr);
	while (p_job) {
		if (p_job->flags & HASH_MB_LAST) {
			debug_char('-');
			p_job = sha512_flush_job_avx(&mb_mgr);
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

		p_job = sha512_submit_job_avx(&mb_mgr, p_job);
		if (p_job == NULL)
			p_job = sha512_flush_job_avx(&mb_mgr);
	}

	// Check digests
	for (i = 0; i < TEST_BUFS; i++) {
		for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {

			DEBUG_PRINT(("Test %d, digest %d is %8lx %8x\n", i, j,
				     job[i].result_digest[j],
				     byteswap(((UINT32 *) digest_ssl[i])[j])));

			if (job[i].result_digest[j] !=
			    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
				fail++;
				printf("Test%d fixed size, digest%d fail %08lX <=> %08lX",
				       i, j, job[i].result_digest[j],
				       byteswap64(((UINT64 *) digest_ssl[i])[j]));
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
			    SHA512_BLOCK_SIZE +
			    SHA512_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);
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

		sha512_init_mb_mgr(&mb_mgr);

		// Run OpenSSL jobs
		for (i = 0; i < jobs; i++)
			SHA512(job[i].buffer, job[i].len_total, digest_ssl[i]);

		// Run mb_sha512 jobs
		i = 0;
		p_job = &job[0];

		while (i < jobs) {
			p_job = sha512_submit_job_avx(&mb_mgr, p_job);
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
			    SHA512_BLOCK_SIZE +
			    SHA512_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);

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
		p_job = sha512_flush_job_avx(&mb_mgr);
		while (p_job) {
			if (p_job->flags & HASH_MB_LAST) {
				debug_char('-');
				p_job = sha512_flush_job_avx(&mb_mgr);
				continue;
			}
			// Resubmit unfinished job
			p_job->buffer += p_job->len;
			len_done =
			    (int)((unsigned long)p_job->buffer -
				  (unsigned long)p_job->user_data);
			len_rem = p_job->len_total - len_done;
			len_rand =
			    SHA512_BLOCK_SIZE +
			    SHA512_BLOCK_SIZE * (rand() % MAX_RAND_UPDATE_BLOCKS);

			debug_char('+');
			if (len_rem <= len_rand) {
				p_job->flags = HASH_MB_LAST;
				p_job->len = len_rem;
			} else {
				p_job->flags = HASH_MB_NO_FLAGS;
				p_job->len = len_rand;
			}

			p_job = sha512_submit_job_avx(&mb_mgr, p_job);
			if (p_job == NULL)
				p_job = sha512_flush_job_avx(&mb_mgr);
		}

		// Check result digest
		for (i = 0; i < jobs; i++) {
			for (j = 0; j < NUM_SHA512_DIGEST_WORDS; j++) {

				DEBUG_PRINT(("Test %d, digest %d is %8lx %8x\n", i, j,
					     job[i].result_digest[j],
					     byteswap(((UINT32 *) digest_ssl[i])[j])));

				if (job[i].result_digest[j] !=
				    byteswap64(((UINT64 *) digest_ssl[i])[j])) {
					fail++;
					printf("Test%d, digest%d fail %08lX <=> %08lX\n",
					       i, j, job[i].result_digest[j],
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

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" mb_sha512_avx_update rand: Pass\n");

	return fail;
}

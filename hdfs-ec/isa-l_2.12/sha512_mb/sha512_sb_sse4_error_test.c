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
#include "sha512_mb.h"

#define TEST_LEN  (1024*1024)
#define TEST_BUFS 3
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

#define UPDATE_SIZE		13*SHA512_BLOCK_SIZE
#define MAX_RAND_UPDATE_BLOCKS 	(TEST_LEN/(16*SHA512_BLOCK_SIZE))

extern void sha512_ref(uint8_t * input_data, uint64_t * digest, uint32_t len);

/* Reference digest global to reduce stack usage */
static uint64_t digest_ref[TEST_BUFS][SHA512_DIGEST_NWORDS];

// Generates pseudo-random data
void rand_buffer(unsigned char *buf, const long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

int main()
{
	SHA512_HASH_CTX_MGR *mgr;
	SHA512_HASH_CTX ctxpool[TEST_BUFS], *ctx;
	uint32_t i, fail = 0;
	unsigned char *bufs[TEST_BUFS];
	unsigned char *buf_ptr[TEST_BUFS];

	srand(TEST_SEED);

	posix_memalign((void *)&mgr, 16, sizeof(SHA512_HASH_CTX_MGR));
	sha512_ctx_mgr_init_sb_sse4(mgr);

	for (i = 0; i < TEST_BUFS; i++) {
		// Allocate and fill buffer
		bufs[i] = (unsigned char *)malloc(TEST_LEN);
		buf_ptr[i] = bufs[i];
		if (bufs[i] == NULL) {
			printf("malloc failed test aborted\n");
			return 1;
		}
		rand_buffer(bufs[i], TEST_LEN);

		// Init ctx contents
		hash_ctx_init(&ctxpool[i]);
		ctxpool[i].user_data = (void *)((uint64_t) i);

		// Run refenrence test
		sha512_ref(bufs[i], digest_ref[i], TEST_LEN);
	}

	// Error tests:
	// 1. Submitting a job without proper flags
	//   HASH_CTX_ERROR_INVALID_FLAGS      = -1 
	ctx = sha512_ctx_mgr_submit_sb_sse4(mgr, &ctxpool[1], buf_ptr[1], UPDATE_SIZE, 4);
	if (ctx->error != HASH_CTX_ERROR_INVALID_FLAGS) {
		printf("Test 2: ctx->error = %d, should be %d\n", ctx->error,
		       HASH_CTX_ERROR_INVALID_FLAGS);
		fail++;
	}
	while (sha512_ctx_mgr_flush_sb_sse4(mgr)) ;

	// 2. Submit a completed CTX without re-initialising the CTX with a HASH_FIRST job
	//    HASH_CTX_ERROR_ALREADY_COMPLETED  = -3
	ctx =
	    sha512_ctx_mgr_submit_sb_sse4(mgr, &ctxpool[2], buf_ptr[2], TEST_LEN, HASH_ENTIRE);
	// CTX 2 has been submitted as entire, make sure it's cleared
	while (sha512_ctx_mgr_flush_sb_sse4(mgr)) ;

	// Submit CTX 2 again with update
	ctx = sha512_ctx_mgr_submit_sb_sse4(mgr, &ctxpool[2],
					    buf_ptr[2], UPDATE_SIZE, HASH_UPDATE);

	if (ctx->error != HASH_CTX_ERROR_ALREADY_COMPLETED) {
		printf("Test 3: ctx->error = %d, should be %d\n", ctx->error,
		       HASH_CTX_ERROR_ALREADY_COMPLETED);
		fail++;
	}

	if (fail)
		printf("Test failed function check %d\n", fail);
	else
		printf(" sb_sse4_sha512 error: Pass\n");

	return fail;
}

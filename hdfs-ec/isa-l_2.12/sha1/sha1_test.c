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
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include "sha.h"

#define TEST_LEN   16*1024
#define TEST_SIZE   8*1024
#define TEST_MEM   TEST_LEN
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

extern void sha1_ref(unsigned char *input_data, unsigned int *digest, int len);

// Generates pseudo-random data

void rand_buffer(unsigned char *buf, long buffer_size)
{
	long i;
	for (i = 0; i < buffer_size; i++)
		buf[i] = rand();
}

void dump(char *buf, int len)
{
	int i;
	for (i = 0; i < len;) {
		printf(" %2x", 0xff & buf[i++]);
		if (i % 20 == 0)
			printf("\n");
	}
	if (i % 20 != 0)
		printf("\n");
}

int main(int argc, char *argv[])
{
	int i, fail = 0;
	unsigned int hash_tst[5], hash_ref[5];
	unsigned char *buff;
	int size, offset;

	printf("Test sha1 ");

	srand(TEST_SEED);

	buff = malloc(TEST_LEN + 64);
	if (NULL == buff)
		return -1;

	// Rand test1
	rand_buffer(buff, TEST_LEN);

	sha1_ref(buff, hash_ref, TEST_LEN);
	sha1_opt(buff, hash_tst, TEST_LEN);

	for (i = 0; i < 5; i++) {
		if (hash_tst[i] != hash_ref[i])
			fail++;
	}

	if (fail) {
		printf("fail rand1 test\n");
		printf("asm: ");
		dump((char *)hash_tst, 20);
		printf("ref: ");
		dump((char *)hash_ref, 20);
	} else
		putchar('.');

	// Test various size messages
	for (size = TEST_LEN; size >= 0; size--) {

		// Fill with rand data
		rand_buffer(buff, size);

		sha1_ref(buff, hash_ref, size);
		sha1_opt(buff, hash_tst, size);

		for (i = 0; i < 5; i++) {
			if (hash_tst[i] != hash_ref[i])
				fail++;
		}

		if (fail) {
			printf("Fail size=%d\n", size);
			printf("asm: ");
			dump((char *)hash_tst, 20);
			printf("ref: ");
			dump((char *)hash_ref, 20);
			return -1;
		}

		if ((size & 0xff) == 0) {
			putchar('.');
			fflush(0);
		}
	}

	// Test various buffer offsets and sizes
	printf("offset tests");
	for (size = TEST_LEN - 256; size > 256; size -= 11) {
		for (offset = 0; offset < 256; offset++) {
			sha1_ref(buff + offset, hash_ref, size);
			sha1_opt(buff + offset, hash_tst, size);

			for (i = 0; i < 5; i++) {
				if (hash_tst[i] != hash_ref[i])
					fail++;
			}

			if (fail) {
				printf("Fail size=%d offset=%d\n", size, offset);
				printf("asm: ");
				dump((char *)hash_tst, 20);
				printf("ref: ");
				dump((char *)hash_ref, 20);
				return -1;
			}

		}
		if ((size & 0xf) == 0) {
			putchar('.');
			fflush(0);
		}
	}

	// Run efence tests
	printf("efence tests");
	for (size = TEST_SIZE; size > 0; size--) {
		offset = TEST_LEN - size;
		sha1_ref(buff + offset, hash_ref, size);
		sha1_opt(buff + offset, hash_tst, size);

		for (i = 0; i < 5; i++) {
			if (hash_tst[i] != hash_ref[i])
				fail++;
		}

		if (fail) {
			printf("Fail size=%d offset=%d\n", size, offset);
			printf("asm: ");
			dump((char *)hash_tst, 20);
			printf("ref: ");
			dump((char *)hash_ref, 20);
			return -1;
		}

		if ((size & 0xf) == 0) {
			putchar('.');
			fflush(0);
		}
	}

	printf("sha1_test: %s\n", fail == 0 ? "Pass" : "Fail");

	return fail;
}

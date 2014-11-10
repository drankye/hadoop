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
#include "mem_routines.h"

#define RAND_ALIGN   32
#ifndef RANDOMS
# define RANDOMS 2000
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

#define TEST_MEM    10*1024*1024	// 10MB
#define TEST_LEN    8*1024	// 8KB

int main(int argc, char *argv[])
{
	unsigned char *src, *des, tmp;
	int ret = 0, c, i;

	srand(TEST_SEED);

	printf("mem_cmp_sse_test %d bytes, %d randoms, seed=0x%x ",
	       TEST_MEM, RANDOMS, TEST_SEED);

	if (!(src = (unsigned char *)malloc((size_t) TEST_MEM))) {
		printf("Error: src mem allocate failed.\n");
		return -1;
	}

	if (!(des = (unsigned char *)malloc((size_t) TEST_MEM))) {
		printf("Error: des mem allocate failed.\n");
		return -1;
	}

/*First part test begin------------------------------------------------------*/
	/*
	 *  First part - test same content
	 */

	// Test all 0 in full buffer
	c = 0;
	memset(src, c, TEST_MEM);
	memset(des, c, TEST_MEM);

	if (mem_cmp_sse(src, des, TEST_MEM)) {
		printf("Error: all 0 test failed.\n");
		printf("       src and des are all 0 but tested as "
		       "different - mem_cmp_sse.\n");
		return 1;
	} else {
		putchar('.');
		fflush(0);
	}

	// Test all 0 in small buffer at the begin
	for (i = 0; i < TEST_LEN; i++) {
		ret |= mem_cmp_sse(src + i, des + i, TEST_LEN - i);
		if (ret) {
			printf("Error: small buffer 0 begin test failed.\n");
			printf("       offset- %d, len - %d.\n", i, TEST_LEN - i);
			return ret;
		}
	}
	putchar('.');
	fflush(0);

	// Test all 0 in small buffer at the end
	for (i = 0; i < TEST_LEN; i++) {
		ret |= mem_cmp_sse(src + TEST_MEM - i, des + TEST_MEM - i, i);
		if (ret) {
			printf("Error: small buffer 0 end test failed.\n");
			printf("       offset- %d, len - %d.\n", TEST_MEM - i, i);
			return ret;
		}
	}
	putchar('.');
	fflush(0);

	// Test all random in full buffer
	for (i = 0; i < TEST_MEM; i++) {
		c = rand();
		*(src + i) = (unsigned char)c;
		*(des + i) = (unsigned char)c;
	}

	if (mem_cmp_sse(src, des, TEST_MEM)) {
		printf("Error: all random test failed.\n");
		printf("       src and des are the same random but tested "
		       "as different - mem_cmp_sse.\n");
		ret |= 1;
	} else {
		putchar('.');
		fflush(0);
	}

	// Test all random in small buffer at the begin
	for (i = 0; i < TEST_LEN; i++) {
		ret |= mem_cmp_sse(src + i, des + i, TEST_LEN - i);
		if (ret) {
			printf("Error: small buf random begin test failed.\n");
			printf("       offset- %d, len - %d.\n", i, TEST_LEN - i);
			return ret;
		}
	}
	putchar('.');
	fflush(0);

	// Test all random in small buffer at the end
	for (i = 0; i < TEST_LEN; i++) {
		ret |= mem_cmp_sse(src + TEST_MEM - i, des + TEST_MEM - i, i);
		if (ret) {
			printf("Error: small buf random end test failed.\n");
			printf("       offset- %d, len - %d.\n", TEST_MEM - i, i);
			return ret;
		}
	}
	putchar('.');
	fflush(0);

/*First part test end--------------------------------------------------------*/

/*Second part test begin-----------------------------------------------------*/
	/*
	 * Second part - test different content
	 */
	c = 0xAA;
	memset(src, c, TEST_MEM);
	memset(des, c, TEST_MEM);

	// Test difference at the begin
	*des = (unsigned char)c + 1;
	if (!mem_cmp_sse(src, des, TEST_MEM)) {
		printf("Error: Begin difference test failed - " "mem_cmp_sse.\n");
		printf("       src and des are different at the begin "
		       "but tested as the same.\n");
		return 1;
	} else {
		putchar('.');
		fflush(0);
	}
	*des = (unsigned char)c;

	// Test difference at the middle
	*(des + TEST_MEM / 2) = (unsigned char)c + 2;
	if (!mem_cmp_sse(src, des, TEST_MEM)) {
		printf("Error: Middle difference test failed - " "mem_cmp_sse.\n");
		printf("       src and des are different at the middle "
		       "but tested as the same.\n");
		return 1;
	} else {
		putchar('.');
		fflush(0);
	}
	*(des + TEST_MEM / 2) = (unsigned char)c;

	// Test difference at the end
	*(des + TEST_MEM - 1) = (unsigned char)c + 3;
	if (!mem_cmp_sse(src, des, TEST_MEM)) {
		printf("Error: End difference test failed - mem_cmp_sse.\n");
		printf("       src and des are different at the end "
		       "but tested as the same.\n");
		return 1;
	} else {
		putchar('.');
		fflush(0);
	}
	*(des + TEST_MEM - 1) = (unsigned char)c;

	// Test difference at random offset
	for (i = 0; i < RANDOMS; i++) {
		c = rand() % TEST_MEM;
		tmp = *(des + c);
		*(des + c) += 0x12;
		if (!mem_cmp_sse(src, des, TEST_MEM)) {
			printf("Error: rand diff failed, offset: %d.\n", c);
			return 1;
		}
		*(des + c) = tmp;
	}
	putchar('.');
	fflush(0);

/*Second pat test end--------------------------------------------------------*/

	// Free allocated resource
	if (src)
		free(src);
	if (des)
		free(des);

	printf(ret == 0 ? " Pass\n" : " Fail\n");

	return ret;
}

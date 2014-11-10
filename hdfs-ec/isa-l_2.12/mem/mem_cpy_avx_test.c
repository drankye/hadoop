#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "test.h"
#include "mem_routines.h"

#define RAND_ALIGN   32
#ifndef RANDOMS
# define RANDOMS 2000
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

#define TEST_LEN 0x1000

int main(int argc, char *argv[])
{
	unsigned char *src, *des;
	int i, r, ret = 0;

	srand(TEST_SEED);

	printf("mem_cpy_avx_test %d bytes, %d randoms, seed=0x%x\n",
	       TEST_LEN, RANDOMS, TEST_SEED);

	if ((src = (unsigned char *)malloc(TEST_LEN)) == NULL) {
		printf("Error: src malloc failed\n");
		return -1;
	}

	if ((des = (unsigned char *)malloc(TEST_LEN)) == NULL) {
		printf("Error: des malloc failed\n");
		return -1;
	}

	memset(src, 0x55, TEST_LEN);
	memset(des, 0xAA, TEST_LEN);

	// Test the whole length
	mem_cpy_avx(des, src, TEST_LEN);
	if (memcmp(des, src, TEST_LEN)) {
		printf("Error: whole length test failed - mem_cpy_avx\n");
		ret |= 1;
	} else {
		putchar('.');
		fflush(0);
	}

	// Test difference at random position
	for (i = 0; i < RANDOMS; i++) {
		r = rand() % TEST_LEN;
		*(src + r) = *(des + r) + 1;
		mem_cpy_avx(des, src, TEST_LEN);
		if (memcmp(des, src, TEST_LEN)) {
			printf("Error: random pos test failed (r: %d) - mem_cpy_avx\n", r);
			ret |= 1;
		} else {
			putchar('.');
			fflush(0);
		}
	}

	// Test random length
	for (i = 0; i < RANDOMS; i++) {
		r = rand() % TEST_LEN;
		memset(src, r, r);
		memset(des, r * 3 + 1, r);
		mem_cpy_avx(des, src, r);
		if (memcmp(des, src, r)) {
			printf("Error: random len test failed (r: %d) - mem_cpy_avx\n", r);
			ret |= 1;
		} else {
			putchar('.');
			fflush(0);
		}
	}

	printf(ret == 0 ? " Pass\n" : " Fail\n");

	return ret;
}

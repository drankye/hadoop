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
#include "mb_md5.h"

typedef UINT32 DigestMD5[NUM_MD5_DIGEST_WORDS];

#define NUM_TESTS 26
#define NUM_LOOPS 2
#define NUM_JOBS 1000

/* Repeat the vectors in order to have more than the number of lanes (to use full submit function) */

UINT8 msg1[] = "Test vector from febooti.com";
UINT8 msg2[] =
    "12345678901234567890123456789012345678901234567890123456789012345678901234567890";
UINT8 msg3[] = "";
UINT8 msg4[] = "abcdefghijklmnopqrstuvwxyz";
UINT8 msg5[] = "message digest";
UINT8 msg6[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
UINT8 msg7[] = "abc";
UINT8 msg8[] = "a";
UINT8 msg9[] = "";
UINT8 msgA[] = "abcdefghijklmnopqrstuvwxyz";	//10
UINT8 msgB[] = "message digest";	//11
UINT8 msgC[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";	//12
UINT8 msgD[] = "abc";		//13

UINT8 msgE[] = "Test vector from febooti.com";	//14
UINT8 msgF[] = "12345678901234567890123456789012345678901234567890123456789012345678901234567890";	//15
UINT8 msg10[] = "";		//16
UINT8 msg11[] = "abcdefghijklmnopqrstuvwxyz";	//17
UINT8 msg12[] = "message digest";	//18
UINT8 msg13[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";	//19
UINT8 msg14[] = "abc";		//20
UINT8 msg15[] = "a";		//21
UINT8 msg16[] = "";		//22
UINT8 msg17[] = "abcdefghijklmnopqrstuvwxyz";	//23
UINT8 msg18[] = "message digest";	//24
UINT8 msg19[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";	//25
UINT8 msg1A[] = "abc";		//27

DigestMD5 expResultDigest1 = { 0x61b60a50, 0xfbb76d3c, 0xf5620cd3, 0x0f3d57ff };
DigestMD5 expResultDigest2 = { 0xa2f4ed57, 0x55c9e32b, 0x2eda49ac, 0x7ab60721 };
DigestMD5 expResultDigest3 = { 0xd98c1dd4, 0x04b2008f, 0x980980e9, 0x7e42f8ec };
DigestMD5 expResultDigest4 = { 0xd7d3fcc3, 0x00e49261, 0x6c49fb7d, 0x3be167ca };
DigestMD5 expResultDigest5 = { 0x7d696bf9, 0x8d93b77c, 0x312f5a52, 0xd061f1aa };
DigestMD5 expResultDigest6 = { 0x98ab74d1, 0xf5d977d2, 0x2c1c61a5, 0x9f9d419f };
DigestMD5 expResultDigest7 = { 0x98500190, 0xb04fd23c, 0x7d3f96d6, 0x727fe128 };
DigestMD5 expResultDigest8 = { 0xb975c10c, 0xa8b6f1c0, 0xe299c331, 0x61267769 };
DigestMD5 expResultDigest9 = { 0xd98c1dd4, 0x04b2008f, 0x980980e9, 0x7e42f8ec };
DigestMD5 expResultDigestA = { 0xd7d3fcc3, 0x00e49261, 0x6c49fb7d, 0x3be167ca };
DigestMD5 expResultDigestB = { 0x7d696bf9, 0x8d93b77c, 0x312f5a52, 0xd061f1aa };
DigestMD5 expResultDigestC = { 0x98ab74d1, 0xf5d977d2, 0x2c1c61a5, 0x9f9d419f };
DigestMD5 expResultDigestD = { 0x98500190, 0xb04fd23c, 0x7d3f96d6, 0x727fe128 };

DigestMD5 expResultDigestE = { 0x61b60a50, 0xfbb76d3c, 0xf5620cd3, 0x0f3d57ff };
DigestMD5 expResultDigestF = { 0xa2f4ed57, 0x55c9e32b, 0x2eda49ac, 0x7ab60721 };
DigestMD5 expResultDigest10 = { 0xd98c1dd4, 0x04b2008f, 0x980980e9, 0x7e42f8ec };
DigestMD5 expResultDigest11 = { 0xd7d3fcc3, 0x00e49261, 0x6c49fb7d, 0x3be167ca };
DigestMD5 expResultDigest12 = { 0x7d696bf9, 0x8d93b77c, 0x312f5a52, 0xd061f1aa };
DigestMD5 expResultDigest13 = { 0x98ab74d1, 0xf5d977d2, 0x2c1c61a5, 0x9f9d419f };
DigestMD5 expResultDigest14 = { 0x98500190, 0xb04fd23c, 0x7d3f96d6, 0x727fe128 };
DigestMD5 expResultDigest15 = { 0xb975c10c, 0xa8b6f1c0, 0xe299c331, 0x61267769 };
DigestMD5 expResultDigest16 = { 0xd98c1dd4, 0x04b2008f, 0x980980e9, 0x7e42f8ec };
DigestMD5 expResultDigest17 = { 0xd7d3fcc3, 0x00e49261, 0x6c49fb7d, 0x3be167ca };
DigestMD5 expResultDigest18 = { 0x7d696bf9, 0x8d93b77c, 0x312f5a52, 0xd061f1aa };
DigestMD5 expResultDigest19 = { 0x98ab74d1, 0xf5d977d2, 0x2c1c61a5, 0x9f9d419f };
DigestMD5 expResultDigest1A = { 0x98500190, 0xb04fd23c, 0x7d3f96d6, 0x727fe128 };

UINT8 *msgs[NUM_TESTS] = { msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9,
	msgA, msgB, msgC, msgD, msgE, msgF, msg10, msg11, msg12,
	msg13, msg14, msg15, msg16, msg17, msg18, msg19, msg1A
};

UINT32 *expResultDigest[NUM_TESTS] = {
	expResultDigest1, expResultDigest2, expResultDigest3,
	expResultDigest4, expResultDigest5, expResultDigest6,
	expResultDigest7, expResultDigest8, expResultDigest9,
	expResultDigestA, expResultDigestB, expResultDigestC,
	expResultDigestD, expResultDigestE, expResultDigestF,
	expResultDigest10, expResultDigest11, expResultDigest12,
	expResultDigest13, expResultDigest14, expResultDigest15,
	expResultDigest16, expResultDigest17, expResultDigest18,
	expResultDigest19, expResultDigest1A
};

int main()
{
	JOB_MD5 job[NUM_JOBS], *p_job;
	UINT32 i, j, k, t, checked = 0;
	MD5_MB_MGR_X8X2 *mb_mgr;
	UINT32 *good;

	for (i = 0; i < NUM_TESTS; i++) {
		job[i].buffer = msgs[i];
		job[i].len = strlen((char *)(msgs[i]));
		job[i].len_total = job[i].len;
		job[i].user_data = (void *)expResultDigest[i];
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	posix_memalign((void *)&mb_mgr, 32, sizeof(MD5_MB_MGR_X8X2));
	printf("multi_buffer_md5_avx2_test\n");
	md5_init_mb_mgr_x8x2(mb_mgr);

	for (k = 0; k < NUM_LOOPS; k++) {
		for (i = 0; i < NUM_TESTS; i++) {
			p_job = md5_submit_job_avx2(mb_mgr, &job[i]);
			if (p_job) {
				t = (UINT32) (p_job - job);
				good = (UINT32 *) p_job->user_data;
				checked++;
				for (j = 0; j < NUM_MD5_DIGEST_WORDS; j++) {
					if (good[j] != p_job->result_digest[j]) {
						printf
						    ("\nTest vector %d, submit.\tDigest[%d] = %08X, Expected Digest = %08X.",
						     t, j, p_job->result_digest[j], good[j]);
						return -1;
					}
				}
			}
		}		// loop i

		while (NULL != (p_job = md5_flush_job_avx2(mb_mgr))) {
			t = (UINT32) (p_job - job);
			good = (UINT32 *) p_job->user_data;
			checked++;
			for (j = 0; j < NUM_MD5_DIGEST_WORDS; j++) {
				if (good[j] != p_job->result_digest[j]) {
					printf
					    ("\nTest vector %d flush.\tDigest[%d] = %08X, Expected Digest = %08X.",
					     t, j, p_job->result_digest[j], good[j]);
					printf("TEST FAILED!\n\n");
					return -1;
				}
			}
		}

		if (checked != (k + 1) * NUM_TESTS) {
			printf("only tested %d rather than %d\n", checked,
			       (k + 1) * NUM_TESTS);
			return -1;
		}

	}			// loop k

	// do larger test in pseudo-random order
	for (i = 0; i < NUM_JOBS; i++) {
		j = (i * 5 + (i * i) / 64) % NUM_TESTS;
		job[i].buffer = msgs[j];
		job[i].len = strlen((char *)(msgs[j]));
		job[i].len_total = job[i].len;
		job[i].user_data = (void *)expResultDigest[j];
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	checked = 0;
	for (i = 0; i < NUM_JOBS; i++) {
		p_job = md5_submit_job_avx2(mb_mgr, &job[i]);
		if (p_job) {
			t = (UINT32) (p_job - job);
			good = (UINT32 *) p_job->user_data;
			checked++;
			for (j = 0; j < NUM_MD5_DIGEST_WORDS; j++) {
				if (good[j] != p_job->result_digest[j]) {
					printf("Test %d, digest %d is %08X, should be %08X\n",
					       t, j, p_job->result_digest[j], good[j]);
					return -1;
				}
			}
		}
	}
	while (NULL != (p_job = md5_flush_job_avx2(mb_mgr))) {
		t = (UINT32) (p_job - job);
		good = (UINT32 *) p_job->user_data;
		checked++;
		for (j = 0; j < NUM_MD5_DIGEST_WORDS; j++) {
			if (good[j] != p_job->result_digest[j]) {
				printf("Test %d, digest %d is %08X, should be %08X\n",
				       t, j, p_job->result_digest[j], good[j]);
				return -1;
			}
		}
	}

	if (checked != NUM_JOBS) {
		printf("only tested %d rather than %d\n", checked, NUM_JOBS);
		return -1;
	}

	printf("md5 test: Pass\n");
	return 0;
}

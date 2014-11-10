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
#ifdef DEBUG
# include <stddef.h>
#endif

typedef UINT32 DigestSHA256[NUM_SHA256_DIGEST_WORDS];

#define NUM_TESTS 7
#define NUM_JOBS  1000

UINT8 msg1[] = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
UINT8 msg2[] = "0123456789:;<=>?@ABCDEFGHIJKLMNO";
UINT8 msg3[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<";
UINT8 msg4[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQR";
UINT8 msg5[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?";
UINT8 msg6[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTU";
UINT8 msg7[] = "";

DigestSHA256 expResultDigest1 = { 0x248D6A61, 0xD20638B8, 0xE5C02693, 0x0C3E6039,
	0xA33CE459, 0x64FF2167, 0xF6ECEDD4, 0x19DB06C1
};

DigestSHA256 expResultDigest2 = { 0xD9C2E699, 0x586B948F, 0x4022C799, 0x4FFE14C6,
	0x3A4E8E31, 0x2EE2AEE1, 0xEBE51BED, 0x85705CFD
};

DigestSHA256 expResultDigest3 = { 0xE3057651, 0x81295681, 0x7ECF1791, 0xFF9A1619,
	0xB2BC5CAD, 0x2AC00018, 0x92AE489C, 0x48DD10B3
};

DigestSHA256 expResultDigest4 = { 0x0307DAA3, 0x7130A140, 0x270790F9, 0x95B71407,
	0x8EC752A6, 0x084EC1F3, 0xBD873D79, 0x3FF78383
};

DigestSHA256 expResultDigest5 = { 0x679312F7, 0x2E18D599, 0x5F51BDC6, 0x4ED56AFD,
	0x9B5704D3, 0x4387E11C, 0xC2331089, 0x2CD45DAA
};

DigestSHA256 expResultDigest6 = { 0x8B1767E9, 0x7BA7BBE5, 0xF9A6E8D9, 0x9996904F,
	0x3AF6562E, 0xA58AF438, 0x5D8D584B, 0x81C808CE
};

DigestSHA256 expResultDigest7 = { 0xE3B0C442, 0x98FC1C14, 0x9AFBF4C8, 0x996FB924,
	0x27AE41E4, 0x649B934C, 0xA495991B, 0x7852B855
};

UINT8 *msgs[NUM_TESTS] = { msg1, msg2, msg3, msg4, msg5, msg6, msg7 };

UINT32 *expResultDigest[NUM_TESTS] = {
	expResultDigest1, expResultDigest2, expResultDigest3,
	expResultDigest4, expResultDigest5, expResultDigest6,
	expResultDigest7
};

int main()
{
	JOB_SHA256 job[NUM_JOBS], *p_job;
	UINT32 i, j, t, checked = 0;
	SHA256_MB_MGR_X8 mb_mgr;
	UINT32 *good;

	DEBUG_PRINT(("buffer: %ld\n", offsetof(JOB_SHA256, buffer)));
	DEBUG_PRINT(("len: %ld\n", offsetof(JOB_SHA256, len)));
	DEBUG_PRINT(("result_digest: %ld\n", offsetof(JOB_SHA256, result_digest)));
	DEBUG_PRINT(("status: %ld\n", offsetof(JOB_SHA256, status)));
	DEBUG_PRINT(("user_data: %ld\n", offsetof(JOB_SHA256, user_data)));
	DEBUG_PRINT(("size = %ld\n", sizeof(JOB_SHA256)));

	for (i = 0; i < NUM_TESTS; i++) {
		job[i].buffer = msgs[i];
		job[i].len = strlen((char *)(msgs[i]));
		job[i].len_total = job[i].len;
		job[i].user_data = (void *)expResultDigest[i];
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha256_init_mb_mgr_x8(&mb_mgr);

	for (i = 0; i < NUM_TESTS; i++) {
		p_job = sha256_submit_job_avx2(&mb_mgr, &job[i]);
		if (p_job) {
			DEBUG_PRINT(("testing %ld\n", p_job - job));
			t = (UINT32) (p_job - job);
			good = (UINT32 *) p_job->user_data;
			checked++;
			for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
				if (good[j] != p_job->result_digest[j]) {
					printf("Test %d, digest %d is %08X, should be %08X\n",
					       t, j, p_job->result_digest[j], good[j]);
					return -1;
				}
			}
		}
	}
	while (NULL != (p_job = sha256_flush_job_avx2(&mb_mgr))) {
		t = (UINT32) (p_job - job);
		good = (UINT32 *) p_job->user_data;
		checked++;
		for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
			if (good[j] != p_job->result_digest[j]) {
				printf("Test %d, digest %d is %08X, should be %08X\n",
				       t, j, p_job->result_digest[j], good[j]);
				return -1;
			}
		}
	}

	if (checked != NUM_TESTS) {
		printf("only tested %d rather than %d\n", checked, NUM_TESTS);
		return -1;
	}
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
		p_job = sha256_submit_job_avx2(&mb_mgr, &job[i]);
		if (p_job) {
			DEBUG_PRINT(("testing %ld\n", p_job - job));
			t = (UINT32) (p_job - job);
			good = (UINT32 *) p_job->user_data;
			checked++;
			for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
				if (good[j] != p_job->result_digest[j]) {
					printf("Test %d, digest %d is %08X, should be %08X\n",
					       t, j, p_job->result_digest[j], good[j]);
					return -1;
				}
			}
		}
	}
	while (NULL != (p_job = sha256_flush_job_avx2(&mb_mgr))) {
		t = (UINT32) (p_job - job);
		good = (UINT32 *) p_job->user_data;
		checked++;
		for (j = 0; j < NUM_SHA256_DIGEST_WORDS; j++) {
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

	printf("test passes\n");

	return 0;
}

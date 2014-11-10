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

typedef UINT32 DigestSHA1[5];

#define MSGS 7

UINT8 msg1[] = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
DigestSHA1 expResultDigest1 = { 0x84983E44, 0x1C3BD26E, 0xBAAE4AA1, 0xF95129E5, 0xE54670F1 };

UINT8 msg2[] = "0123456789:;<=>?@ABCDEFGHIJKLMNO";
DigestSHA1 expResultDigest2 = { 0xB7C66452, 0x0FD122B3, 0x55D539F2, 0xA35E6FAA, 0xC2A5A11D };

UINT8 msg3[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<";
DigestSHA1 expResultDigest3 = { 0x127729B6, 0xA8B2F8A0, 0xA4DDC819, 0x08E1D8B3, 0x67CEEA55 };

UINT8 msg4[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQR";
DigestSHA1 expResultDigest4 = { 0xFDDE2D00, 0xABD5B7A3, 0x699DE6F2, 0x3FF1D1AC, 0x3B872AC2 };

UINT8 msg5[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?";
DigestSHA1 expResultDigest5 = { 0xE7FCA85C, 0xA4AB3740, 0x6A180B32, 0x0B8D362C, 0x622A96E6 };

UINT8 msg6[] =
    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWX0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTU";
DigestSHA1 expResultDigest6 = { 0x505B0686, 0xE1ACDF42, 0xB3588B5A, 0xB043D52C, 0x6D8C7444 };

UINT8 msg7[] = "";
DigestSHA1 expResultDigest7 = { 0xDA39A3EE, 0x5E6B4B0D, 0x3255BFEF, 0x95601890, 0xAFD80709 };

UINT8 *msgs[MSGS] = { msg1, msg2, msg3, msg4, msg5, msg6, msg7 };

UINT32 *expResultDigest[MSGS] = {
	expResultDigest1, expResultDigest2, expResultDigest3,
	expResultDigest4, expResultDigest5, expResultDigest6,
	expResultDigest7
};

int main(void)
{
	JOB_SHA1 job[MSGS], *p_job;
	UINT32 i, j, t, checked = 0;
	SHA1_MB_MGR mb_mgr;
	UINT32 *good;

	for (i = 0; i < MSGS; i++) {
		job[i].buffer = msgs[i];
		job[i].len = strlen((char *)msgs[i]);
		job[i].len_total = job[i].len;
		job[i].user_data = (void *)expResultDigest[i];
		job[i].flags = HASH_MB_FIRST | HASH_MB_LAST;
	}

	sha1_init_mb_mgr(&mb_mgr);

	for (i = 0; i < MSGS; i++) {
		p_job = sha1_submit_job_avx(&mb_mgr, &job[i]);
		if (p_job) {
			t = (UINT32) (p_job - job);
			good = (UINT32 *) p_job->user_data;
			checked++;
			for (j = 0; j < NUM_SHA1_DIGEST_WORDS; j++) {
				if (good[j] != p_job->result_digest[j])
					printf("Test %d, digest %d is %08X, should be %08X\n",
					       t, j, p_job->result_digest[j], good[j]);
			}
		}
	}
	while (NULL != (p_job = sha1_flush_job_avx(&mb_mgr))) {
		t = (UINT32) (p_job - job);
		good = (UINT32 *) p_job->user_data;
		checked++;
		for (j = 0; j < NUM_SHA1_DIGEST_WORDS; j++) {
			if (good[j] != p_job->result_digest[j])
				printf("Test %d, digest %d is %08X, should be %08X\n",
				       t, j, p_job->result_digest[j], good[j]);
		}
	}

	if (checked != MSGS)
		printf("only tested %d rather than %d\n", checked, MSGS);

	printf("test done\n");

	return 0;
}

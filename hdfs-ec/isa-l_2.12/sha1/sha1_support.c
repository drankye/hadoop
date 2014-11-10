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

#include <string.h>
#include "sha.h"

#define H0 0x67452301
#define H1 0xefcdab89
#define H2 0x98badcfe
#define H3 0x10325476
#define H4 0xc3d2e1f0

int sha1_init(unsigned int *digest)
{
	digest[0] = H0;
	digest[1] = H1;
	digest[2] = H2;
	digest[3] = H3;
	digest[4] = H4;
	return 0;
}

void sha1_opt(unsigned char *input_data, unsigned int *digest, int len)
{
	long long len_bits;
	int i, j, blocks;
	unsigned char buf[128];

	digest[0] = H0;
	digest[1] = H1;
	digest[2] = H2;
	digest[3] = H3;
	digest[4] = H4;

	blocks = len / 64;
	i = len % 64;

	if (blocks > 0)
		sha1_update(digest, input_data, blocks);

	// The last block must be padded with 1 and length in bits
	memcpy(buf, input_data + (blocks * 64), i);
	buf[i++] = 0x80;

	// Zero pad to the next 64 bytes + room for len
	while ((i + 8) % 64 != 0)
		buf[i++] = 0;

	// Append length in bits to last 64 bits of block
	len_bits = len * 8;
	for (j = 7; j >= 0; j--) {
		buf[i + j] = len_bits & 0xff;
		len_bits >>= 8;
	}

	// Do final update on 1-2 appended message blocks
	sha1_update(digest, buf, i > 64 - 8 ? 2 : 1);

}

struct slver {
	unsigned short snum;
	unsigned char ver;
	unsigned char core;
};

struct slver sha1_opt_slver_00020051;
struct slver sha1_opt_slver = { 0x0051, 0x02, 0x00 };

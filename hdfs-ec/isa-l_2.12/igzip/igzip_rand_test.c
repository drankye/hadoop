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
#include "igzip_lib.h"
#ifdef _WIN64
# define ZLIB_WINAPI 1
#endif
#include "zlib.h"
#include "math.h"

#ifndef RANDOMS
# define RANDOMS   100
#endif
#ifndef TEST_SEED
# define TEST_SEED 0x1234
#endif

#define IBUF_SIZE  (1024*1024)
#define OBUF_SIZE  (1024*1024)

#define EFENCE_TEST_IBUF_SIZE  (8*1024)
#define EFENCE_TEST_OBUF_SIZE  (8*1024)
#define EFENCE_TEST_IBUF_SIZE_SYNC_FLUSH  (32*1024)
#define EFENCE_TEST_OBUF_SIZE_SYNC_FLUSH  (32*1024)

#define EFENCE_TEST_SIZE       32
#define EFENCE_TEST_STEP       16

#define DEFLATE 1
#define str1 "Short test string"
#define str2 "one two three four five six seven eight nine ten eleven twelve " \
		 "thirteen fourteen fifteen sixteen"

typedef unsigned char u8;

/* Generate Gaussian random variable with mean=0, sigma=1 */
double gaussian_rand(void)
{
	unsigned int u1 = rand();
	unsigned int u2 = rand();
	return sqrt(-2 * log((double)u1 / RAND_MAX))
	    * sin(2 * M_PI * (double)u2 / RAND_MAX);
}

void create_data(u8 * data, int size)
{
	while (size--)
		*data++ = rand();
}

void create_data_structured(u8 * data, int size)
{
	char d = 'm';
	while (size--)
		*data++ = d = d + (int)(3 * gaussian_rand());
}

int test_compress(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize;
	stream.end_of_stream = 1;
	stream.next_in = inbuf;
	stream.flush = FINISH_FLUSH;
	do {
		stream.avail_out = bufsize;
		stream.next_out = zbuf + stream.total_out;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}
	/* Test inflate with zlib */
	z_stream gstream;
	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;
#ifndef DEFLATE
	if (0 != inflateInit2(&gstream, 32 + 15)) {
#else
	if (0 != inflateInit2(&gstream, -15)) {
#endif
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

int test_compress_stateless(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize,
			    uint8_t no_comp)
{
	int ret = COMP_OK;
	uint32_t overflow = 0;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.internal_state.no_comp = no_comp;

	stream.avail_in = insize;
	stream.end_of_stream = 1;
	stream.next_in = inbuf;
	stream.flush = FINISH_FLUSH;

	stream.avail_out = bufsize;
	stream.next_out = zbuf;
	ret = fast_lz_stateless(&stream);

	if (ret != COMP_OK) {
		if (ret == STATELESS_OVERFLOW) {
			//printf("overflow occurs\n");
			overflow = 1;
		} else {
			printf("error compressing ret = %d\n", ret);
			return -1;
		}
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (ret == COMP_OK && stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}

	/* verify the stream */
	if (stream.next_in - inbuf != stream.total_in ||
	    stream.total_in + stream.avail_in != insize) {
		printf("error: stream input buffer check error\n");
		printf("input buffer start addr: 0x%p, input buffer size: 0x%x\n", inbuf,
		       insize);
		printf("next_in: 0x%p, total_in: 0x%x, avail_in: 0x%x\n", stream.next_in,
		       stream.total_in, stream.avail_in);
		return -1;
	}
	if (stream.next_out - zbuf != stream.total_out ||
	    stream.total_out + stream.avail_out != bufsize) {
		printf("error: stream output buffer check error\n");
		printf("output buffer start addr: 0x%p, output buffer size: 0x%x\n", zbuf,
		       bufsize);
		printf("next_out: 0x%p, total_out: 0x%x, avail_out: 0x%x\n", stream.next_out,
		       stream.total_out, stream.avail_out);
		return -1;
	}

	if (overflow == 1)
		return 0;

	/* Test inflate with zlib */
	z_stream gstream;
	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;
#ifndef DEFLATE
	if (0 != inflateInit2(&gstream, 32 + 15)) {
#else
	if (0 != inflateInit2(&gstream, -15)) {
#endif
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	if (gstream.avail_in != 0) {
		printf("gstream.avail_in(0x%x) != 0\n", gstream.avail_in);
		printf("the trailer of igzip output contains junk\n");
		return -1;
	}
	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*Sync flush compression test*/
int test_compress2(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize / 2;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = SYNC_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf;
	ret = fast_lz(&stream);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}

	stream.avail_in = insize - (insize / 2);
	stream.end_of_stream = 1;
	stream.next_in = inbuf + stream.total_in;
	stream.flush = SYNC_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf + stream.total_out;
	ret = fast_lz(&stream);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}

	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}

	/* Test inflate with zlib */
	z_stream gstream;
	gstream.total_in = 0;
	gstream.avail_in = 0;

	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;

	if (0 != inflateInit2(&gstream, -15)) {
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*SYNC FLUSH followed by Finish flush*/
int test_compress3(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize / 2;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = SYNC_FLUSH;

	do {
		stream.avail_out = bufsize / 2;
		stream.next_out = zbuf;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	stream.avail_in = insize - (insize / 2);
	stream.end_of_stream = 1;
	stream.next_in = inbuf + stream.total_in;
	stream.flush = FINISH_FLUSH;

	do {
		stream.avail_out = bufsize / 2;
		stream.next_out = zbuf + stream.total_out;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}

	/* Test inflate with zlib */
	z_stream gstream;
	gstream.total_in = 0;
	gstream.avail_in = 0;

	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;

	if (0 != inflateInit2(&gstream, -15)) {
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*Finish Flush followed by sync flush test --- INVALID*/
int test_compress4(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	LZ_Stream1 stream;
	int ret = COMP_OK;
	init_stream(&stream);

	stream.avail_in = insize / 2;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = FINISH_FLUSH;

	do {
		stream.avail_out = bufsize / 2;
		stream.next_out = zbuf;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	stream.avail_in = insize - (insize / 2);
	stream.end_of_stream = 1;
	stream.next_in = inbuf + stream.total_in;
	stream.flush = SYNC_FLUSH;

	do {
		stream.avail_out = bufsize / 2;
		stream.next_out = zbuf + stream.total_out;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != INVALID_PARAM) {
		printf("error compressing\n");
		return -1;
	} else if (ret == INVALID_PARAM) {
		return 0;
	}

	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return 0;
	}

	return 0;
}

/*invalid Flush value test*/
int test_compress5(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	LZ_Stream1 stream;
	int ret = COMP_OK;
	init_stream(&stream);

	stream.avail_in = insize;
	stream.end_of_stream = 1;
	stream.next_in = inbuf;
	stream.flush = 10;

	do {
		stream.avail_out = bufsize;
		stream.next_out = zbuf;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != INVALID_FLUSH) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	} else if (ret == INVALID_FLUSH) {
		return 0;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return 0;
	}

	return 0;
}

/*Bytes consumed test*/
int test_compress6(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = SYNC_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf;
	ret = fast_lz(&stream);
	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}

	if (stream.bytes_consumed != insize)
		return -1;

	/* Test inflate with zlib */
	z_stream gstream;
	gstream.total_in = 0;
	gstream.avail_in = 0;

	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;

	if (0 != inflateInit2(&gstream, -15)) {
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*no flush smaller output buffer*/
int test_compress7(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize;
	stream.end_of_stream = 1;
	stream.next_in = inbuf;
	stream.flush = FINISH_FLUSH;
	do {
		stream.avail_out = bufsize / 16;
		stream.next_out = zbuf + stream.total_out;
		ret = fast_lz(&stream);
	} while (stream.avail_out == 0);

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}
	/* Test inflate with zlib */
	z_stream gstream;
	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;
#ifndef DEFLATE
	if (0 != inflateInit2(&gstream, 32 + 15)) {
#else
	if (0 != inflateInit2(&gstream, -15)) {
#endif
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*sync flush smaller output buffer*/
int test_compress8(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;

	init_stream(&stream);

	stream.avail_in = insize;
	stream.end_of_stream = 1;
	stream.next_in = inbuf;
	stream.flush = SYNC_FLUSH;

	do {
		do {
			stream.avail_out -= bufsize / 4;
			stream.next_out = zbuf + stream.total_out;
			fast_lz(&stream);
		} while (stream.avail_out == 0);

	} while (!stream.end_of_stream);

	/*didn't compress everything from the last buffer */
	while (stream.bytes_consumed < insize) {
		stream.avail_out -= bufsize / 16;
		stream.next_out = zbuf + stream.total_out;
		fast_lz(&stream);
	}

	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}
	/* Test inflate with zlib */
	z_stream gstream;
	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;
#ifndef DEFLATE
	if (0 != inflateInit2(&gstream, 32 + 15)) {
#else
	if (0 != inflateInit2(&gstream, -15)) {
#endif
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*Non-Sync flush compression test with test to verify state at various stage*/
int test_compress9(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;
	struct LZ_State1 *state = &stream.internal_state;

	init_stream(&stream);
	if (state->state != LZS2_HDR) {
		printf("error verifying stream state after init_stream()\n");
		return -1;
	}

	stream.avail_in = insize / 2;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = FINISH_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf;
	ret = fast_lz(&stream);

	if (state->state != LZS2_BODY) {
		printf("error verifying stream state after fast_lz()\n");
		return -1;
	}
	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}

	stream.avail_in = insize - (insize / 2);
	stream.end_of_stream = 1;
	stream.next_in = inbuf + stream.total_in;
	stream.flush = FINISH_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf + stream.total_out;
	ret = fast_lz(&stream);

	if (state->state != LZS2_END) {
		printf("error verifying stream state after compression\n");
		return -1;
	}
	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (!stream.end_of_stream) {
		printf("error: compression test could not fit into allocated buffers\n");
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}
	/* Test inflate with zlib */
	z_stream gstream;
	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;
#ifndef DEFLATE
	if (0 != inflateInit2(&gstream, 32 + 15)) {
#else
	if (0 != inflateInit2(&gstream, -15)) {
#endif
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

/*Sync flush compression test with test to verify state at various stage*/
int test_compress10(u8 * inbuf, int insize, u8 * zbuf, u8 * testbuf, int bufsize)
{
	int ret = COMP_OK;
	LZ_Stream1 stream;
	struct LZ_State1 *state = &stream.internal_state;

	init_stream(&stream);
	if (state->state != LZS2_HDR) {
		printf("error verifying stream state after init_stream()\n");
		return -1;
	}

	stream.avail_in = insize / 2;
	stream.end_of_stream = 0;
	stream.next_in = inbuf;
	stream.flush = SYNC_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf;
	ret = fast_lz(&stream);

	if (state->state != LZS2_BODY) {
		printf("error verifying stream state after fast_lz()\n");
		return -1;
	}
	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}

	stream.avail_in = insize - (insize / 2);
	stream.end_of_stream = 1;
	stream.next_in = inbuf + stream.total_in;
	stream.flush = SYNC_FLUSH;
	stream.avail_out = bufsize;
	stream.next_out = zbuf + stream.total_out;
	ret = fast_lz(&stream);

	if (state->state != LZS2_END) {
		printf("error verifying stream state after compression\n");
		return -1;
	}
	if (ret != COMP_OK) {
		printf("error compressing ret = %d\n", ret);
		return -1;
	}
	if (stream.avail_in != 0) {
		printf("error: didn't compress all the input\n");
		return -1;
	}

	/* Test inflate with zlib */
	z_stream gstream;
	gstream.total_in = 0;
	gstream.avail_in = 0;

	gstream.next_in = zbuf;
	gstream.avail_in = stream.total_out;
	gstream.zalloc = Z_NULL;
	gstream.zfree = Z_NULL;
	gstream.opaque = Z_NULL;

	if (0 != inflateInit2(&gstream, -15)) {
		printf("Fail zlib init\n");
		return -1;
	}

	do {
		gstream.next_out = testbuf;
		gstream.avail_out = bufsize;
		ret = inflate(&gstream, Z_NO_FLUSH);
		if (ret == Z_STREAM_ERROR)
			break;
	} while (gstream.avail_out == 0);

	switch (ret) {
	case Z_OK:
	case Z_STREAM_END:
		putchar('.');
		break;
	case Z_MEM_ERROR:
		printf(" mem error\n");
		return -1;
		break;
	case Z_BUF_ERROR:
		printf(" buff error\n");
		return -1;
		break;
	case Z_DATA_ERROR:
		printf(" data error\n");
	default:
		return -1;
		break;
	}

	return memcmp(inbuf, testbuf, gstream.total_out);
}

int main(int argc, char *argv[])
{
	int i = 0, insize = 0, ret = 0, fin_ret = 0;
	u8 *inbuf, *zbuf, *testbuf;
	u8 *inbuf_efence, *zbuf_efence, *testbuf_efence;
	u8 *inbuf_efence_sync, *zbuf_efence_sync, *testbuf_efence_sync;

	printf("Window Size: %d K\n", HIST_SIZE);
	printf("igzip_rand_test: ");
	fflush(0);
	srand(TEST_SEED);

	inbuf = malloc(IBUF_SIZE);
	if (inbuf == NULL) {
		fprintf(stderr, "Can't allocate inbuf memory\n");
		return -1;
	}
	zbuf = malloc(OBUF_SIZE);
	if (zbuf == NULL) {
		fprintf(stderr, "Can't allocate zbuf memory\n");
		return -1;
	}
	testbuf = malloc(OBUF_SIZE);
	if (testbuf == NULL) {
		fprintf(stderr, "Can't allocate testbuf memory\n");
		return -1;
	}

	inbuf_efence = malloc(EFENCE_TEST_IBUF_SIZE);
	if (inbuf_efence == NULL) {
		fprintf(stderr, "Can't allocate inbuf_efence memory\n");
		return -1;
	}
	zbuf_efence = malloc(EFENCE_TEST_OBUF_SIZE);
	if (zbuf_efence == NULL) {
		fprintf(stderr, "Can't allocate zbuf_efence memory\n");
		return -1;
	}
	testbuf_efence = malloc(EFENCE_TEST_OBUF_SIZE);
	if (testbuf_efence == NULL) {
		fprintf(stderr, "Can't allocate testbuf_efence memory\n");
		return -1;
	}

	inbuf_efence_sync = malloc(EFENCE_TEST_IBUF_SIZE_SYNC_FLUSH);
	if (inbuf_efence_sync == NULL) {
		fprintf(stderr, "Can't allocate inbuf_efence_sync memory\n");
		return -1;
	}
	zbuf_efence_sync = malloc(EFENCE_TEST_OBUF_SIZE_SYNC_FLUSH);
	if (zbuf_efence_sync == NULL) {
		fprintf(stderr, "Can't allocate zbuf_efence_sync memory\n");
		return -1;
	}
	testbuf_efence_sync = malloc(EFENCE_TEST_OBUF_SIZE_SYNC_FLUSH);
	if (testbuf_efence_sync == NULL) {
		fprintf(stderr, "Can't allocate testbuf_efence_sync memory\n");
		return -1;
	}

	uint32_t hdr_bytes;
	uint32_t repeated_8k_char_out_bz;

#ifdef DEFLATE
	extern const uint32_t deflate_huff_hdr_count;
	extern const uint32_t deflate_huff_hdr_extra_bits;

	hdr_bytes = deflate_huff_hdr_count * 8 + (deflate_huff_hdr_extra_bits + 7) / 8;
	repeated_8k_char_out_bz = hdr_bytes + 84;
#else
	extern const uint32_t huff_hdr_count;
	extern const uint32_t huff_hdr_extra_bits;

	hdr_bytes = huff_hdr_count * 8 + (huff_hdr_extra_bits + 7) / 8;
	repeated_8k_char_out_bz = hdr_bytes + 84 + 8;
#endif
	/* repeated 8k of the same character */
	uint8_t repeated_8k_char_buf[8 * 1024];
	uint32_t repeated_char;
	uint32_t diff_char_pos;

	for (repeated_char = 0; repeated_char < 256; repeated_char++) {
		memset(repeated_8k_char_buf, repeated_char, 8 * 1024);
		/* normal case */
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, IBUF_SIZE, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz + 1, 0);
		/* overflow */
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz - 1, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, 0, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, 1, 0);
	}

	/* 8k input of almost the same character */
	for (diff_char_pos = 0; diff_char_pos < 8 * 1024; diff_char_pos++) {
		repeated_char = diff_char_pos % 256;
		memset(repeated_8k_char_buf, repeated_char, 8 * 1024);
		repeated_8k_char_buf[diff_char_pos] = repeated_char + 1;
		/* normal case */
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, IBUF_SIZE, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz + 1, 0);
		/* overflow */
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, repeated_8k_char_out_bz - 1, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, 0, 0);
		ret |= test_compress_stateless((u8 *) repeated_8k_char_buf, 8 * 1024, zbuf,
					       testbuf, 1, 0);
	}

	/* empty stored block */
	ret |= test_compress_stateless((u8 *) str1, 0, zbuf, testbuf, IBUF_SIZE, 0);
	/* stored block with size of multiple 65535 */
	ret |= test_compress_stateless(inbuf, 65535, zbuf, testbuf, IBUF_SIZE, 1);
	ret |= test_compress_stateless(inbuf, 65535 * 2, zbuf, testbuf, IBUF_SIZE, 1);

	ret |= test_compress_stateless((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE, 0);
	ret |= test_compress_stateless((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE, 0);

	/* test input of 260 < LA */
	ret |= test_compress_stateless(inbuf, 260, zbuf, testbuf, IBUF_SIZE, 0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress_stateless(inbuf, insize, zbuf, testbuf, IBUF_SIZE, 0);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress_stateless(inbuf, insize, zbuf, testbuf, IBUF_SIZE, 0);
	}

	/* overflow */
	ret |= test_compress_stateless((u8 *) str1, sizeof(str1), zbuf, testbuf, 1, 0);
	ret |= test_compress_stateless((u8 *) str2, sizeof(str2), zbuf, testbuf, 5, 0);
	ret |= test_compress_stateless(inbuf, hdr_bytes + 1, zbuf, testbuf, 1 + hdr_bytes, 0);

	/* stored block */
	ret |= test_compress_stateless(inbuf, hdr_bytes, zbuf, testbuf, 5 + hdr_bytes, 0);

	/* big data */
	ret |= test_compress_stateless(inbuf, IBUF_SIZE, zbuf, testbuf, IBUF_SIZE, 0);

	/* big stored block */
	ret |= test_compress_stateless(inbuf, IBUF_SIZE - 1024, zbuf, testbuf, IBUF_SIZE, 1);

	printf("igzip_rand_test_stateless %s\n", ret ? "Fail" : "Pass");
	printf("igzip_rand_test_stateless: ");
	fflush(0);
	fin_ret |= ret;

	ret |= test_compress((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip_rand_test %s\n", ret ? "Fail" : "Pass");

	printf("igzip_rand_test SYNC_FLUSH: ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress2((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress2((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress2(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress2(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip_rand_test SYNC_FLUSH %s\n", ret ? "Fail" : "Pass");

	printf("igzip_rand_test SYNC_FLUSH followed by FINISH_FLUSH: ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress3((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress3((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress3(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress3(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip_rand_test SYNC_FLUSH followed by FINISH_FLUSH: %s\n",
	       ret ? "Fail" : "Pass");

	printf("igzip_rand_test FINISH_FLUSH followed by SYNC_FLUSH: ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress4((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	printf("igzip_rand_test FINISH_FLUSH followed by SYNC_FLUSH: %s\n",
	       ret ? "Fail" : "Pass");

	printf("igzip_rand_test Invalid flush flag: ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress5((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	printf("igzip_rand_test Invalid flush flag: %s\n", ret ? "Fail" : "Pass");

	printf("igzip_rand_test Bytes Consumed test: ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress6((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress6((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress6(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress6(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip_rand_test Bytes Consumed test %s\n", ret ? "Fail" : "Pass");

	printf("igzip_rand_test Smaller output buffer no sync flush ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress7((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress7((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress7(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}

	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress7(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}

	printf("igzip_rand_test Smaller output buffer no sync flush %s\n",
	       ret ? "Fail" : "Pass");

	printf("igzip_rand_test Smaller output buffer with sync flush ");
	fflush(0);
	fin_ret |= ret;

	ret = test_compress8((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress8((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress8(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}

	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress8(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}

	printf("igzip_rand_test Smaller output buffer with sync flush %s\n",
	       ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	// Run finish flush efence test at end of 8K buffer.
	printf("igzip finish flush efence test at end of 8K buffer: ");
	create_data(inbuf_efence, EFENCE_TEST_IBUF_SIZE);
	for (i = EFENCE_TEST_SIZE; i > 0; i = i - EFENCE_TEST_STEP) {
		ret =
		    test_compress(inbuf_efence + (EFENCE_TEST_IBUF_SIZE - i), i, zbuf_efence,
				  testbuf_efence, EFENCE_TEST_IBUF_SIZE);
	}
	printf("igzip finish flush efence test at end of 8K buffer %s\n",
	       ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	// Run sync flush efence test at end of 8K buffer.
	printf("igzip sync flush efence test at end of 8K buffer: ");
	create_data(inbuf_efence, EFENCE_TEST_IBUF_SIZE);
	for (i = EFENCE_TEST_SIZE * 2; i > 0; i = i - EFENCE_TEST_STEP * 2) {
		ret =
		    test_compress2(inbuf_efence + (EFENCE_TEST_IBUF_SIZE - i), i, zbuf_efence,
				   testbuf_efence, EFENCE_TEST_IBUF_SIZE);
	}

	printf("igzip sync flush efence test at end of 8K buffer %s\n", ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	// Run sync flush efence test at end of 32K buffer.
	printf("igzip sync flush efence test at end of 32K buffer: ");
	create_data(inbuf_efence_sync, EFENCE_TEST_IBUF_SIZE_SYNC_FLUSH);
	for (i = EFENCE_TEST_SIZE * 2; i > 0; i = i - EFENCE_TEST_STEP * 2) {
		ret =
		    test_compress2(inbuf_efence + (EFENCE_TEST_IBUF_SIZE_SYNC_FLUSH - i), i,
				   zbuf_efence_sync, testbuf_efence_sync,
				   EFENCE_TEST_IBUF_SIZE_SYNC_FLUSH);
	}
	printf("igzip sync flush efence test at end of 32K buffer %s\n",
	       ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	// Run non sync flush test with tests to verify state at various stage
	printf("igzip non sync flush with tests to verify state: ");
	ret = test_compress9((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress9((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress9(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress9(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip non sync flush with tests to verify state %s\n", ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	// Run sync flush test with tests to verify state at various stage
	printf("igzip sync flush with tests to verify state: ");
	ret = test_compress10((u8 *) str1, sizeof(str1), zbuf, testbuf, IBUF_SIZE);
	ret |= test_compress10((u8 *) str2, sizeof(str2), zbuf, testbuf, IBUF_SIZE);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data(inbuf, insize);
		ret |= test_compress10(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	fflush(0);

	for (i = 0; i < RANDOMS; i++) {
		insize = rand() % (IBUF_SIZE / 2);
		create_data_structured(inbuf, insize);
		ret |= test_compress10(inbuf, insize, zbuf, testbuf, IBUF_SIZE);
	}
	printf("igzip sync flush with tests to verify state %s\n", ret ? "Fail" : "Pass");
	fflush(0);
	fin_ret |= ret;

	printf("igzip rand test finished: %s\n",
	       fin_ret ? "Some tests failed" : "All tests passed");
	return fin_ret;
}

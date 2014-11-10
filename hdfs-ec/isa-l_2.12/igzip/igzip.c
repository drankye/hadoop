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

#define ASM

#include <assert.h>
#include <string.h>
#ifndef __unix__
# include <intrin.h>
#endif
#define DEFLATE 1
#define FORCE_FLUSH 64
#define MIN_OBUF_SIZE 224
#define NON_EMPTY_BLOCK_SIZE 6

#include "fast_lz77.h"
#include "huffman.h"
#include "bitbuf2.h"
#include "igzip_lib.h"
#include "repeated_char_8k_result.h"


extern const uint32_t gzip_hdr_bytes;
extern const uint32_t gzip_trl_bytes;

extern UINT32 crc32_gzip(UINT32 init_crc, const unsigned char *buf, UINT64 len);

static int write_stored_block_stateless(struct LZ_Stream1 *stream, uint32_t stored_len,
					uint32_t crc32);
static int write_header_stateless(struct LZ_Stream1 *stream);
static int write_trailer_stateless(struct LZ_Stream1 *stream, uint32_t avail_in,
				   uint32_t crc32);

void fast_lz2_body_stateless(struct LZ_Stream1 *stream);
void fast_lz2_finish_stateless(struct LZ_Stream1 *stream);

unsigned int detect_repeated_char(uint8_t * buf, uint32_t size);

#define STORED_BLK_HDR_BZ 5
#define STORED_BLK_MAX_BZ 65535

#define INPUT_SIZE_8K         8192
#define DYN_BLK_MAX_BZ_8K     84	/* Only the deflate block size */

extern const uint64_t huff_hdr[];
extern const uint64_t sync_flush_huff_hdr[];
extern const uint64_t deflate_huff_hdr[];
extern const uint64_t deflate_end_huff_hdr[];
extern const uint64_t huff_hdr_extra;
extern const uint32_t huff_hdr_count;
extern const uint32_t huff_hdr_extra_bits;
extern const uint64_t deflate_huff_hdr_extra;
extern const uint32_t deflate_huff_hdr_count;
extern const uint32_t deflate_huff_hdr_extra_bits;

void fast_lz2_body(struct LZ_Stream1 *stream);
void fast_lz2_finish(struct LZ_Stream1 *stream);
uint32_t crc_512to32(uint32_t * crc);
static void get_eob_code(uint32_t * code, uint32_t * len);
static void write_stored_block(struct LZ_Stream1 *stream);

/*****************************************************************/

/* Forward declarations */
struct LZ_Stream1;
void write_header(struct LZ_Stream1 *stream);
void write_deflate_header(struct LZ_Stream1 *stream);
void write_trailer(struct LZ_Stream1 *stream);

struct slver {
	uint16_t snum;
	uint8_t ver;
	uint8_t core;
};

/* Version info */
struct slver init_stream_slver_01030081;
struct slver init_stream_slver = { 0x0081, 0x03, 0x01 };

struct slver fast_lz_slver_01030082;
struct slver fast_lz_slver = { 0x0082, 0x03, 0x01 };

struct slver fast_lz_stateless_slver_01010083;
struct slver fast_lz_stateless_slver = { 0x0083, 0x01, 0x01 };

/*****************************************************************/

uint32_t file_size(struct LZ_State1 *state)
{
	return state->b_bytes_valid + (uint32_t) (state->buffer - state->file_start);
}

static inline void get_eob_code(uint32_t * code, uint32_t * len)
{
	uint32_t code_len;
	code_len = 0x4ffb;
	*code = code_len >> 4;
	*len = code_len & 0xF;
}

static void fast_lz_int(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	if (state->state == LZS2_HDR) {
		write_header(stream);
		if (stream->avail_out < 8)
			return;
	}
	if (state->state == LZS2_BODY) {
		if ((stream->avail_in != 0) || !stream->end_of_stream) {
			fast_lz2_body(stream);
			if (stream->avail_out < 8)
				return;
		}
		if ((stream->avail_in == 0) && stream->end_of_stream) {
			fast_lz2_finish(stream);
			if (stream->avail_out < 8)
				return;
		}
	}
	if (state->state == LZS2_TRL) {
		write_trailer(stream);
		if (stream->avail_out < 8)
			return;
	}
}

static uint32_t write_8k_compressed(uint8_t * out, uint32_t repeated_char)
{
	uint32_t result_bz;

	result_bz = repeated_char_8k_gzip_sz[repeated_char];
#ifdef DEFLATE
	result_bz -= gzip_trl_bytes;
#endif
	memcpy(out, repeated_char_8k_gzip_blk[repeated_char], result_bz);

	return result_bz;
}

static int fast_lz_int_stateless(struct LZ_Stream1 *stream, uint8_t * next_in,
				 const uint32_t avail_in)
{
	uint32_t crc32 = 0;
	uint32_t dyn_blk_max_bz_8k;
	uint32_t repeated_8k_char;
	uint32_t repeated_8k_char_bz;

	if (write_header_stateless(stream) != COMP_OK)
		return STATELESS_OVERFLOW;
	if (stream->avail_out < 8)
		return STATELESS_OVERFLOW;

	/* check 8k repeated character
	   the current dynamic Huffman table for both gzip and deflate will be ended
	   byte-aligned, so for the repated 8K character case, we can do direct memory
	   copy without bit buffer management */
	if (avail_in == INPUT_SIZE_8K && stream->internal_state.bitbuf.m_bit_count == 0) {
		dyn_blk_max_bz_8k = DYN_BLK_MAX_BZ_8K;
#ifndef DEFLATE
		dyn_blk_max_bz_8k += gzip_trl_bytes;
#endif
		if (stream->avail_out >= dyn_blk_max_bz_8k) {
			repeated_8k_char =
			    detect_repeated_char(stream->next_in, INPUT_SIZE_8K);

			if (repeated_8k_char <= 255) {
				repeated_8k_char_bz =
				    write_8k_compressed(stream->next_out, repeated_8k_char);

				stream->avail_out -= repeated_8k_char_bz;
				stream->next_out += repeated_8k_char_bz;
				stream->total_out += repeated_8k_char_bz;

				stream->avail_in = 0;
				stream->next_in += INPUT_SIZE_8K;
				stream->total_in += INPUT_SIZE_8K;

				return COMP_OK;
			}
		} else {
			// for 8KB input, content of the same character will be the best
			// case for compression ratio, if the size of output buffer is
			// smaller than size of this compressed data, return overflow
			return STATELESS_OVERFLOW;
		}
	}

	fast_lz2_body_stateless(stream);
	if (stream->avail_out < 8)
		return STATELESS_OVERFLOW;

	fast_lz2_finish_stateless(stream);
	if (!stream->internal_state.has_eob || stream->avail_out < 8)
		return STATELESS_OVERFLOW;

#ifndef DEFLATE
	crc32 = crc32_gzip(0x0, next_in, avail_in);
#endif

	if (write_trailer_stateless(stream, avail_in, crc32) != COMP_OK)
		return STATELESS_OVERFLOW;

	return COMP_OK;
}

static
void write_stored_block(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	unsigned int bytes = 0;
	uint16_t len = 0;
	unsigned long stored_blk_hdr = 0L;

	state->stored_blk_len = 0;
	if (stream->bytes_consumed == state->submitted)
		return;

	set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
	write_bits(&state->bitbuf, stored_blk_hdr, 3);
	flush(&state->bitbuf);
	bytes = buffer_used(&state->bitbuf);
	stream->next_out = buffer_ptr(&state->bitbuf);
	stream->avail_out -= bytes;
	stream->total_out += bytes;

	len = stream->avail_out - 4;	/*4 bytes for the len and inverse */
	if ((state->submitted - stream->bytes_consumed) < len)
		len = (state->submitted - stream->bytes_consumed);

	set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
	write_bits(&state->bitbuf, len & 0x0000ffff, 16);
	flush(&state->bitbuf);
	bytes = buffer_used(&state->bitbuf);
	stream->next_out = buffer_ptr(&state->bitbuf);
	stream->avail_out -= bytes;
	stream->total_out += bytes;

	set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
	write_bits(&state->bitbuf, (~len & 0x0000ffff), 16);
	flush(&state->bitbuf);

	bytes = buffer_used(&state->bitbuf);
	stream->next_out = buffer_ptr(&state->bitbuf);
	stream->avail_out -= bytes;
	stream->total_out += bytes;
	memcpy(stream->next_out, (state->last_next_in + stream->bytes_consumed), len);
	state->b_bytes_processed += len;
	stream->avail_out -= len;
	stream->total_out += len;
	stream->next_out += len;
	state->stored_blk_len = len;
}

static int write_stored_block_stateless(struct LZ_Stream1 *stream,
					uint32_t stored_len, uint32_t crc32)
{
	uint64_t stored_blk_hdr;
	uint32_t copy_size;
	uint32_t avail_in;

#ifndef DEFLATE
	uint64_t gzip_trl;
#endif

	if (stream->avail_out < stored_len)
		return STATELESS_OVERFLOW;

	stream->avail_out -= stored_len;
	stream->total_out += stored_len;
	avail_in = stream->avail_in;

#ifndef DEFLATE
	memcpy(stream->next_out, huff_hdr, gzip_hdr_bytes);
	stream->next_out += gzip_hdr_bytes;
#endif

	do {
		if (avail_in >= STORED_BLK_MAX_BZ) {
			stored_blk_hdr = 0xFFFF00;
			copy_size = STORED_BLK_MAX_BZ;
		} else {
			stored_blk_hdr = ~avail_in;
			stored_blk_hdr <<= 24;
			stored_blk_hdr |= (avail_in & 0xFFFF) << 8;
			copy_size = avail_in;
		}

		avail_in -= copy_size;

		/* Handle BFINAL bit */
		if (avail_in == 0)
			stored_blk_hdr |= 0x1;

		memcpy(stream->next_out, &stored_blk_hdr, STORED_BLK_HDR_BZ);
		stream->next_out += STORED_BLK_HDR_BZ;

		memcpy(stream->next_out, stream->next_in, copy_size);
		stream->next_out += copy_size;
		stream->next_in += copy_size;
		stream->total_in += copy_size;
	} while (avail_in != 0);

#ifndef DEFLATE
	gzip_trl = stream->avail_in;
	gzip_trl <<= 32;
	gzip_trl |= crc32 & 0xFFFFFFFF;
	memcpy(stream->next_out, &gzip_trl, gzip_trl_bytes);
	stream->next_out += gzip_trl_bytes;
#endif

	stream->avail_in = 0;
	return COMP_OK;
}

static
void sync_flush(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	unsigned int bytes = 0, end_of_stored_blk = 0xFFFF;
	unsigned long stored_blk = 0L;
	uint32_t code = 0, len = 0;

	if (stream->end_of_stream) {
		state->state = LZS2_TRL;
		return;
	}
	if (state->no_comp)
		return;

	if (!state->has_eob) {
		get_eob_code(&code, &len);
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
		write_bits(&state->bitbuf, code, len);
		check_space(&state->bitbuf, FORCE_FLUSH);
		bytes = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= bytes;
		stream->total_out += bytes;
	}

	if (stream->avail_out >= NON_EMPTY_BLOCK_SIZE) {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
		write_bits(&state->bitbuf, stored_blk, 3);
		pad_to_byte(&state->bitbuf);
		write_bits(&state->bitbuf, end_of_stored_blk << 16, 32);
		flush(&state->bitbuf);

		bytes = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= bytes;
		stream->total_out += bytes;
	}
}

static
void fast_lz_int_with_flush(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	int took_space = 1, cont_overflow = 0;
	uint32_t code = 0, len = 0;
	unsigned int bytes = 0;
	uint32_t last_bytes_consumed = stream->bytes_consumed;

	/*******************************OVERFLOW LOGIC*********************************/
	if (state->had_overflow) {
		if (stream->bytes_consumed == state->overflow_submitted) {
			state->had_overflow = 0;
		} else {
			switch (state->overflow_submitted / (1024)) {
			case 16:
				/* 16K input buffer we simply didn't consume everything set a
				 * flag
				 */
				cont_overflow = 1;
				break;
			case 32:
				/* 32K input buffer we simply didn't consume everything set a
				 * flag
				 */
				if ((stream->bytes_consumed % (8 * 1024)) == 0) {
					/*We have consumed all the input to an 8k, 16k or 24k boundary
					 * of the buffer
					 */
					stream->avail_in = state->left_over;
					state->left_over = 0;
				} else {
					cont_overflow = 1;
				}
				break;
			default:
				/*NOT 16K or 32K buffer */
				stream->avail_in = state->left_over;
				state->left_over = 0;
				break;
			}
		}
	}
	if (state->overflow) {
		if (stream->bytes_consumed == (state->overflow_submitted - state->left_over)) {
			/*We have consumed all the input from the bottom part of the buffer */
			stream->avail_in = state->left_over;
			state->left_over = 0;
			state->overflow = 0;
			state->had_overflow = 1;
		} else {
			/*We didn't consume everything so we need to call
			 * compress with avail_in == 0 to continue processing*/
			state->left_over = stream->avail_in;
			stream->avail_in = 0;

		}
	}

	/*******************************STORED BLK LOGIC*******************************/
	if (!stream->end_of_stream && stream->avail_out < MIN_OBUF_SIZE) {
		/* Deflate HDR + TREES is 203
		 * Need enough space so we can take away 15 (7 here and 8 in the body
		 * function) so we still have space to compress and sync flush
		 * we actually need 218 to be able to do anything
		 */
		if (stream->avail_in != 0) {
			state->last_next_in = stream->next_in;
			stream->bytes_consumed = 0;
		}
		write_stored_block(stream);
		if (stream->avail_in != 0) {
			/* We read in a new buffer and we didn't have more than 224 bytes
			 * available need to update offsets for when fast_lz2_body is
			 * invoked.
			 */

			if (state->stored_blk_len != 0) {
				/* reset state->b_bytes_processed because it was modified in
				 * write_stored_block();
				 */
				state->b_bytes_processed -= state->stored_blk_len;
			}
		} else {
			/* we haven't consumed all the input from the last submitted buffer
			 * when we ran out of space */
			stream->bytes_consumed = state->submitted
			    - (state->b_bytes_valid - state->b_bytes_processed);

			if (state->had_overflow) {
				stream->bytes_consumed += last_bytes_consumed;
				if (stream->bytes_consumed == state->overflow_submitted) {
					state->had_overflow = 0;
				}
			} else if (state->overflow) {
				if (stream->bytes_consumed == state->submitted) {
					stream->avail_in = state->left_over;
					state->left_over = 0;
					state->overflow = 0;
					state->had_overflow = 1;
				}
			}
			return;
		}
		state->no_comp = 1;	/*In order to copy new buffer into history */
	} else if (stream->end_of_stream && stream->avail_out < MIN_OBUF_SIZE) {
		if (stream->avail_in != 0) {
			state->last_next_in = stream->next_in;
			stream->bytes_consumed = 0;

			write_stored_block(stream);
			/* reset state->b_bytes_processed because it was modified in
			 * write_stored_block();
			 */
			if (state->stored_blk_len != 0)
				state->b_bytes_processed -= state->stored_blk_len;

			state->no_comp = 1;
		}
	}
	/****************************END OF STORED BLK LOGIC*********************************/
	/*Keep space for FLUSH_FLAG + EOB */
	if (stream->avail_out > 7)
		stream->avail_out -= 7;
	else
		took_space = 0;

	write_deflate_header(stream);	/*For each block */

	if (state->state == LZS2_HDR) {
		write_header(stream);
		if (stream->avail_out < 8)
			return;
	}

	if (state->state == LZS2_BODY) {
		if ((stream->avail_in != 0)) {
			/*we have definitely consumed all the input update pointers */
			state->submitted = stream->avail_in;
			state->last_next_in = stream->next_in;
		}
		fast_lz2_body(stream);
		if ((stream->avail_in != 0)) {
			/*there wasn't enough space in internal state */
			state->left_over = stream->avail_in;
			state->overflow_submitted = state->submitted;
			state->submitted -= state->left_over;
			state->overflow = 1;
		}

		if (state->no_comp) {
			if (state->stored_blk_len != 0)
				state->b_bytes_processed += state->stored_blk_len;
		}
		stream->bytes_consumed = state->submitted
		    - (state->b_bytes_valid - state->b_bytes_processed);

		fast_lz2_finish(stream);
		if (cont_overflow) {
			/* if we are continuing processing an overflowed buffer need to reset
			 * this so that it doesn't add the same value twice
			 */
			last_bytes_consumed -= stream->bytes_consumed;
			cont_overflow = 0;
		}
		if (took_space) {
			/*add space for FLUSH_FLAG + EOB */
			stream->avail_out += 7;
		}
		sync_flush(stream);
		if (state->no_comp) {
			state->no_comp = 0;
		}
		stream->bytes_consumed = state->submitted
		    - (state->b_bytes_valid - state->b_bytes_processed);

		if (state->had_overflow) {
			/*We're in the 2nd half */
			stream->bytes_consumed += last_bytes_consumed;
		}
		if ((state->overflow || state->had_overflow) && state->left_over > 0) {
			stream->avail_in = state->left_over;
		}
		if (!stream->end_of_stream) {
			if (stream->avail_out >= 5 && stream->avail_out <= 8) {
				if (state->had_overflow) {
					/*We're in the 2nd half */
					stream->bytes_consumed -= last_bytes_consumed;
				}
				if (stream->bytes_consumed < state->submitted)
					write_stored_block(stream);

				stream->bytes_consumed = state->submitted
				    - (state->b_bytes_valid - state->b_bytes_processed);
				if (state->had_overflow) {
					/*We're in the 2nd half */
					stream->bytes_consumed += last_bytes_consumed;
				}
			}
			return;
		}		/*end if (!stream->end_of_stream) */
		if (stream->end_of_stream) {
			/*IF we ran out of space on the final block */
			uint32_t temp_submitted = 0;
			if (state->left_over > 0) {
				stream->avail_in = state->left_over;
			}

			if (state->had_overflow)
				temp_submitted = state->overflow_submitted;
			else
				temp_submitted = state->submitted;

			if (state->state == LZS2_TRL) {
				if (stream->bytes_consumed < temp_submitted
				    || state->left_over > 0)
					state->state = LZS2_BODY;

			} else {
				state->state = LZS2_TRL;
			}
		}		/*end if (stream->end_of_stream) */
	}
	/*end if (state->state == LZS2_BODY) */
	if (state->state == LZS2_TRL) {
		if (!state->has_eob) {
			get_eob_code(&code, &len);
			set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
			write_bits(&state->bitbuf, code, len);
			check_space(&state->bitbuf, FORCE_FLUSH);
			bytes = buffer_used(&state->bitbuf);
			stream->next_out = buffer_ptr(&state->bitbuf);
			stream->avail_out -= bytes;
			stream->total_out += bytes;
		}
		if (&state->bitbuf.m_bit_count > 0) {
			set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
			flush(&state->bitbuf);
			bytes = buffer_used(&state->bitbuf);
			stream->next_out = buffer_ptr(&state->bitbuf);
			stream->avail_out -= bytes;
			stream->total_out += bytes;
		}
		state->state = LZS2_END;
	}

}

int fast_lz_stateless(struct LZ_Stream1 *stream)
{
	uint8_t *next_in = stream->next_in;
	const uint32_t avail_in = stream->avail_in;

	uint8_t *next_out = stream->next_out;
	const uint32_t avail_out = stream->avail_out;

	uint32_t crc32 = 0;
	uint32_t stored_len;
	uint32_t dyn_min_len;
	uint32_t min_len;
	uint32_t select_stored_blk = 0;

	if (avail_in == 0)
		stored_len = STORED_BLK_HDR_BZ;
	else
		stored_len =
		    STORED_BLK_HDR_BZ * ((avail_in + STORED_BLK_MAX_BZ - 1) /
					 STORED_BLK_MAX_BZ) + avail_in;

	/*
	   at least 1 byte compressed data in the case of empty dynamic block which only
	   contains the EOB
	 */
#ifdef DEFLATE
	dyn_min_len = deflate_huff_hdr_count * 8 + (deflate_huff_hdr_extra_bits + 7) / 8 + 1;
#else
	dyn_min_len = huff_hdr_count * 8 + (huff_hdr_extra_bits + 7) / 8 + gzip_trl_bytes + 1;
	stored_len += gzip_hdr_bytes + gzip_trl_bytes;
#endif

	min_len = dyn_min_len;

	if (stored_len < dyn_min_len) {
		min_len = stored_len;
		select_stored_blk = 1;
	}

	/*
	   the output buffer should be no less than 8 bytes
	   while empty stored deflate block is 5 bytes only
	 */
	if (avail_out < min_len || stream->avail_out < 8)
		return STATELESS_OVERFLOW;

	if (!select_stored_blk && !stream->internal_state.no_comp) {
		if (fast_lz_int_stateless(stream, next_in, avail_in) == COMP_OK)
			return COMP_OK;
	}
	if (avail_out < stored_len)
		return STATELESS_OVERFLOW;

	init_stream(stream);

	stream->next_in = next_in;
	stream->avail_in = avail_in;
	stream->total_in = 0;

	stream->next_out = next_out;
	stream->avail_out = avail_out;
	stream->total_out = 0;

#ifndef DEFLATE
	crc32 = crc32_gzip(0x0, next_in, avail_in);
#endif
	return write_stored_block_stateless(stream, stored_len, crc32);
}

int fast_lz(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	uint32_t size;
	int ret = COMP_OK;

	if (state->tmp_out_start != state->tmp_out_end) {
		size = state->tmp_out_end - state->tmp_out_start;
		if (size > stream->avail_out)
			size = stream->avail_out;
		memcpy(stream->next_out, state->tmp_out_buff + state->tmp_out_start, size);
		stream->next_out += size;
		stream->avail_out -= size;
		stream->total_out += size;
		state->tmp_out_start += size;
		if (stream->avail_out == 0)
			return ret;
	}
	assert(state->tmp_out_start == state->tmp_out_end);

	if (state->state == LZS2_HDR)
		state->last_flush = stream->flush;

	switch (stream->flush) {
	case SYNC_FLUSH:
	case FINISH_FLUSH:
		break;
	default:
		return INVALID_FLUSH;
	}

	if (state->last_flush != stream->flush) {

		if (state->last_flush == FINISH_FLUSH && stream->flush == SYNC_FLUSH) {
			return INVALID_PARAM;
		} else {
			/* state->last_flush == SYNC_FLUSH && stream->flush == FINISH_FLUSH
			 * Reset it to sync flush to follow internal logic for now
			 */
			stream->flush = SYNC_FLUSH;
		}
	}

	if (stream->flush != SYNC_FLUSH)
		fast_lz_int(stream);
	else
		fast_lz_int_with_flush(stream);

	if (stream->avail_out == 0)
		return ret;

	if (stream->flush != SYNC_FLUSH) {
		if (stream->avail_out < 8) {
			uint8_t *next_out;
			uint32_t avail_out;
			uint32_t total_out;

			next_out = stream->next_out;
			avail_out = stream->avail_out;
			total_out = stream->total_out;

			stream->next_out = state->tmp_out_buff;
			stream->avail_out = sizeof(state->tmp_out_buff);
			stream->total_out = 0;

			if (stream->flush != SYNC_FLUSH)
				fast_lz_int(stream);
			else
				fast_lz_int_with_flush(stream);

			state->tmp_out_start = 0;
			state->tmp_out_end = stream->total_out;

			stream->next_out = next_out;
			stream->avail_out = avail_out;
			stream->total_out = total_out;
			if (state->tmp_out_end) {
				size = state->tmp_out_end;
				if (size > stream->avail_out)
					size = stream->avail_out;
				memcpy(stream->next_out, state->tmp_out_buff, size);
				stream->next_out += size;
				stream->avail_out -= size;
				stream->total_out += size;
				state->tmp_out_start += size;
			}
		}
	}
	return ret;
}

static int write_header_stateless(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;

	uint32_t count_end, count;
	uint64_t *p64;

#ifdef DEFLATE
	count = deflate_huff_hdr_count;
#else
	count = huff_hdr_count;
#endif
	count_end = stream->avail_out / 8;
	if (count >= count_end)
		return STATELESS_OVERFLOW;

	count_end = count;
	p64 = (uint64_t *) stream->next_out;

	stream->avail_out -= count_end * 8;
	stream->total_out += count_end * 8;

	count = 0;

	while (count < count_end) {
#ifdef DEFLATE
		*p64++ = deflate_end_huff_hdr[count++];
#else
		*p64++ = huff_hdr[count++];
#endif
	}
	stream->next_out = (uint8_t *) p64;

	if (stream->avail_out < 8)
		return STATELESS_OVERFLOW;
	else {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
#ifdef DEFLATE
		write_bits(&state->bitbuf, deflate_huff_hdr_extra,
			   deflate_huff_hdr_extra_bits);
#else
		write_bits(&state->bitbuf, huff_hdr_extra, huff_hdr_extra_bits);
#endif
		check_space(&state->bitbuf, FORCE_FLUSH);

		count = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= count;
		stream->total_out += count;
	}

	return COMP_OK;
}

void write_header(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;

	uint32_t count_end, count;
	uint64_t *p64;
	if (stream->flush != FINISH_FLUSH)
		return;
#ifndef DEFLATE
	count = huff_hdr_count - state->count;
#else
	count = deflate_huff_hdr_count - state->count;
#endif
	if (count != 0) {
		count_end = stream->avail_out / 8;
		if (count < count_end)
			count_end = count;

		p64 = (uint64_t *) stream->next_out;
		count = state->count;

		stream->avail_out -= count_end * 8;
		stream->total_out += count_end * 8;
		state->count += count_end;

		count_end += count;

		while (count < count_end) {
#ifndef DEFLATE
			*p64++ = huff_hdr[count++];
#else
			*p64++ = deflate_end_huff_hdr[count++];
#endif
		}
		stream->next_out = (uint8_t *) p64;
#ifndef DEFLATE
		count = huff_hdr_count - state->count;
#else
		count = deflate_huff_hdr_count - state->count;
#endif
	}
	if ((count == 0) && (stream->avail_out >= 8)) {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
#ifndef DEFLATE
		write_bits(&state->bitbuf, huff_hdr_extra, huff_hdr_extra_bits);
#else
		write_bits(&state->bitbuf, deflate_huff_hdr_extra,
			   deflate_huff_hdr_extra_bits);
#endif
		check_space(&state->bitbuf, FORCE_FLUSH);
		state->state = LZS2_BODY;
		state->count = 0;

		count = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= count;
		stream->total_out += count;
	}

}

void write_deflate_header(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;

	uint32_t count_end, count;
	uint64_t *p64;

	if (stream->flush != SYNC_FLUSH)
		return;

	if (state->has_eob_hdr == 1)
		return;

	if (stream->avail_out < 203)
		return;
	count = deflate_huff_hdr_count - state->count;
	if (count != 0) {
		count_end = stream->avail_out / 8;
		if (count < count_end)
			count_end = count;

		p64 = (uint64_t *) stream->next_out;
		count = state->count;

		stream->avail_out -= count_end * 8;
		stream->total_out += count_end * 8;
		state->count += count_end;

		count_end += count;
		if (!stream->end_of_stream) {
			while (count < count_end) {
				*p64++ = deflate_huff_hdr[count++];
			}
		} else {
			while (count < count_end) {
				*p64++ = deflate_end_huff_hdr[count++];
			}
			state->has_eob_hdr = 1;
		}

		stream->next_out = (uint8_t *) p64;
		count = deflate_huff_hdr_count - state->count;
	}
	if ((count == 0) && (stream->avail_out >= 8)) {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);
		write_bits(&state->bitbuf, deflate_huff_hdr_extra,
			   deflate_huff_hdr_extra_bits);
		check_space(&state->bitbuf, FORCE_FLUSH);
		state->count = 0;

		count = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= count;
		stream->total_out += count;
	}

	if (state->state == LZS2_HDR)
		state->state = LZS2_BODY;
}

#if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP1))
uint32_t get_crc(uint32_t crc)
{
	return ~crc;
}
#endif // if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP1))

void write_trailer(struct LZ_Stream1 *stream)
{
	struct LZ_State1 *state = &stream->internal_state;
	unsigned int bytes;

	if (stream->avail_out >= 8) {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);

		/* the flush() will pad to the next byte and write up to 8 bytes
		 * to the output stream/buffer.
		 */
		flush(&state->bitbuf);
#ifndef DEFLATE
		if (stream->flush != SYNC_FLUSH) {
			if (!is_full(&state->bitbuf)) {
#if ((MAJOR_VERSION == IGZIP0C) || (MAJOR_VERSION == IGZIP1C))
				write_bits(&state->bitbuf, crc_512to32(state->crc), 32);
#elif ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP1))
				write_bits(&state->bitbuf, get_crc(state->crc), 32);
#else
# error UNKNOWN MAJOR VERSION MAJOR_VERSION
#endif
				write_bits(&state->bitbuf, file_size(state), 32);
				flush(&state->bitbuf);
				state->state = LZS2_END;
			}
			/* update output buffer */
		} else {
			state->state = LZS2_END;
		}
#else
		state->state = LZS2_END;
#endif
		bytes = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= bytes;
		stream->total_out += bytes;
	}
}

static int write_trailer_stateless(struct LZ_Stream1 *stream, uint32_t avail_in,
				   uint32_t crc32)
{
	struct LZ_State1 *state = &stream->internal_state;
	unsigned int bytes;

	if (stream->avail_out < 8) {
		return STATELESS_OVERFLOW;
	} else {
		set_buf(&state->bitbuf, stream->next_out, stream->avail_out);

		/* the flush() will pad to the next byte and write up to 8 bytes
		 * to the output stream/buffer.
		 */
		flush(&state->bitbuf);
#ifndef DEFLATE
		if (is_full(&state->bitbuf)) {
			return STATELESS_OVERFLOW;
		} else {
#if ((MAJOR_VERSION == IGZIP0C) || (MAJOR_VERSION == IGZIP1C))
			write_bits(&state->bitbuf, crc32, 32);
#endif
			write_bits(&state->bitbuf, avail_in, 32);
			flush(&state->bitbuf);
		}
#endif
		bytes = buffer_used(&state->bitbuf);
		stream->next_out = buffer_ptr(&state->bitbuf);
		stream->avail_out -= bytes;
		stream->total_out += bytes;
	}

	return COMP_OK;
}

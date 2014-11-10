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
#ifndef _IGZIP_H
#define _IGZIP_H

/**
 * @file igzip_lib.h
 *
 * @brief This file defines the igzip compression interface, a high performance
 * deflate compression interface for storage applications.
 * 
 * Deflate is a widely used compression standard that can be used standalone, it
 * also forms the basis of gzip and zlib compression formats. igzip supports the
 * following flush features:
 *
 * - Sync flush: whereby each call to fast_lz returns a new deflate block. Each
 *   deflate block is byte aligned with an empty stored block that is appended
 *   to the compressed output. With this flush mode an accurate bytes consumed
 *   figure is reported in the compression state.
 *
 * - Finish Flush: whereby a call or multiple calls to fast_lz will return a
 *   single deflate block that is not byte aligned. Accurate bytes consumed is
 *   not supported with this flush mode
 *
 * There are 4 major versions selectable at build time:
 *
 * - IGZIP0C: the default version, it uses PCLMULQDQ for CRC calculations and
 *            1 pointer in the hash table.
 * - IGZIP1C: it uses PCLMULQDQ for CRC calculations and a fixed array of 4
 *            pointers in the hash table.
 * - IGZIP0:  similar to IGZIP0C, with the CLMUL requirement no longer necessary.
 * - IGZIP1:  similar to IGZIP1C, with the CLMUL requirement no longer necessary,
 *            and no limit on the hash update.
 *
 * A number of configuration options are available, and can be used and combined
 * to override igzip's defaults.  igzip default configuration is:
 *
 * - 8K window size
 * - IGZIP0C major version
 *
 * These options can be overriden to enable:
 *
 * - 32K window size, a large window size, by adding \#define LARGE_WINDOW 1 in
 *   igzip_lib.h and \%define LARGE_WINDOW 1 in options.inc, or via the command
 *   line with @verbatim gmake D="LARGE_WINDOW=1" @endverbatim
 *   on Linux and FreeBSD, or with
 *   @verbatim nmake -f Makefile.nmake D="-D LARGE_WINDOW=1" @endverbatim on Windows.
 *
 * - A different igzip major version, by passing a variable via command line
 *   with the version to select, such as
 *   @verbatim gmake D="MAJOR_VERSION=IGZIP0C" @endverbatim
 *   on Linux and FreeBSD, and
 *   @verbatim nmake -f Makefile.nmake D="-D MAJOR_VERSION=IGZIP0C" @endverbatim
 *   on Windows.
 *
 * KNOWN ISSUES:
 * - Minimum size output buffer needs to be >218 Bytes, which is the size of the
 *   deflate header and trees.
 * - If building the code on Windows with the 32K window enabled, the
 *   /LARGEADDRESSAWARE:NO link option must be added.
 * - The 32K window isn't supported when used in a shared library.
 *
 */
#include <stdint.h>
#include "types.h"

#ifdef __cplusplus
extern "C" {
#endif

// Options:dir
// c - Use PCLMULQDQ for CRC calc
// m - reschedule mem reads
// e b - bitbuff style
// t s x - compare style
// h - limit hash updates
// l - use longer huffman table
// f - fix cache read

// Invalid Combinations
// --------+----+-------+-------+-------+-------+------------------
//         |          COMPARE TYPE              | LIMIT_HASH_UPDATE
//         | 1  | 2 (t) | 3 (s) | 4 (x) | 5 (y) |
//         +----+-------+-------+-------+-------+------------------
// IGZIP0  | no |       |  no   |  no   |       |
// IGZIP0c |    |  no   |  no   |       |       |
// IGZIP1  | no |       |  no   |  no   |       |      no
// IGZIP1c |    |       |  no   |       |       |
// --------+----+-------+-------+-------+-------+------------------

// All versions are "m" variants
#define IGZIP0  1
#define IGZIP0C 2
#define IGZIP1  3
#define IGZIP1C 4

#ifndef MAJOR_VERSION
# define MAJOR_VERSION IGZIP0C
#endif

#if defined(LARGE_WINDOW)
# define HIST_SIZE 32
#else
# define HIST_SIZE 8
#endif

/* bit buffer types
 * BITBUF8: (e) Always write 8 bytes of data
 * BITBUFB: (b) Always write data
 */
#define USE_BITBUFB

/* compare types
 * 1: ( ) original
 * 2: (t) with CMOV
 * 3: (s) with sttni
 * 4: (x) with xmm / pmovbmsk
 * 5: (y) with ymm / pmovbmsk (32-bytes at a time)
 */
#ifndef COMPARE_TYPE
# define COMPARE_TYPE 1
#endif

#if ((MAJOR_VERSION == IGZIP0C || MAJOR_VERSION == IGZIP1C) && (COMPARE_TYPE == 2 || COMPARE_TYPE == 3))
# undef COMPARE_TYPE
# define COMPARE_TYPE 1
#elif ((MAJOR_VERSION == IGZIP0 || MAJOR_VERSION == IGZIP1C) && COMPARE_TYPE != 2)
# undef COMPARE_TYPE
# define COMPARE_TYPE 2
#endif


/* (h) limit hash update
 *    not supported in version IGZIP1
 */
#if (MAJOR_VERSION != IGZIP1)
# define LIMIT_HASH_UPDATE
#endif

/* (l) longer huffman table */
#define LONGER_HUFFTABLE

/* (f) fix cache read problem */
#define FIX_CACHE_READ

#if (HIST_SIZE > 8)
# undef LONGER_HUFFTABLE
#endif

#define IGZIP_K  1024
#define IGZIP_D  (HIST_SIZE * IGZIP_K)	/* Amount of history */
#define IGZIP_LA (17 * 16)		/* Max look-ahead, rounded up to 32 byte boundary */
#define BSIZE  (2*IGZIP_D + IGZIP_LA)	/* Nominal buffer size */

#define HASH_SIZE  IGZIP_D
#define HASH_MASK  (HASH_SIZE - 1)

#define SHORTEST_MATCH  3

/* Flush Flags */
#define FINISH_FLUSH	  0	/*Default */
#define SYNC_FLUSH	1

/* Return values */
#define COMP_OK 0
#define INVALID_FLUSH -7
#define INVALID_PARAM -8
#define STATELESS_OVERFLOW -1
#define DEFLATE_HDR_LEN 3
/**
 *  @enum LZ_State1_state
 *  @brief Compression State please note LZS2_TRL only applies for GZIP compression
 */
enum LZ_State1_state {
	LZS2_HDR,	//!< Header state
	LZS2_BODY,	//!< Body state
	LZS2_TRL,	//!< Trailer state
	LZS2_END	//!< End state
};

/** @brief Holds Bit Buffer information*/
struct BitBuf2 {
	uint64_t m_bits;	//!< bits in the bit buffer
	uint32_t m_bit_count;	//!< number of valid bits in the bit buffer
	uint8_t *m_out_buf;	//!< current index of buffer to write to
	uint8_t *m_out_end;	//!< end of buffer to write to
	uint8_t *m_out_start;	//!< start of buffer to write to
};

/* Variable prefixes: 
 * b_ : Measured wrt the start of the buffer
 * f_ : Measured wrt the start of the file (aka file_start)
 */

/** @brief Holds the internal state information for input and output compression streams*/
typedef struct LZ_State1 {
	uint32_t b_bytes_valid;	//!< number of bytes of valid data in buffer
	uint32_t b_bytes_processed;	//!< keeps track of the number of bytes processed from the input buffer
	uint8_t *file_start;	//!< pointer to where file would logically start
#if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP1))
	uint32_t crc;	//!< crc
#elif ((MAJOR_VERSION == IGZIP0C) || (MAJOR_VERSION == IGZIP1C))
	DECLARE_ALIGNED(uint32_t crc[16], 16);	//!< actually 4 128-bit integers
#else
# error NO MAJOR VERSION SELECTED
#endif
	struct BitBuf2 bitbuf;	//!< Bit Buffer
	enum LZ_State1_state state;	//!< can be LZS2_HDR, LZS2_BODY, LZS2_TRL, LZS2_END
	uint32_t count;	//!< used for partial header/trailer writes
	uint8_t tmp_out_buff[16];	//!< temporary array
	uint32_t tmp_out_start;	//!< temporary variable
	uint32_t tmp_out_end;	//!< temporary variable
	uint32_t last_flush;	//!< keeps track of last submitted flush
	uint32_t submitted;	//!< keeps track of submitted bytes internally
	uint8_t *last_next_in;	//!< keeps track of last submitted input buffer
	uint32_t has_eob;	//!< keeps track of eob on the last deflate block
	uint32_t has_eob_hdr;	//!< keeps track of eob hdr (with BFINAL set)
	uint32_t stored_blk_len;	//!< keeps track of the length of a stored block
	uint32_t no_comp;	//!< used to copy data into history where a stored block is used
	uint32_t left_over;	//!< keeps track of overflow bytes
	uint32_t overflow_submitted;	//!< keeps track of how many bytes were submitted when overflow
	uint32_t overflow;	//!< indicates we're in an overflow state
	uint32_t had_overflow;	//!< indicates we had overflow state
	DECLARE_ALIGNED(uint8_t buffer[BSIZE + 16], 16);	//!< Internal buffer
#if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP0C))
	DECLARE_ALIGNED(uint16_t head[HASH_SIZE], 16);	//!< Hash array
#elif ((MAJOR_VERSION == IGZIP1) || (MAJOR_VERSION == IGZIP1C))
	DECLARE_ALIGNED(uint64_t head[HASH_SIZE], 16);	//!< Hash array
#else
# error NO MAJOR VERSION SELECTED
#endif

} LZ_State1;

/** @brief Holds stream information*/
typedef struct LZ_Stream1 {
	uint8_t *next_in;	//!< Next input byte
	uint32_t avail_in;	//!< number of bytes available at next_in
	uint32_t total_in;	//!< total number of bytes read so far

	uint8_t *next_out;	//!< Next output byte
	uint32_t avail_out;	//!< number of bytes available at next_out
	uint32_t total_out;	//!< total number of bytes written so far
	uint32_t end_of_stream;	//!< non-zero if this is the last input buffer
	uint32_t flush;	//!< Flush type can be FINISH_FLUSH or SYNC_FLUSH
	uint32_t bytes_consumed;	//!< indicates the number of bytes processed from the input buffer
	struct LZ_State1 internal_state;	//!< Internal state for this stream
} LZ_Stream1;

/**
 * @brief Initialize compression stream data structure
 * @requires SSE4.1, CLMUL
 *
 * @param stream Structure holding state information on the compression streams.
 * @returns none
 */
void init_stream(LZ_Stream1 *stream);


/**
 * @brief Fast data (deflate) compression for storage applications.
 *
 * On entry to fast_lz(), next_in points to an input buffer and avail_in
 * indicates the length of that buffer. Similarly next_out points to an empty
 * output buffer and avail_out indicates the size of that buffer.
 *
 * The fields total_in and total_out start at 0 and are updated by
 * fast_lz(). These reflect the total number of bytes read or written so far.
 *
 * The call to fast_lz() will take data from the input buffer (updating next_in,
 * avail_in and in the case of sync flush bytes_consumed for accurate bytes
 * consumed) and write a compressed stream to the output buffer (updating
 * next_out and avail_out). Without sync flushing the function returns when
 * either avail_in or avail_out goes to zero (i.e. when it runs out of input
 * data or when the output buffer fills up, whichever comes first), producing
 * one contiguous deflate block.
 *
 * With sync flushing the function returns when it runs out of input data
 * (bytes_consumed equals what was submitted in avail_in) or if it runs out of
 * space (gets within 13 bytes from the end of the output buffer, avail_out <=
 * 13bytes), whichever comes first.  It produces one raw deflate block for each
 * input buffer followed by an empty stored block (sync flush per input
 * buffer). When a buffer is submitted, it is copied into the internal state and
 * avail_in is decremented to 0 (the internal state manages the offsets on each
 * successive call to fast_lz()). The bytes_consumed variable will reflect how
 * much of that input buffer has been compressed (for example: if there was not
 * enough space in the output buffer).  NOTE: bytes_consumed indicates exactly
 * what was consumed from the input buffer even if avail_in returns as 0;
 * avail_in needs to be updated if the input buffer was not fully consumed and
 * the stream is re-initialized.
 *
 * When the last input buffer is passed in, NOTE: the end_of_stream flag should
 * be set (FINISH_FLUSH does not indicate this is the last buffer). This will
 * cause the routine to complete the bit stream when it gets to the end of that
 * input buffer, as long as the output buffer is big enough.
 *
 * The equivalent of the zlib FLUSH_SYNC operation is currently supported.
 * Flush types can be SYNC_FLUSH or FINISH_FLUSH. Default value is FINISH_FLUSH.
 * if SYNC_FLUSH is selected each input buffer is compressed and byte aligned
 * with a type 0 block appended to the end.
 * @requires SSE4.1, CLMUL
 *
 * @param  stream Structure holding state information on the compression streams.
 * @return COMP_OK (if everything is ok),
 *         INVALID_FLUSH (if an invalid FLUSH is selected),
 *         INVALID_PARAM (If FLUSH_SYNC is selected after FLUSH_FINISH without
 *         resetting the stream).
 */
int fast_lz(LZ_Stream1 *stream);


/**
 * @brief Fast data (deflate) stateless compression for storage applications.
 *
 * Stateless (one shot) compression routine with a similar interface to
 * fast_lz() but operates on entire input buffer at one time. Parameter
 * avail_out must be large enough to fit the entire compressed output. Max
 * expansion is limited to the input size plus the header size of a stored/raw
 * block.
 * @requires SSE4.1, CLMUL
 *
 * @param  stream Structure holding state information on the compression streams.
 * @return COMP_OK (if everything is ok),
 *         STATELESS_OVERFLOW (if output buffer will not fit output).
 */
int fast_lz_stateless(LZ_Stream1 *stream);


#ifdef __cplusplus
}
#endif
#endif	/* ifndef _IGZIP_H */

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


#ifndef _MB_SHA1_H_
#define _MB_SHA1_H_

/**
 *  @file  mb_sha1.h
 *  @brief Multi-buffer SHA1 function prototypes and structures to submit jobs.
 *
 * Interface for multi-buffer SHA1 functions.
 *
 * <b> Multi-buffer SHA1 Init/Update/Finalize </b>
 *
 * The multi-buffer sha1 interface includes the ability to submit complete
 * buffers for hashing (with init and finalize steps) or jobs in the category of
 * init only (do initialization but no finalize) or update (no init or finalize
 * steps). The job must specify the flags HASH_MB_FIRST and/or HASH_MB_LAST, or
 * HASH_MB_NO_FLAGS to specify between the types of jobs.  Job types without
 * HASH_MB_LAST must be submitted with size as a multiple of the fundamental
 * block size of 64 bytes. Note: The update function is not yet available for 
 * the AVX2 versions, but the job flags and total length must still be set. 
 *
 */

#include "types.h"
#include "multi_buffer.h"

// Define interface to SHA1 base asm code

#ifdef __cplusplus
extern "C" {
#endif

#define NUM_SHA1_DIGEST_WORDS 5
#define NUM_SHA1_LANES 4
#define NUM_SHA1_LANES_X8 8
#define SHA1_BLOCK_SIZE 64

#define ALIGN32		32 
#define ALIGN16		16	


/** @brief Holds arguments for submitted SHA1 job */

typedef struct {
	DECLARE_ALIGNED(UINT32 digest[NUM_SHA1_DIGEST_WORDS][NUM_SHA1_LANES], ALIGN16);
	//!< Holds the working digest for each lane
	UINT8* data_ptr[NUM_SHA1_LANES]; //!< Pointers to working buffer for each lane
} SHA1_ARGS_X4;

/** @brief Holds arguments for submitted SHA1 job */

typedef struct {
	DECLARE_ALIGNED(UINT32 digest[NUM_SHA1_DIGEST_WORDS][NUM_SHA1_LANES_X8], ALIGN32);
	//!< Holds the working digest for each lane
	UINT8*	data_ptr[NUM_SHA1_LANES_X8]; //!< Pointers to working buffer for each lane
} SHA1_ARGS_X8;

/** @brief Holds info describing a single SHA1 job for the multi-buffer manager */

typedef struct {
	UINT8*	buffer;	   //!< pointer to data buffer for this job
	UINT32	len;	   //!< length of buffer for this job in bytes. For finalize can be any length.	 For update must be a multiple of SHA1_BLOCK_SIZE.
	UINT32	len_total; //!< total size of the complete hash in bytes
	DECLARE_ALIGNED(UINT32 result_digest[NUM_SHA1_DIGEST_WORDS],ALIGN16);
			   //!< holds result of hash operation
	JOB_STS status;	   //!< output job status
	UINT32	flags;	   //!< input flags to indicate init, update or finalize
	void*	user_data; //!< pointer for user to keep any job-related data
} JOB_SHA1;

/** @brief SHA1 out-of-order scheduler fields */

typedef struct {
	DECLARE_ALIGNED(UINT8 extra_block[2*64+8], ALIGN32);
	//!< Extra block array - for padding or sub-block message
	JOB_SHA1 *job_in_lane;	//!< address of lane's current job
	UINT32 extra_blocks;	//!< num extra blocks (1 or 2)
	UINT32 size_offset;	//!< offset in extra_block to start of size field
	UINT32 start_offset;	//!< offset to start of data
	UINT32 padding;		//!< padding for internal use
} SHA1_HMAC_LANE_DATA;

/** @brief Holds state for multi-buffer SHA1 jobs */

typedef struct {
	SHA1_ARGS_X4 args;	//!< Structure containing working digests and pointers to input buffers
	UINT64 lens[NUM_SHA1_LANES];
	//!< Length (number of blocks) of each lane's current message
	UINT64 unused_lanes; //!< each byte is index (0...3) of unused lanes, byte 4 is set to FF as a flag
	SHA1_HMAC_LANE_DATA ldata[NUM_SHA1_LANES]; //!< Structure containing lane setup 
} SHA1_MB_MGR;

/** @brief Holds state for multi-buffer SHA1 jobs */

typedef struct {
	SHA1_ARGS_X8 args;	//!< Structure containing working digests and pointers to input buffers
	DECLARE_ALIGNED(UINT32 lens[NUM_SHA1_LANES_X8], ALIGN32);
	//!< Length (number of blocks) of each lane's current message
	UINT64 unused_lanes;	//!< each nibble is index (0...7) of unused lanes, nibble 8 is set to F as a flag
	SHA1_HMAC_LANE_DATA ldata[NUM_SHA1_LANES_X8]; //!< Structure containing lane setup 
} SHA1_MB_MGR_X8;


////////////////////////////////////////////////////////////////////////
// SHA1 out-of-order function protypes

/**
 * @brief Initialize the SHA1 multi-buffer manager structure.
 * @requires SSE4.1
 *
 * @param state    Structure holding jobs state info
 * @returns void
 */

void sha1_init_mb_mgr(SHA1_MB_MGR *state);

/**
 * @brief Initialize the SHA1 multi-buffer manager structure.
 * @requires AVX2
 *
 * @param state    Structure holding jobs state info
 * @returns void
 */

void sha1_init_mb_mgr_x8(SHA1_MB_MGR_X8 *state);

/**
 * @brief  Submit a new SHA1 job to the multi-buffer manager.
 * @requires SSE4.1
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_submit_job(SHA1_MB_MGR *state, JOB_SHA1* job);

/**
 * @brief Finish all submitted SHA1 jobs and return when complete.
 * @requires SSE4.1
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_flush_job(SHA1_MB_MGR *state);

/**
 * @brief  Submit a new SHA1 job to the multi-buffer manager.
 * @requires AVX
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_submit_job_avx(SHA1_MB_MGR *state, JOB_SHA1* job);

/**
 * @brief Finish all submitted SHA1 jobs and return when complete.
 * @requires AVX
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_flush_job_avx(SHA1_MB_MGR *state);

/**
 * @brief  Submit a new SHA1 job to the multi-buffer manager.
 * @requires AVX2
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_submit_job_avx2(SHA1_MB_MGR_X8 *state, JOB_SHA1* job);

/**
 * @brief Finish all submitted SHA1 jobs and return when complete.
 * @requires AVX2
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_SHA1* sha1_flush_job_avx2(SHA1_MB_MGR_X8 *state);

#ifdef __cplusplus
}
#endif

#endif // _MB_SHA1_H_


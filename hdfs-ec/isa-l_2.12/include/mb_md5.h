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


#ifndef _MB_MD5_H
#define _MB_MD5_H

/**
 *  @file  mb_md5.h
 *  @brief Multi-buffer MD5 function prototypes and structures to submit jobs.
 *
 * Interface for multi-buffer MD5 functions.
 *
 * <b> Multi-buffer MD5 Init/Update/Finalize </b>
 *
 * The multi-buffer md5 interface includes the ability to submit complete
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

// Define interface to MD5 base asm code

#ifdef __cplusplus
extern "C" {
#endif

#define NUM_MD5_DIGEST_WORDS 4
#define NUM_MD5_LANES 8
#define NUM_MD5_LANES_X8X2 16
#define MD5_BLOCK_SIZE 64

#define ALIGN16 16
#define ALIGN32 32

/** @brief Holds arguments for submitted MD5 job */

typedef struct {
	DECLARE_ALIGNED(UINT32 digest[NUM_MD5_DIGEST_WORDS][NUM_MD5_LANES], ALIGN16);
	//!< Holds the working digest for each lane
	UINT8* data_ptr[NUM_MD5_LANES]; //!< Pointers to working buffer for each lane
} MD5_ARGS_X8;

/** @brief Holds arguments for submitted AVX2 MD5 job */

typedef struct {
	DECLARE_ALIGNED(UINT32 digest[NUM_MD5_DIGEST_WORDS][NUM_MD5_LANES_X8X2], ALIGN32);
	//!< Holds the working digest for each lane
	UINT8* data_ptr[NUM_MD5_LANES_X8X2]; //!< Pointers to working buffer for each lane
} MD5_ARGS_X8X2;

/** @brief Holds info describing a single MD5 job for the multi-buffer manager */

typedef struct {
	UINT8*	buffer;	   //!< pointer to data buffer for this job
	UINT32	len;	   //!< length of buffer for this job in bytes. For finalize can be any length.	For update must be a multiple of MD5_BLOCK_SIZE.
	UINT32	len_total; //!< total size of the complete hash in bytes
	DECLARE_ALIGNED(UINT32 result_digest[NUM_MD5_DIGEST_WORDS],ALIGN16);
			   //!< holds result of hash operation
	JOB_STS status;	   //!< output job status
	UINT32	flags;	   //!< input flags to indicate init, update or finalize
	void*	user_data; //!< pointer for user to keep any job-related data
} JOB_MD5;

/** @brief MD5 out-of-order scheduler fields */

typedef struct {
	DECLARE_ALIGNED(UINT8 extra_block[2*64+8],ALIGN16); 
	//!< Extra block array - for padding or sub-block message
	JOB_MD5 *job_in_lane;		//!< address of lane's current job
	UINT32 extra_blocks;		//!< num extra blocks (1 or 2)
	UINT32 size_offset;		//!< offset in extra_block to start of size field
	UINT32 start_offset;		//!< offset to start of data
} MD5_HMAC_LANE_DATA;

/** @brief Holds state for multi-buffer MD5 jobs */

typedef struct {
	MD5_ARGS_X8 args; //!< Structure containing working digests and pointers to input buffers
	DECLARE_ALIGNED(UINT32 lens[NUM_MD5_LANES], ALIGN16); 
	//!< Length (number of blocks) of each lane's current message
	UINT64 unused_lanes; //!< each nibble is index (0...7) of unused lanes, nibble 8 is set to F as a flag
	MD5_HMAC_LANE_DATA ldata[NUM_MD5_LANES]; //!< Structure containing lane setup 
} MD5_MB_MGR;

/** @brief Holds state for multi-buffer AVX2 MD5 jobs */

typedef struct {
	MD5_ARGS_X8X2 args;	//!< Structure containing working digests and pointers to input buffers
	DECLARE_ALIGNED(UINT32 lens[NUM_MD5_LANES_X8X2], ALIGN32);
	//!< Length (number of blocks) of each lane's current message
	UINT64 unused_lanes;	//!< each nibble is index (0...15) of unused lanes
	MD5_HMAC_LANE_DATA ldata[NUM_MD5_LANES_X8X2];   //!< Structure containing lane setup 
	UINT32 num_lanes_inuse;	//!< Counter required to supplement unused_lanes in the case of 16 lanes.
} MD5_MB_MGR_X8X2;

////////////////////////////////////////////////////////////////////////
// MD5 out-of-order function protypes

/**
 * @brief Initialize the MD5 multi-buffer manager structure.
 * @requires SSE4.1
 *
 * @param state    Structure holding jobs state info
 * @returns void
 */

void md5_init_mb_mgr(MD5_MB_MGR *state);

/**
 * @brief Initialize the MD5 multi-buffer manager structure.
 * @requires AVX2
 *
 * @param state    Structure holding jobs state info
 * @returns void
 */

void md5_init_mb_mgr_x8x2(MD5_MB_MGR_X8X2 *state);

/**
 * @brief  Submit a new MD5 job to the multi-buffer manager.
 * @requires SSE4.1
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_MD5* md5_submit_job(MD5_MB_MGR *state, JOB_MD5* job);

/**
 * @brief Finish all submitted MD5 jobs and return when complete.
 * @requires SSE4.1
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_MD5* md5_flush_job(MD5_MB_MGR *state);

/**
 * @brief  Submit a new MD5 job to the multi-buffer manager.
 * @requires AVX
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_MD5* md5_submit_job_avx(MD5_MB_MGR *state, JOB_MD5* job);

/**
 * @brief Finish all submitted MD5 jobs and return when complete.
 * @requires AVX
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_MD5* md5_flush_job_avx(MD5_MB_MGR *state);

/**
 * @brief  Submit a new MD5 job to the multi-buffer manager.
 * @requires AVX2
 *
 * @param  state  Structure holding jobs state info
 * @param  job    Structure holding new job info
 * @returns NULL if no jobs complete or pointer to jobs structure.
 */

JOB_MD5* md5_submit_job_avx2(MD5_MB_MGR_X8X2 *state, JOB_MD5* job);

/**
 * @brief Finish all submitted MD5 jobs and return when complete.
 * @requires AVX2
 *
 * @param state    Structure holding jobs state info
 * @returns NULL if no jobs to complete or pointer to jobs structure.
 */

JOB_MD5* md5_flush_job_avx2(MD5_MB_MGR_X8X2 *state);

#ifdef __cplusplus
}
#endif

#endif // ifndef _MB_MD5_H

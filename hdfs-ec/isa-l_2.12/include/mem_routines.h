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

#ifndef MEM_ROUTINES_H
#define MEM_ROUTINES_H

/**
 *  @file mem_routines.h
 *  @brief Interface to storage mem operations
 *
 *  Defines the interface for vector versions of common memory functions.
 *  Vector memory functions are beneficial in some cases to standard library
 *  calls but not in all situations.  Users should select vector versions when
 *  it is known from special use or environmental conditions that they will
 *  likely benefit.
 */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Detect if a memory region is all zero
 *
 * Zero detect function with optimizations for large blocks > 128 bytes
 * @requires AVX
 *
 * @param    mem   Pointer to memory region to test
 * @param    len   Length of region in bytes
 * @returns  0     - region is all zeros
 *           other - region has non zero bytes
 */
int mem_zero_detect_avx(void *mem, int len);

/**
 * @brief Compare two memory blocks
 *
 * Memory compare function with optimizations for large blocks > 128 bytes
 * @requires SSE4.1
 *
 * @param    src   the first memory region
 * @param    des   the second memory region
 * @param    n     the length of each memory region in bytes
 * @returns  0     - the two memory blocks are exactly the same
 *           other - the blocks are not the same
 */
int mem_cmp_sse(void *src, void *des, int n);

/**
 * @brief Compare two memory blocks
 *
 * Memory compare function with optimizations for large blocks > 256 bytes
 * @requires AVX
 *
 * @param    src   the first memory region
 * @param    des   the second memory region
 * @param    n     the length of each memory region in bytes
 * @returns  0     - the two memory blocks are exactly the same
 *           other - the blocks are not the same
 */
int mem_cmp_avx(void *src, void *des, int n);

/**
 * @brief Compare two memory blocks
 *
 * Memory compare function with optimizations for large blocks > 256 bytes
 * @requires AVX2
 *
 * @param    src   the first memory region
 * @param    des   the second memory region
 * @param    n     the length of each memory region in bytes
 * @returns  0     - the two memory blocks are exactly the same
 *           other - the blocks are not the same
 */
int mem_cmp_avx2(void *src, void *des, int n);

/**
 * @brief Copy memory blocks from src to des. Source and destination
 *        addresses cannot overlap.
 *
 * Memory copy function with optimizations for large blocks > 128 bytes
 * @requires SSE2
 *
 * @param    src   the source memory region to copy from
 * @param    des   the destination memory region to copy into
 * @param    n     the length of memory region in bytes
 * @returns        the start address of the destination memory region
 *
 */
void *mem_cpy_sse(void *des, void *src, int n);

/**
 * @brief Copy memory blocks from src to des. Source and destination
 *        addresses cannot overlap.
 *
 * Memory copy function with optimizations for large blocks > 256 bytes
 * @requires AVX
 *
 * @param    src   the source memory region to copy from
 * @param    des   the destination memory region to copy into
 * @param    n     the length of memory region in bytes
 * @returns        the start address of the destination memory region
 *
 */
void *mem_cpy_avx(void *des, void *src, int n);

#ifdef __cplusplus
}
#endif

#endif /* MEM_ROUTINES_H */


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


#ifndef _SHA_H_
#define _SHA_H_

/**
 *  @file  sha.h
 *  @brief SHA1 functions.
 */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Part of the SHA1 hash algorithm that can be run repeatedly
 *        on message blocks of 64 bytes to update the hash value.
 * @requires SSE3
 *
 * @param digest Pointer to digest to update.
 * @param input  Pointer to buffer containing the input message in 64 byte blocks.
 * @param num_blocks Number of 64 byte blocks to incorporate in hash update.
 * @returns None
 */

void sha1_update(unsigned int *digest, unsigned char* input, size_t num_blocks );


/**
 * @brief Performs complete SHA1 algorithm using optimized sha1_update routine.
 * @requires SSE3
 *
 * @param input  Pointer to buffer containing the input message.
 * @param digest Pointer to digest to update.
 * @param len    Length of buffer.
 * @returns None
 */

void sha1_opt(unsigned char *input, unsigned int *digest, int len);

#ifdef __cplusplus
}
#endif

#endif //_SHA_H_

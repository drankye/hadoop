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


/**
 *  @file  crc.h
 *  @brief CRC functions.
 */


#ifndef _CRC_H_
#define _CRC_H_

#include "types.h"

#ifdef __cplusplus
extern "C" {
#endif


/**
 * @brief Generate CRC from the T10 standard.
 * @requires SSE3, CLMUL
 *
 * @returns 16 bit CRC
 */

UINT16 crc16_t10dif_01(
	UINT16 init_crc,          //!< initial CRC value, 16 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);


/**
 * @brief Generate CRC from the T10 standard. Optimized for SLM.
 * @requires SSE4, PCLMULQDQ.
 *
 * @returns 16 bit CRC
 */
UINT16 crc16_t10dif_by4(
	UINT16 init_crc,          //!< initial CRC value, 16 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);

/**
 * @brief Generate CRC from the T10 standard, runs appropriate version.
 *
 * This function determines what instruction sets are enabled and 
 * selects the appropriate version at runtime.
 * @returns 16 bit CRC
 */
UINT16 crc16_t10dif(
	UINT16 init_crc,          //!< initial CRC value, 16 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);


/**
 * @brief Generate CRC from the IEEE standard.
 * @requires SSE3, CLMUL
 *
 * @returns 32 bit CRC
 */

UINT32 crc32_ieee_01(
	UINT32 init_crc,          //!< initial CRC value, 32 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);


/**
 * @brief Generate CRC from the IEEE standard.Optimized for SLM.
 * @requires SSE4, PCLMULQDQ.
 *
 * @returns 32 bit CRC.
 */
UINT32 crc32_ieee_by4(
	UINT32 init_crc,          //!< initial CRC value, 32 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);

/**
 * @brief Generate CRC from the IEEE standard, runs appropriate version.
 * 
 * This function determines what instruction sets are enabled and
 * selects the appropriate version at runtime. 
 *
 * @returns 32 bit CRC
 */

UINT32 crc32_ieee(
	UINT32 init_crc,          //!< initial CRC value, 32 bits
	const unsigned char *buf, //!< buffer to calculate CRC on
	UINT64 len                //!< buffer length in bytes (64-bit data)
	);


/**
 * @brief ISCSI CRC simple implementation with CRC32 instruction.

 * ISCSI CRC function that uses the CRC32 instruction in a simple, codesize
 * efficient manner.
 * @requires SSE4.2
 *
 * @returns 32 bit CRC
 */

unsigned int crc32_iscsi_simple(
	unsigned char *buffer, //!< buffer to calculate CRC on
	int len,               //!< buffer length in bytes
	unsigned int init_crc  //!< initial CRC value
	);


/**
 * @brief ISCSI CRC baseline implementation with CRC32 instruction.

 * ISCSI CRC function using the CRC32 instruction in an unrolled loop.
 * @requires SSE4.2
 *
 * @returns 32 bit CRC
 */

unsigned int crc32_iscsi_baseline(
	unsigned char *buffer, //!< buffer to calculate CRC on
	int len,               //!< buffer length in bytes
	unsigned int init_crc  //!< initial CRC value
	);


/**
 * @brief ISCSI CRC function optimized for Nehalem.
 * @requires SSE4.2
 *
 * @returns 32 bit CRC
 */

unsigned int crc32_iscsi_00(
	unsigned char *buffer, //!< buffer to calculate CRC on
	int len,               //!< buffer length in bytes
	unsigned int init_crc  //!< initial CRC value
	);


/**
 * @brief ISCSI CRC function optimized for Westmere.
 * @requires SSE4.2, CLMUL
 *
 * @returns 32 bit CRC
 */

unsigned int crc32_iscsi_01(
	unsigned char *buffer, //!< buffer to calculate CRC on
	int len,               //!< buffer length in bytes
	unsigned int init_crc  //!< initial CRC value
	);


/**
 * @brief ISCSI CRC function, runs appropriate version.
 *
 * This function determines what instruction sets are enabled and
 * selects the appropriate version at runtime.
 *
 * @returns 32 bit CRC
 */
unsigned int crc32_iscsi(
	unsigned char *buffer, //!< buffer to calculate CRC on
	int len,               //!< buffer length in bytes
	unsigned int init_crc  //!< initial CRC value
	);


/**
 * @brief ISCSI CRC function, baseline version
 * @returns 32 bit CRC
 */
unsigned int crc32_iscsi_base(
	unsigned char *buffer,	//!< buffer to calculate CRC on 
	int len, 		//!< buffer length in bytes
	unsigned int crc_init	//!< initial CRC value
	);


/**
 * @brief Generate CRC from the T10 standard, runs baseline version 
 * @returns 16 bit CRC
 */
UINT16 crc16_t10dif_base(
	UINT16 seed,	//!< initial CRC value, 16 bits 
	UINT8 *buf,	//!< buffer to calculate CRC on 
	UINT64 len 	//!< buffer length in bytes (64-bit data)
	);


/**
 * @brief Generate CRC from the IEEE standard, runs baseline version 
 * @returns 32 bit CRC
 */
UINT32 crc32_ieee_base(
	UINT32 seed, 	//!< initial CRC value, 32 bits
	UINT8 *buf,	//!< buffer to calculate CRC on
	UINT64 len 	//!< buffer length in bytes (64-bit data)
	);

#ifdef __cplusplus
}
#endif

#endif // _CRC_H_

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

#include <stdint.h>

enum {DIST_TABLE_SIZE = 1024};

/* DECODE_OFFSET is dist code index corresponding to DIST_TABLE_SIZE + 1 */
enum { DECODE_OFFSET = 20 };

typedef struct HuffTables {

	/* bits 7:0 are the length
	 * bits 31:8 are the code 
	 */
	uint32_t dist_table[DIST_TABLE_SIZE];

	/* bits 7:0 are the length
	 * bits 31:8 are the code 
	 */
	uint32_t len_table[256];

	/* bits 3:0 are the length
	 * bits 15:4 are the code 
	 */
	uint16_t lit_table[257];

	/* bits 3:0 are the length
	 * bits 15:4 are the code 
	 */
	uint16_t dcodes[30 - DECODE_OFFSET];

} huff_tables;

/**************************************************************
 * Function Prototypes
 */

void get_dist_code(uint32_t dist, uint32_t *code, uint32_t *code_len);
void get_len_code(uint32_t len,	uint32_t *code, uint32_t *code_len);
void get_lit_code(uint32_t lit,	uint32_t *code, uint32_t *code_len);

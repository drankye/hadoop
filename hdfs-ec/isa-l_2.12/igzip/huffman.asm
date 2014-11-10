;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;  Copyright(c) 2011-2014 Intel Corporation All rights reserved.
;
;  Redistribution and use in source and binary forms, with or without
;  modification, are permitted provided that the following conditions 
;  are met:
;    * Redistributions of source code must retain the above copyright
;      notice, this list of conditions and the following disclaimer.
;    * Redistributions in binary form must reproduce the above copyright
;      notice, this list of conditions and the following disclaimer in
;      the documentation and/or other materials provided with the
;      distribution.
;    * Neither the name of Intel Corporation nor the names of its
;      contributors may be used to endorse or promote products derived
;      from this software without specific prior written permission.
;
;  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
;  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
;  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
;  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
;  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
;  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
;  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

%include "options.asm"
%include "lz0a_const.asm"

; Macros for doing Huffman Encoding

%ifdef LONGER_HUFFTABLE
	%if (D > 8192)	
		%error History D is larger than 8K, cannot use %LONGER_HUFFTABLE
		 % error
	%else
		%define DIST_TABLE_SIZE 8192
		%define DECODE_OFFSET     26	
	%endif
%else
	%define DIST_TABLE_SIZE 1024
	%define DECODE_OFFSET     20
%endif

;extern const struct HuffTables {
;
;    // bits 7:0 are the length
;    // bits 31:8 are the code
;    uint32_t dist_table[DIST_TABLE_SIZE];
;
;    // bits 7:0 are the length
;    // bits 31:8 are the code
;    uint32_t len_table[256];
;
;    // bits 3:0 are the length
;    // bits 15:4 are the code
;    uint16_t lit_table[257];
;
;    // bits 3:0 are the length
;    // bits 15:4 are the code
;    uint16_t dcodes[30 - DECODE_OFFSET];
;
;} huff_tables;

extern huff_tables
extern dist_table
extern len_table
extern lit_table
%ifndef LONGER_HUFFTABLE
extern dcodes
%endif

%ifdef LONGER_HUFFTABLE
; Uses RCX, clobbers dist
; get_dist_code	dist, code, len
%macro get_dist_code 3
%define %%dist %1d	; 32-bit IN, clobbered
%define %%distq %1	; 64-bit IN
%define %%code %2d	; 32-bit OUT
%define %%len  %3d	; 32-bit OUT
%define %%lenq %3	; 64-bit TMP

	lea	%%lenq, [dist_table - 4 wrt rip WRT_OPT]
	mov	%%len, [%%lenq + 4*%%distq]
	mov	%%code, %%len
	and	%%len, 0xFF;
	shr	%%code, 8
%endm

%else
; Assumes (dist != 0)
; Uses RCX, clobbers dist
; void compute_dist_code	dist, code, len
%macro compute_dist_code 3
%define %%dist %1	; IN, clobbered
%define %%code %2	; OUT
%define %%len  %3	; OUT

	dec	%%dist
	bsr	ecx, %%dist	; ecx = msb = bsr(dist)
	dec	ecx		; ecx = num_extra_bits = msb - N
	mov	%%code, 1
	shl	%%code, CL
	dec	%%code		; code = ((1 << num_extra_bits) - 1)
	and	%%code, %%dist	; code = extra_bits
	shr	%%dist, CL	; dist >>= num_extra_bits
	lea	%%dist, [%%dist + 2*ecx] ; dist = sym = dist + num_extra_bits*2
	movzx	%%dist, word [dcodes + 2 * (%%dist - DECODE_OFFSET) WRT_OPT]
	mov	%%len, ecx	; len = num_extra_bits
	mov	ecx, %%dist	; ecx = sym
	and	ecx, 0xF	; ecx = sym & 0xF
	shl	%%code, CL	; code = extra_bits << (sym & 0xF)
	shr	%%dist, 4	; dist = (sym >> 4)
	or	%%code, %%dist	; code = (sym >> 4) | (extra_bits << (sym & 0xF))
	add	%%len, ecx	; len = num_extra_bits + (sym & 0xF)
%endm

; Uses RCX, clobbers dist
; get_dist_code	dist, code, len
%macro get_dist_code 3
%define %%dist %1d	; 32-bit IN, clobbered
%define %%code %2d	; 32-bit OUT
%define %%len  %3d	; 32-bit OUT

	cmp	%%dist, DIST_TABLE_SIZE
	jg	%%do_compute
	mov	%%len, [dist_table - 4 + 4*%%dist WRT_OPT]
	mov	%%code, %%len
	and	%%len, 0xFF;
	shr	%%code, 8
	jmp	%%done
%%do_compute:
	compute_dist_code	%%dist, %%code, %%len
%%done:
%endm

%endif


; "len" can be same register as "length"
; get_len_code	length, code, len 
%macro get_len_code 3
%define %%length %1d	; 32-bit IN
%define %%lengthq %1	; 64-bit IN
%define %%code %2d	; 32-bit OUT
%define %%len  %3d	; 32-bit OUT
%define %%lenq %3	; 64-bit TMP

	lea	%%lenq, [len_table - 12 wrt rip WRT_OPT]
	mov	%%len, [%%lenq + 4*%%lengthq]
	mov	%%code, %%len
	and	%%len, 0xFF;
	shr	%%code, 8
%endm

; "len" can be same register as "lit"
; get_lit_code	lit, code, len 
%macro get_lit_code 3
%define %%lit %1d	; 32-bit IN
%define %%litq %1	; 64-bit IN
%define %%code %2d	; 32-bit OUT
%define %%len  %3d	; 32-bit OUT
%define %%lenq %3	; 64-bit TMP
	
	lea	%%lenq, [lit_table wrt rip WRT_OPT]
	movzx	%%len, word [%%lenq + 2*%%litq]
	mov	%%code, %%len
	and	%%len, 0xF;
	shr	%%code, 4
%endm
%macro get_lit_code_const 3
%define %%lit %1	; 32-bit IN  (constant)
%define %%litq %1	; 64-bit IN
%define %%code %2d	; 32-bit OUT
%define %%len  %3d	; 32-bit OUT
%define %%lenq %3	; 64-bit TMP
	
	lea	%%lenq, [lit_table wrt rip WRT_OPT]
	movzx	%%len, word [%%lenq + 2*%%litq]
	mov	%%code, %%len
	and	%%len, 0xF;
	shr	%%code, 4
%endm

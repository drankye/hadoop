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

; note: the type uint256_t is used to access YMM registers and is obtained with:
;     typedef struct {
;        uint64_t low;
;        uint64_t high;
;     } uint128_t;
;     typedef struct {
;        uint128_t low;
;        uint128_t high;
;     } uint256_t;

;; For debugging purposes
%ifdef _DEBUG
%define movntdqa movdqa
%endif

%assign K   1024;
%assign D   8 * K;       ; Amount of history
%assign LA  17 * 16;     ; Max look-ahead, rounded up to 32 byte boundary

%define _xmm(x) xmm %+ x

; copy D + LA bytes from src to dst
; dst is aligned
;void copy_D_LA(uint8_t *dst, uint8_t *src);
; arg 1: rcx : dst
; arg 2: rdx : src
%assign SIZE (D + LA) / 16   ; number of DQ words to be copied
%assign SIZE4 SIZE/4
global copy_D_LA
copy_D_LA:
	lea	rax, [rcx + 4 * 16 * SIZE4]
	jmp	copy_D_LA_1
align 16
copy_D_LA_1:
	movdqu	xmm0, [rdx]
	movdqu	xmm1, [rdx+16]
	movdqu	xmm2, [rdx+32]
	movdqu	xmm3, [rdx+48]
	movdqa	[rcx], xmm0
	movdqa	[rcx+16], xmm1
	movdqa	[rcx+32], xmm2
	movdqa	[rcx+48], xmm3
	add	rdx, 4*16
	add	rcx, 4*16
	cmp	rcx, rax
	jne	copy_D_LA_1
%assign i 0
%rep (SIZE - 4 * SIZE4)
	movdqu	_xmm(i), [rdx + i*16]
%assign i i+1
%endrep
%assign i 0
%rep (SIZE - 4 * SIZE4)
	movdqa	[rcx + i*16], _xmm(i)
%assign i i+1
%endrep
	ret


; copy x bytes (rounded up to 16 bytes) from src to dst
; src & dst are unaligned
;void copy_in(uint8_t *dst, uint8_t *src, uint32_t x);
; arg 1: rcx : dst
; arg 2: rdx : src
; arg 3: r8  : size in bytes
global copy_in
copy_in:
	; align source
	xor	rax, rax
	sub	rax, rdx
	and	rax, 15
	jz	copy_in_1
	; need to align
	movdqu	xmm0, [rdx]
	movdqu	[rcx], xmm0
	add	rcx, rax
	add	rdx, rax
	sub	r8, rax
copy_in_1:
	sub	r8, 49
	jl	copy_in_3
	jmp	copy_in_2
align 16
copy_in_2:
	movntdqa	xmm0, [rdx]
	movntdqa	xmm1, [rdx+16]
	movntdqa	xmm2, [rdx+32]
	movntdqa	xmm3, [rdx+48]
	movdqu	[rcx], xmm0
	movdqu	[rcx+16], xmm1
	movdqu	[rcx+32], xmm2
	movdqu	[rcx+48], xmm3
	add	rdx, 4*16
	add	rcx, 4*16
	sub	r8, 4*16
	jge	copy_in_2
copy_in_3:
	; r8 contains (num bytes left - 49)
	; range: -64 ... -49 :  0 bytes left
	;        -48 ... -33 : 16 bytes left
	;        -32 ... -17 : 32 bytes left
	;        -17 ...  -1 : 48 bytes left
	add	r8, 48
	jl	copy_in_4
	movntdqa	xmm0, [rdx]
	movdqu	[rcx], xmm0
	cmp	r8, 16
	jl	copy_in_4
	movntdqa	xmm0, [rdx+16]
	movdqu	[rcx+16], xmm0
	cmp	r8, 32
	jl	copy_in_4
	movntdqa	xmm0, [rdx+32]
	movdqu	[rcx+32], xmm0
copy_in_4:
	ret

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; void warm_cache_t1(void *ptr, int len)
; arg1 : rcx : ptr to data
; arg2 : rdx : len in bytes
global warm_cache_t1
warm_cache_t1:
	add	rdx, rcx
wc_loop:
	prefetcht1	[rcx];
	add	rcx, 32
	cmp	rcx, rdx
	jl	wc_loop
	ret

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

%ifnidn __OUTPUT_FORMAT__, elf64
global _BitScanReverse
_BitScanReverse:
	bsr	eax, edx
	mov	[rcx],eax
	ret
%endif
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; void _vpcmpeqb(uint256_t *dst, uint256_t *src1, uint256_t *src2)
; arg1: rcx: pointer to dst
; arg2: rdx: pointer to src1
; arg3: r8:  pointer to src2
%ifdef AVX 			;gbt added
global _vpcmpeqb
_vpcmpeqb:
	vmovdqu	ymm0, [rdx]
	vmovdqu	ymm1, [r8]
	vpcmpeqb ymm1, ymm1, ymm0
	vmovdqu [rcx], ymm1
	ret

; uint64_t _vpmovmskb(uint256_t *src)
; arg1:    rcx: pointer to src
; returns: rax: byte mask of src
global _vpmovmskb
_vpmovmskb:
	vmovdqu		ymm0, [rcx]
	vpmovmskb	rax, ymm0
	ret
%endif

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Define a function void ssc(uint64_t x)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
global ssc
ssc:
	mov	rax, rbx
	mov	rbx, rcx
	db	0x64
	db	0x67
	nop
	mov	rbx, rax
	ret


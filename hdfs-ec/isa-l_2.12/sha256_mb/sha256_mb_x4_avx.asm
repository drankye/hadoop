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

%include "sha256_mb_mgr_datastruct.asm"

;; code to compute quad SHA256 using AVX
;; Logic designed/laid out by JDG

; transpose r0, r1, r2, r3, t0, t1
; "transpose" data in {r0..r3} using temps {t0..t3}
; Input looks like: {r0 r1 r2 r3}
; r0 = {a3 a2 a1 a0}
; r1 = {b3 b2 b1 b0}
; r2 = {c3 c2 c1 c0}
; r3 = {d3 d2 d1 d0}
;
; output looks like: {t0 r1 r0 r3}
; t0 = {d0 c0 b0 a0}
; r1 = {d1 c1 b1 a1}
; r0 = {d2 c2 b2 a2}
; r3 = {d3 c3 b3 a3}
; 
%macro TRANSPOSE 6
%define %%r0 %1
%define %%r1 %2
%define %%r2 %3
%define %%r3 %4
%define %%t0 %5
%define %%t1 %6
	vshufps	%%t0, %%r0, %%r1, 0x44	; t0 = {b1 b0 a1 a0}
	vshufps	%%r0, %%r0, %%r1, 0xEE	; r0 = {b3 b2 a3 a2}

	vshufps	%%t1, %%r2, %%r3, 0x44	; t1 = {d1 d0 c1 c0}
	vshufps	%%r2, %%r2, %%r3, 0xEE	; r2 = {d3 d2 c3 c2}

	vshufps	%%r1, %%t0, %%t1, 0xDD	; r1 = {d1 c1 b1 a1}

	vshufps	%%r3, %%r0, %%r2, 0xDD	; r3 = {d3 c3 b3 a3}

	vshufps	%%r0, %%r0, %%r2, 0x88	; r0 = {d2 c2 b2 a2}
	vshufps	%%t0, %%t0, %%t1, 0x88	; t0 = {d0 c0 b0 a0}
%endmacro	


%define TABLE	K256_4_MB
%define SZ	4
%define SZ4	4*SZ
%define ROUNDS 64*SZ4

%define a xmm0
%define b xmm1
%define c xmm2
%define d xmm3
%define e xmm4
%define f xmm5
%define g xmm6
%define h xmm7

%define a0 xmm8
%define a1 xmm9
%define a2 xmm10

%define TT0 xmm14
%define TT1 xmm13
%define TT2 xmm12
%define TT3 xmm11
%define TT4 xmm10
%define TT5 xmm9

%define T1  xmm14
%define TMP xmm15


%macro ROTATE_ARGS 0
%xdefine TMP_ h
%xdefine h g
%xdefine g f
%xdefine f e
%xdefine e d
%xdefine d c
%xdefine c b
%xdefine b a
%xdefine a TMP_
%endm

; PRORD reg, imm, tmp
%macro PRORD 3
%define %%reg %1
%define %%imm %2
%define %%tmp %3
	vpslld	%%tmp, %%reg, (32-(%%imm))
	vpsrld	%%reg, %%reg, %%imm
	vpor	%%reg, %%reg, %%tmp
%endmacro

; non-destructive
; PRORD_nd reg, imm, tmp, src
%macro PRORD_nd 4
%define %%reg %1
%define %%imm %2
%define %%tmp %3
%define %%src %4
	vpslld	%%tmp, %%src, (32-(%%imm))
	vpsrld	%%reg, %%src, %%imm
	vpor	%%reg, %%reg, %%tmp
%endmacro

; PRORD dst/src, amt
%macro PRORD 2
	PRORD	%1, %2, TMP
%endmacro

; PRORD_nd dst, src, amt
%macro PRORD_nd 3
	PRORD_nd	%1, %3, TMP, %2
%endmacro

;; arguments passed implicitly in preprocessor symbols i, a...h
%macro ROUND_00_15 2
%define %%T1 %1
%define %%i  %2


	PRORD_nd	a0, e, (11-6)	; sig1: a0 = (e >> 5)

	vpxor	a2, f, g	; ch: a2 = f^g
	vpand	a2, e		; ch: a2 = (f^g)&e
	vpxor	a2, g		; a2 = ch

	PRORD_nd	a1, e, 25		; sig1: a1 = (e >> 25)
	vmovdqa	[SZ4*(%%i&0xf) + rsp], %%T1
	vpaddd	%%T1, %%T1, [TBL + ROUND]	; T1 = W + K
	vpxor	a0, a0, e	; sig1: a0 = e ^ (e >> 5)
	PRORD	a0, 6		; sig1: a0 = (e >> 6) ^ (e >> 11)
	vpaddd	h, h, a2	; h = h + ch
	PRORD_nd	a2, a, (13-2)	; sig0: a2 = (a >> 11)
	vpaddd	h, h, %%T1	; h = h + ch + W + K
	vpxor	a0, a0, a1	; a0 = sigma1
	PRORD_nd	a1, a, 22	; sig0: a1 = (a >> 22)
	vpxor	%%T1, a, c	; maj: T1 = a^c
	add	ROUND, SZ4	; ROUND++
	vpand	%%T1, %%T1, b	; maj: T1 = (a^c)&b
	vpaddd	h, h, a0

	vpaddd	d, d, h

	vpxor	a2, a2, a	; sig0: a2 = a ^ (a >> 11)
	PRORD	a2, 2		; sig0: a2 = (a >> 2) ^ (a >> 13)
	vpxor	a2, a2, a1	; a2 = sig0
	vpand	a1, a, c	; maj: a1 = a&c
	vpor	a1, a1, %%T1	; a1 = maj
	vpaddd	h, h, a1	; h = h + ch + W + K + maj
	vpaddd	h, h, a2	; h = h + ch + W + K + maj + sigma0

	ROTATE_ARGS
%endm


;; arguments passed implicitly in preprocessor symbols i, a...h
%macro ROUND_16_XX 2
%define %%T1 %1
%define %%i  %2

	vmovdqa	%%T1, [SZ4*((%%i-15)&0xf) + rsp]
	vmovdqa	a1, [SZ4*((%%i-2)&0xf) + rsp]
	vmovdqa	a0, %%T1
	PRORD	%%T1, 18-7
	vmovdqa	a2, a1
	PRORD	a1, 19-17
	vpxor	%%T1, %%T1, a0
	PRORD	%%T1, 7
	vpxor	a1, a1, a2
	PRORD	a1, 17
	vpsrld	a0, a0, 3
	vpxor	%%T1, %%T1, a0
	vpsrld	a2, a2, 10
	vpxor	a1, a1, a2
	vpaddd	%%T1, %%T1, [SZ4*((%%i-16)&0xf) + rsp]
	vpaddd	a1, a1, [SZ4*((%%i-7)&0xf) + rsp]
	vpaddd	%%T1, %%T1, a1

	ROUND_00_15 %%T1, %%i
%endm

%define DIGEST_SIZE	8*SZ4
%define DATA	       16*SZ4
%define ALIGNMENT       1*8
; ALIGNMENT makes FRAMESZ + pushes an odd multiple of 8
%define FRAMESZ (DATA + DIGEST_SIZE + ALIGNMENT)
%define _DIGEST (DATA)
	
%define VMOVPS	vmovups

%define inp0 r8
%define inp1 r9
%define inp2 r10
%define inp3 r11

%ifidn __OUTPUT_FORMAT__, elf64 
 ; Linux definitions
 %define arg1 	rdi
 %define arg2	rsi
%else
 ; Windows definitions
 %define arg1 	rcx
 %define arg2 	rdx
%endif

; Common definitions
%define IDX     rax
%define ROUND	rbx
%define TBL	r12

;; void sha256_mb_x4_avx(SHA256_MB_ARGS_X8 *args, uint64_t len); 
;; arg 1 : arg1 : pointer args (only 4 of the 8 lanes used)
;; arg 2 : arg2 : size of data in blocks (assumed >= 1)
;;
;; Clobbers registers: arg2, rax, rbx, r8-r12, xmm0-xmm15
;;
global sha256_mb_x4_avx:function internal
align 32
sha256_mb_x4_avx:
	sub	rsp, FRAMESZ

	;; Initialize digests
	vmovdqa	a,[arg1+0*SZ4]
	vmovdqa	b,[arg1+1*SZ4]
	vmovdqa	c,[arg1+2*SZ4]
	vmovdqa	d,[arg1+3*SZ4]
	vmovdqa	e,[arg1+4*SZ4]
	vmovdqa	f,[arg1+5*SZ4]
	vmovdqa	g,[arg1+6*SZ4]
	vmovdqa	h,[arg1+7*SZ4]

	lea	TBL,[TABLE wrt rip]
	
	;; transpose input onto stack
	mov	inp0,[arg1 + _data_ptr + 0*8]
	mov	inp1,[arg1 + _data_ptr + 1*8]
	mov	inp2,[arg1 + _data_ptr + 2*8]
	mov	inp3,[arg1 + _data_ptr + 3*8]

	xor	IDX, IDX
lloop:
	xor	ROUND, ROUND

	;; save old digest
	vmovdqa	[rsp + _DIGEST + 0*SZ4], a
	vmovdqa	[rsp + _DIGEST + 1*SZ4], b
	vmovdqa	[rsp + _DIGEST + 2*SZ4], c
	vmovdqa	[rsp + _DIGEST + 3*SZ4], d
	vmovdqa	[rsp + _DIGEST + 4*SZ4], e
	vmovdqa	[rsp + _DIGEST + 5*SZ4], f
	vmovdqa	[rsp + _DIGEST + 6*SZ4], g
	vmovdqa	[rsp + _DIGEST + 7*SZ4], h

%assign i 0
%rep 4
	vmovdqa	TMP, [PSHUFFLE_BYTE_FLIP_MASK wrt rip]
	VMOVPS	TT2,[inp0+IDX+i*16]
	VMOVPS	TT1,[inp1+IDX+i*16]
	VMOVPS	TT4,[inp2+IDX+i*16]
	VMOVPS	TT3,[inp3+IDX+i*16]
	TRANSPOSE	TT2, TT1, TT4, TT3, TT0, TT5
	vpshufb	TT0, TT0, TMP
	vpshufb	TT1, TT1, TMP
	vpshufb	TT2, TT2, TMP
	vpshufb	TT3, TT3, TMP
	ROUND_00_15	TT0,(i*4+0) 
	ROUND_00_15	TT1,(i*4+1) 
	ROUND_00_15	TT2,(i*4+2) 
	ROUND_00_15	TT3,(i*4+3) 
%assign i (i+1)
%endrep
	add	IDX, 4*4*4

	
%assign i (i*4)

	jmp	Lrounds_16_xx
align 16
Lrounds_16_xx:
%rep 16
	ROUND_16_XX	T1, i
%assign i (i+1)
%endrep

	cmp	ROUND,ROUNDS
	jb	Lrounds_16_xx

	;; add old digest
	vpaddd	a, a, [rsp + _DIGEST + 0*SZ4]
	vpaddd	b, b, [rsp + _DIGEST + 1*SZ4]
	vpaddd	c, c, [rsp + _DIGEST + 2*SZ4]
	vpaddd	d, d, [rsp + _DIGEST + 3*SZ4]
	vpaddd	e, e, [rsp + _DIGEST + 4*SZ4]
	vpaddd	f, f, [rsp + _DIGEST + 5*SZ4]
	vpaddd	g, g, [rsp + _DIGEST + 6*SZ4]
	vpaddd	h, h, [rsp + _DIGEST + 7*SZ4]


	sub	arg2, 1
	jne	lloop

	; write digests out
	vmovdqa	[arg1+0*SZ4],a
	vmovdqa	[arg1+1*SZ4],b
	vmovdqa	[arg1+2*SZ4],c
	vmovdqa	[arg1+3*SZ4],d
	vmovdqa	[arg1+4*SZ4],e
	vmovdqa	[arg1+5*SZ4],f
	vmovdqa	[arg1+6*SZ4],g
	vmovdqa	[arg1+7*SZ4],h
	
	; update input pointers
	add	inp0, IDX
	mov	[arg1 + _data_ptr + 0*8], inp0
	add	inp1, IDX
	mov	[arg1 + _data_ptr + 1*8], inp1
	add	inp2, IDX
	mov	[arg1 + _data_ptr + 2*8], inp2
	add	inp3, IDX
	mov	[arg1 + _data_ptr + 3*8], inp3

	;;;;;;;;;;;;;;;;
	;; Postamble
	
	add	rsp, FRAMESZ
	ret

section .data align=64

align 64
TABLE:
	ddq	0x428a2f98428a2f98428a2f98428a2f98
	ddq	0x71374491713744917137449171374491
	ddq	0xb5c0fbcfb5c0fbcfb5c0fbcfb5c0fbcf
	ddq	0xe9b5dba5e9b5dba5e9b5dba5e9b5dba5
	ddq	0x3956c25b3956c25b3956c25b3956c25b
	ddq	0x59f111f159f111f159f111f159f111f1
	ddq	0x923f82a4923f82a4923f82a4923f82a4
	ddq	0xab1c5ed5ab1c5ed5ab1c5ed5ab1c5ed5
	ddq	0xd807aa98d807aa98d807aa98d807aa98
	ddq	0x12835b0112835b0112835b0112835b01
	ddq	0x243185be243185be243185be243185be
	ddq	0x550c7dc3550c7dc3550c7dc3550c7dc3
	ddq	0x72be5d7472be5d7472be5d7472be5d74
	ddq	0x80deb1fe80deb1fe80deb1fe80deb1fe
	ddq	0x9bdc06a79bdc06a79bdc06a79bdc06a7
	ddq	0xc19bf174c19bf174c19bf174c19bf174
	ddq	0xe49b69c1e49b69c1e49b69c1e49b69c1
	ddq	0xefbe4786efbe4786efbe4786efbe4786
	ddq	0x0fc19dc60fc19dc60fc19dc60fc19dc6
	ddq	0x240ca1cc240ca1cc240ca1cc240ca1cc
	ddq	0x2de92c6f2de92c6f2de92c6f2de92c6f
	ddq	0x4a7484aa4a7484aa4a7484aa4a7484aa
	ddq	0x5cb0a9dc5cb0a9dc5cb0a9dc5cb0a9dc
	ddq	0x76f988da76f988da76f988da76f988da
	ddq	0x983e5152983e5152983e5152983e5152
	ddq	0xa831c66da831c66da831c66da831c66d
	ddq	0xb00327c8b00327c8b00327c8b00327c8
	ddq	0xbf597fc7bf597fc7bf597fc7bf597fc7
	ddq	0xc6e00bf3c6e00bf3c6e00bf3c6e00bf3
	ddq	0xd5a79147d5a79147d5a79147d5a79147
	ddq	0x06ca635106ca635106ca635106ca6351
	ddq	0x14292967142929671429296714292967
	ddq	0x27b70a8527b70a8527b70a8527b70a85
	ddq	0x2e1b21382e1b21382e1b21382e1b2138
	ddq	0x4d2c6dfc4d2c6dfc4d2c6dfc4d2c6dfc
	ddq	0x53380d1353380d1353380d1353380d13
	ddq	0x650a7354650a7354650a7354650a7354
	ddq	0x766a0abb766a0abb766a0abb766a0abb
	ddq	0x81c2c92e81c2c92e81c2c92e81c2c92e
	ddq	0x92722c8592722c8592722c8592722c85
	ddq	0xa2bfe8a1a2bfe8a1a2bfe8a1a2bfe8a1
	ddq	0xa81a664ba81a664ba81a664ba81a664b
	ddq	0xc24b8b70c24b8b70c24b8b70c24b8b70
	ddq	0xc76c51a3c76c51a3c76c51a3c76c51a3
	ddq	0xd192e819d192e819d192e819d192e819
	ddq	0xd6990624d6990624d6990624d6990624
	ddq	0xf40e3585f40e3585f40e3585f40e3585
	ddq	0x106aa070106aa070106aa070106aa070
	ddq	0x19a4c11619a4c11619a4c11619a4c116
	ddq	0x1e376c081e376c081e376c081e376c08
	ddq	0x2748774c2748774c2748774c2748774c
	ddq	0x34b0bcb534b0bcb534b0bcb534b0bcb5
	ddq	0x391c0cb3391c0cb3391c0cb3391c0cb3
	ddq	0x4ed8aa4a4ed8aa4a4ed8aa4a4ed8aa4a
	ddq	0x5b9cca4f5b9cca4f5b9cca4f5b9cca4f
	ddq	0x682e6ff3682e6ff3682e6ff3682e6ff3
	ddq	0x748f82ee748f82ee748f82ee748f82ee
	ddq	0x78a5636f78a5636f78a5636f78a5636f
	ddq	0x84c8781484c8781484c8781484c87814
	ddq	0x8cc702088cc702088cc702088cc70208
	ddq	0x90befffa90befffa90befffa90befffa
	ddq	0xa4506ceba4506ceba4506ceba4506ceb
	ddq	0xbef9a3f7bef9a3f7bef9a3f7bef9a3f7
	ddq	0xc67178f2c67178f2c67178f2c67178f2
PSHUFFLE_BYTE_FLIP_MASK: ddq 0x0c0d0e0f08090a0b0405060700010203

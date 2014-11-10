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


%ifidn __OUTPUT_FORMAT__, elf64
 %define arg0  rdi
 %define arg1  rsi
 %define arg2  rdx
 %define arg3  rcx
 %define arg4  r8
 %define arg5  r9
 %define tmp   r11
 %define tmpb  r11b
 %define tmp3  arg4
 %define return rax
 %define func(x) x:
 %define FUNC_SAVE
 %define FUNC_RESTORE
%endif

%ifidn __OUTPUT_FORMAT__, win64
 %define arg0  rcx
 %define arg1  rdx
 %define arg2  r8
 %define arg3  r9
 %define tmp   r11
 %define tmpb  r11b
 %define tmp3  r10
 %define return rax
 %define func(x) proc_frame x
 %macro FUNC_SAVE 0
	end_prolog
 %endmacro
 %macro FUNC_RESTORE 0
 %endmacro
%endif

%define src arg0
%define des arg1
%define	len arg2
%define pos return

default rel

[bits 64]
section .text

align 16
global mem_cmp_sse:function
func(mem_cmp_sse)
	FUNC_SAVE
	mov	pos, 0
	sub	len, 4*16
	jle	.mem_cmp_small_block

.mem_cmp_loop:
	movdqu	xmm0, [src+pos]
	movdqu	xmm1, [src+pos+1*16]
	movdqu	xmm2, [src+pos+2*16]
	movdqu	xmm3, [src+pos+3*16]
	movdqu	xmm4, [des+pos]
	movdqu	xmm5, [des+pos+1*16]
	movdqu	xmm6, [des+pos+2*16]
	movdqu	xmm7, [des+pos+3*16]
	pxor	xmm0, xmm4
	pxor	xmm1, xmm5
	pxor	xmm2, xmm6
	pxor	xmm3, xmm7
	por	xmm0, xmm1
	por	xmm2, xmm3
	por	xmm0, xmm2
	ptest	xmm0, xmm0
	jnz	.return_fail
	add	pos, 4*16
	cmp	pos, len
	jl	.mem_cmp_loop

.mem_cmp_last_block:
	movdqu	xmm0, [src+len]
	movdqu	xmm1, [src+len+1*16]
	movdqu	xmm2, [src+len+2*16]
	movdqu	xmm3, [src+len+3*16]
	movdqu	xmm4, [des+len]
	movdqu	xmm5, [des+len+1*16]
	movdqu	xmm6, [des+len+2*16]
	movdqu	xmm7, [des+len+3*16]
	pxor	xmm0, xmm4
	pxor	xmm1, xmm5
	pxor	xmm2, xmm6
	pxor	xmm3, xmm7
	por	xmm0, xmm1
	por	xmm2, xmm3
	por	xmm0, xmm2
	ptest	xmm0, xmm0
	jnz	.return_fail

.return_pass:
	mov	return, 0
	FUNC_RESTORE
	ret


.mem_cmp_small_block:
	add	len, 4*16
	cmp	len, 3*16
	jl	.mem_cmp_lt48
	vmovdqu	xmm0, [src]
	vmovdqu	xmm1, [src+16]
	vmovdqu	xmm2, [src+16*2]
	vmovdqu xmm3, [des]
	vmovdqu xmm4, [des+16]
	vmovdqu	xmm5, [des+16*2]
	vpxor	xmm6, xmm0, xmm3
	vpxor	xmm7, xmm1, xmm4
	vpxor	xmm8, xmm2, xmm5
	vpor	xmm9, xmm6, xmm7
	vpor	xmm0, xmm9, xmm8
	vptest	xmm0, xmm0
	jnz	.return_fail
	vmovdqu	xmm0, [src+len-16]
	vmovdqu	xmm1, [des+len-16]
	vpxor	xmm2, xmm0, xmm1
	vptest	xmm2, xmm2
	jnz	.return_fail
	jmp	.return_pass


.mem_cmp_lt48:
	cmp	len, 2*16
	jl	.mem_cmp_lt32
	vmovdqu	xmm0, [src]
	vmovdqu	xmm1, [src+16]
	vmovdqu	xmm2, [des]
	vmovdqu	xmm3, [des+16]
	vpxor	xmm4, xmm0, xmm2
	vpxor	xmm5, xmm1, xmm3
	vpor	xmm6, xmm4, xmm5
	vptest	xmm6, xmm6
	jnz	.return_fail
	vmovdqu	xmm0, [src+len-16]
	vmovdqu	xmm1, [des+len-16]
	vpxor	xmm2, xmm0, xmm1
	vptest	xmm2, xmm2
	jnz	.return_fail
	jmp	.return_pass


.mem_cmp_lt32:
	cmp	len, 16
	jl	.mem_cmp_lt16
	vmovdqu	xmm0, [src]
	vmovdqu	xmm1, [des]
	vmovdqu	xmm2, [src+len-16]
	vmovdqu	xmm3, [des+len-16]
	vpxor	xmm4, xmm0, xmm1
	vpxor	xmm5, xmm2, xmm3
	vpor	xmm0, xmm4, xmm5
	vptest	xmm0, xmm0
	jnz	.return_fail
	jmp	.return_pass


.mem_cmp_lt16:
	cmp	len, 8
	jl	.mem_cmp_lt8
	mov	tmp, [src]
	mov	tmp3, [des]
	xor	tmp, tmp3
	test	tmp, tmp
	jnz	.return_fail
	mov	tmp, [src+len-8]
	mov	tmp3, [des+len-8]
	xor	tmp, tmp3
	test	tmp, tmp
	jnz	.return_fail
	jmp	.return_pass

.mem_cmp_lt8:
	cmp	len, 0
	je	.return_pass
.mem_cmp_1byte_loop:
	mov	tmpb, [src+pos]
	cmp	tmpb, [des+pos]
	jnz	.return_fail
	add	pos, 1
	cmp	pos, len
	jl	.mem_cmp_1byte_loop
	jmp	.return_pass

.return_fail:
	mov	return, 1
	FUNC_RESTORE
	ret

endproc_frame


%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro
;;;       func                 core, ver, snum
slversion mem_cmp_sse,           02,  01, 0241

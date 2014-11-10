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
 %define tmp   r11
 %define tmpb  r11b
 %define tmp3  rcx
 %define return rax
 %define func(x) x:
 %define FUNC_SAVE
 %define FUNC_RESTORE
%endif

%ifidn __OUTPUT_FORMAT__, win64
 %define arg0  rcx
 %define arg1  rdx
 %define arg2  r8
 %define tmp   r11
 %define tmpb  r11b
 %define tmp3  rdi
 %define return rax
 %define func(x) proc_frame x
 %macro FUNC_SAVE 0
	end_prolog
 %endmacro
 %macro FUNC_RESTORE 0
 %endmacro
%endif

%define des arg0
%define src arg1
%define	len arg2
%define pos return

default rel

[bits 64]
section .text

align 16
global mem_cpy_avx:function
func(mem_cpy_avx)
	FUNC_SAVE
	mov	pos, 0
	sub	len, 8*32
	jle	.mem_cpy_small_block

.mem_cpy_loop:
	vmovdqu	ymm0, [src+pos]
	vmovdqu	ymm1, [src+pos+1*32]
	vmovdqu	ymm2, [src+pos+2*32]
	vmovdqu	ymm3, [src+pos+3*32]
	vmovdqu	ymm4, [src+pos+4*32]
	vmovdqu	ymm5, [src+pos+5*32]
	vmovdqu	ymm6, [src+pos+6*32]
	vmovdqu	ymm7, [src+pos+7*32]
	vmovdqu	[des+pos], ymm0
	vmovdqu	[des+pos+1*32], ymm1
	vmovdqu	[des+pos+2*32], ymm2
	vmovdqu	[des+pos+3*32], ymm3
	vmovdqu	[des+pos+4*32], ymm4
	vmovdqu	[des+pos+5*32], ymm5
	vmovdqu	[des+pos+6*32], ymm6
	vmovdqu	[des+pos+7*32], ymm7
	add	pos, 8*32
	cmp	pos, len
	jl	.mem_cpy_loop

.mem_cpy_last_block:
	vmovdqu	ymm0, [src+len]
	vmovdqu	ymm1, [src+len+1*32]
	vmovdqu	ymm2, [src+len+2*32]
	vmovdqu	ymm3, [src+len+3*32]
	vmovdqu	ymm4, [src+len+4*32]
	vmovdqu	ymm5, [src+len+5*32]
	vmovdqu	ymm6, [src+len+6*32]
	vmovdqu	ymm7, [src+len+7*32]
	vmovdqu	[des+len], ymm0
	vmovdqu	[des+len+1*32], ymm1
	vmovdqu	[des+len+2*32], ymm2
	vmovdqu	[des+len+3*32], ymm3
	vmovdqu	[des+len+4*32], ymm4
	vmovdqu	[des+len+5*32], ymm5
	vmovdqu	[des+len+6*32], ymm6
	vmovdqu	[des+len+7*32], ymm7

.return_pass:
	mov	return, des
	FUNC_RESTORE
	ret


.mem_cpy_small_block:
	add	len, 8*32
	cmp	len, 4*32
	jl	.mem_cpy_lt128
	vmovdqu	ymm0, [src+pos]
	vmovdqu	ymm1, [src+pos+1*32]
	vmovdqu	ymm2, [src+pos+2*32]
	vmovdqu	ymm3, [src+pos+3*32]
	vmovdqu [des+pos], ymm0
	vmovdqu [des+pos+1*32], ymm1
	vmovdqu	[des+pos+2*32], ymm2
	vmovdqu	[des+pos+3*32], ymm3
	sub	len, 4*32
	add	pos, 4*32


.mem_cpy_lt128:
	cmp	len, 2*32
	jl	.mem_cpy_lt64
	vmovdqu	ymm0, [src+pos]
	vmovdqu	ymm1, [src+pos+32]
	vmovdqu	[des+pos], ymm0
	vmovdqu	[des+pos+32], ymm1
	sub	len, 2*32
	add	pos, 2*32


.mem_cpy_lt64:
	cmp	len, 32
	jl	.mem_cpy_lt32
	vmovdqu	ymm0, [src+pos]
	vmovdqu	[des+pos], ymm0
	sub	len, 32
	add	pos, 32


.mem_cpy_lt32:
	cmp	len, 16
	jl	.mem_cpy_lt16
	add	len, pos
	sub	len, 16
	vmovdqu	xmm0, [src+pos]
	vmovdqu	xmm1, [src+len]
	vmovdqu	[des+pos], xmm0
	vmovdqu	[des+len], xmm1
	jmp	.return_pass


.mem_cpy_lt16:
	cmp	len, 8
	jl	.mem_cpy_lt8
	add	len, pos
	sub	len, 8
	mov	tmp, [src+pos]
	mov	tmp3, [src+len]
	mov	[des+pos], tmp
	mov	[des+len], tmp3
	jmp	.return_pass


.mem_cpy_lt8:
	cmp	len, 0
	je	.return_pass
.mem_cpy_1byte_loop:
	mov	tmpb, [src+pos]
	mov	[des+pos], tmpb
	add	pos, 1
	sub	len, 1
	jg	.mem_cpy_1byte_loop
	jmp	.return_pass

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
slversion mem_cpy_avx,           02,  01, 0237

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
global mem_cpy_sse:function
func(mem_cpy_sse)
	FUNC_SAVE
	mov	pos, 0
	sub	len, 8*16
	jle	.mem_cpy_small_block

.mem_cpy_loop:
	movdqu	xmm0, [src+pos]
	movdqu	xmm1, [src+pos+1*16]
	movdqu	xmm2, [src+pos+2*16]
	movdqu	xmm3, [src+pos+3*16]
	movdqu	xmm4, [src+pos+4*16]
	movdqu	xmm5, [src+pos+5*16]
	movdqu	xmm6, [src+pos+6*16]
	movdqu	xmm7, [src+pos+7*16]
	movdqu	[des+pos], xmm0
	movdqu	[des+pos+1*16], xmm1
	movdqu	[des+pos+2*16], xmm2
	movdqu	[des+pos+3*16], xmm3
	movdqu	[des+pos+4*16], xmm4
	movdqu	[des+pos+5*16], xmm5
	movdqu	[des+pos+6*16], xmm6
	movdqu	[des+pos+7*16], xmm7
	add	pos, 8*16
	cmp	pos, len
	jl	.mem_cpy_loop


.mem_cpy_last_block:
	movdqu	xmm0, [src+len]
	movdqu	xmm1, [src+len+1*16]
	movdqu	xmm2, [src+len+2*16]
	movdqu	xmm3, [src+len+3*16]
	movdqu	xmm4, [src+len+4*16]
	movdqu	xmm5, [src+len+5*16]
	movdqu	xmm6, [src+len+6*16]
	movdqu	xmm7, [src+len+7*16]
	movdqu	[des+len], xmm0
	movdqu	[des+len+1*16], xmm1
	movdqu	[des+len+2*16], xmm2
	movdqu	[des+len+3*16], xmm3
	movdqu	[des+len+4*16], xmm4
	movdqu	[des+len+5*16], xmm5
	movdqu	[des+len+6*16], xmm6
	movdqu	[des+len+7*16], xmm7


.return_pass:
	mov	return, des
	FUNC_RESTORE
	ret


.mem_cpy_small_block:
	add	len, 8*16
	cmp	len, 4*16
	jl	.mem_cpy_lt64
	movdqu	xmm0, [src+pos]
	movdqu	xmm1, [src+pos+1*16]
	movdqu	xmm2, [src+pos+2*16]
	movdqu	xmm3, [src+pos+3*16]
	movdqu	[des+pos], xmm0
	movdqu	[des+pos+1*16], xmm1
	movdqu	[des+pos+2*16], xmm2
	movdqu	[des+pos+3*16], xmm3
	sub	len, 4*16
	add	pos, 4*16


.mem_cpy_lt64:
	cmp	len, 2*16
	jl	.mem_cpy_lt32
	movdqu	xmm0, [src+pos]
	movdqu	xmm1, [src+pos+16]
	movdqu	[des+pos], xmm0
	movdqu	[des+pos+16], xmm1
	sub	len, 2*16
	add	pos, 2*16


.mem_cpy_lt32:
	cmp	len, 16
	jl	.mem_cpy_lt16
	add	len, pos
	sub	len, 16
	movdqu	xmm0, [src+pos]
	movdqu	xmm1, [src+len]
	movdqu	[des+pos], xmm0
	movdqu	[des+len], xmm1
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
slversion mem_cpy_sse,           02,  01, 0236

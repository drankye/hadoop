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

%include "reg_sizes.asm"

default rel
[bits 64]

%ifidn __OUTPUT_FORMAT__, elf64
%define WRT_OPT         wrt ..plt
%else
%define WRT_OPT
%endif

extern pq_gen_base
extern pq_gen_sse
extern pq_gen_avx
extern pq_gen_avx2

extern xor_gen_base
extern xor_gen_sse
extern xor_gen_avx

extern pq_check_base
extern pq_check_sse

extern xor_check_base
extern xor_check_sse

section .data
;;; *_mbinit are initial values for *_dispatched; is updated on first call.
;;; Therefore, *_dispatch_init is only executed on first call.

xor_gen_dispatched:
	dq	xor_gen_mbinit
pq_gen_dispatched:
	dq	pq_gen_mbinit
xor_check_dispatched:
	dq      xor_check_mbinit
pq_check_dispatched:
	dq      pq_check_mbinit

section .text
;;;;
; pq_gen multibinary function
;;;;
global pq_gen:function
pq_gen_mbinit:
	call	pq_gen_dispatch_init
pq_gen:
	jmp	qword [pq_gen_dispatched]

pq_gen_dispatch_init:
	push 	rax
	push	rbx
	push	rcx
	push	rdx
	push	rsi
	lea     rsi, [pq_gen_base WRT_OPT] ; Default

	mov	eax, 1
	cpuid
	test	ecx, FLAG_CPUID1_ECX_SSE4_2
	lea     rbx, [pq_gen_sse WRT_OPT]

	cmovne	rsi, rbx
	and	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)
	cmp	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)
	lea     rbx, [pq_gen_avx WRT_OPT]

	jne     _done_pq_dispatch_init
	mov	rsi, rbx

	;; Try for AVX2
	xor	ecx, ecx
	mov	eax, 7
	cpuid
	test	ebx, FLAG_CPUID1_EBX_AVX2
	lea     rbx, [pq_gen_avx2 WRT_OPT]
	cmovne	rsi, rbx

	;; Does it have xmm and ymm support
	xor	ecx, ecx
	xgetbv
	and	eax, FLAG_XGETBV_EAX_XMM_YMM 
	cmp	eax, FLAG_XGETBV_EAX_XMM_YMM
	je	_done_pq_dispatch_init
	lea     rsi, [pq_gen_sse WRT_OPT]

_done_pq_dispatch_init:
	mov	[pq_gen_dispatched], rsi
	pop	rsi
	pop	rdx
	pop	rcx
	pop	rbx
	pop	rax
	ret

;;;;
; pq_check multibinary function
;;;;
global pq_check:function
pq_check_mbinit:
	call	pq_check_dispatch_init
pq_check:
	jmp     qword [pq_check_dispatched]

pq_check_dispatch_init:
	push    rax
	push    rbx
	push    rcx
	push    rdx
	push    rsi
	lea     rsi, [pq_check_base WRT_OPT] ; Default

	mov     eax, 1
	cpuid
	test    ecx, FLAG_CPUID1_ECX_SSE4_1
	lea     rbx, [pq_check_sse WRT_OPT]
	cmovne	rsi, rbx

	mov     [pq_check_dispatched], rsi
	pop     rsi
	pop     rdx
	pop     rcx
	pop     rbx
	pop     rax
	ret

;;;;
; xor_gen multibinary function
;;;;
global xor_gen:function
xor_gen_mbinit:
	call	xor_gen_dispatch_init
xor_gen:
	jmp	qword [xor_gen_dispatched]

xor_gen_dispatch_init:
	push 	rax
	push	rbx
	push	rcx
	push	rdx
	push	rsi
	lea     rsi, [xor_gen_base WRT_OPT]
	
	mov	eax, 1
	cpuid
	test	ecx, FLAG_CPUID1_ECX_SSE4_2

	lea     rbx, [xor_gen_sse WRT_OPT]
	cmovne	rsi, rbx

	;; Try for AVX
	and	ecx, (FLAG_CPUID1_ECX_OSXSAVE | FLAG_CPUID1_ECX_AVX)
	cmp	ecx, (FLAG_CPUID1_ECX_OSXSAVE | FLAG_CPUID1_ECX_AVX)
	jne	_done_xor_dispatch_init
	
	;; Does it have xmm and ymm support
	xor	ecx, ecx
	xgetbv
	and	eax, FLAG_XGETBV_EAX_XMM_YMM 
	cmp	eax, FLAG_XGETBV_EAX_XMM_YMM	
	jne	_done_xor_dispatch_init
	lea     rsi, [xor_gen_avx WRT_OPT]

_done_xor_dispatch_init:
	mov	[xor_gen_dispatched], rsi
	pop	rsi
	pop	rdx
	pop	rcx
	pop	rbx
	pop	rax
	ret

;;;;
; xor_check multibinary function
;;;;
global xor_check:function
xor_check_mbinit:
        call    xor_check_dispatch_init
xor_check:
        jmp     qword [xor_check_dispatched]

xor_check_dispatch_init:
        push    rax
        push    rbx
        push    rcx
        push    rdx
        push    rsi
	lea     rsi, [xor_check_base WRT_OPT] ; Default
      
	mov     eax, 1
        cpuid
        test    ecx, FLAG_CPUID1_ECX_SSE4_1
	lea     rbx, [xor_check_sse WRT_OPT]
        cmovne  rsi, rbx
      
	mov     [xor_check_dispatched], rsi
        pop     rsi
        pop     rdx
        pop     rcx
        pop     rbx
        pop     rax
        ret


%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro

;;;       func          	core, ver, snum
slversion xor_gen,		00,   02,  0126
slversion xor_check,		00,   02,  0127
slversion pq_gen,		00,   02,  0128
slversion pq_check,		00,   02,  0129

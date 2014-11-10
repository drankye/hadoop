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
%define WRT_OPT		wrt ..plt
%else
%define WRT_OPT
%endif

%include "reg_sizes.asm"
default rel
[bits 64]

%define def_wrd 	dq
%define wrd_sz  	qword
%define arg1		rsi

; declare the L3 ctx level symbols (these will then call the appropriate 
; L2 symbols)
extern sha256_ctx_mgr_init_sse
extern sha256_ctx_mgr_submit_sse
extern sha256_ctx_mgr_flush_sse

extern sha256_ctx_mgr_init_avx
extern sha256_ctx_mgr_submit_avx
extern sha256_ctx_mgr_flush_avx

extern sha256_ctx_mgr_init_avx2
extern sha256_ctx_mgr_submit_avx2
extern sha256_ctx_mgr_flush_avx2


section .data
;;; *_mbinit are initial values for *_dispatched; is updated on first call.
;;; Therefore, *_dispatch_init is only executed on first call.

; Three separate symbols, though all 3 _mbinits may be set together..?
sha256_ctx_mgr_init_dispatched:
	def_wrd		sha256_ctx_mgr_init_mbinit
sha256_ctx_mgr_submit_dispatched:
	def_wrd		sha256_ctx_mgr_submit_mbinit
sha256_ctx_mgr_flush_dispatched:
	def_wrd		sha256_ctx_mgr_flush_mbinit


section .text
;;;;
; sha256_ctx_mgr_init multibinary function
;;;;
global sha256_ctx_mgr_init:function
sha256_ctx_mgr_init_mbinit:
	call	sha256_ctx_mgr_init_dispatch_init

sha256_ctx_mgr_init:
	jmp	wrd_sz [sha256_ctx_mgr_init_dispatched]

sha256_ctx_mgr_init_dispatch_init:
	push    arg1 
	push    rax
	push    rbx
	push    rcx
	push    rdx

	; SSE by default
	lea     arg1, [sha256_ctx_mgr_init_sse WRT_OPT] 

	; Test for AVX
	mov     eax, 1
	cpuid
	and	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	cmp	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	lea     rbx, [sha256_ctx_mgr_init_avx WRT_OPT]

	; If AVX, set AVX symbol, else exit with SSE
	jne	_done_sha256_ctx_mgr_init_dispatch_init
	mov	arg1, rbx

	; Test AVX2
	xor	ecx, ecx
	mov	eax, 7
	cpuid
	test	ebx, FLAG_CPUID1_EBX_AVX2
	lea	rbx, [sha256_ctx_mgr_init_avx2 WRT_OPT]
	cmovne	arg1, rbx

	; If it has the AVX2 flag, set the avx2 symbol, but check the YMM support
	xor	ecx, ecx
	xgetbv
	and	eax, FLAG_XGETBV_EAX_XMM_YMM 
	cmp	eax, FLAG_XGETBV_EAX_XMM_YMM 

	; If it has XMM/YMM, exit with AVX or AVX2 symbol, otherwise reset to SSE
	je	_done_sha256_ctx_mgr_init_dispatch_init
	lea	arg1, [sha256_ctx_mgr_init_sse WRT_OPT]
	
_done_sha256_ctx_mgr_init_dispatch_init:
	pop     rdx
	pop     rcx
	pop     rbx
	pop     rax
	mov     [sha256_ctx_mgr_init_dispatched], arg1 
	pop     arg1 
	ret

;;;;
; sha256_ctx_mgr_submit multibinary function
;;;;
global sha256_ctx_mgr_submit:function
sha256_ctx_mgr_submit_mbinit:
	call	sha256_ctx_mgr_submit_dispatch_submit

sha256_ctx_mgr_submit:
	jmp	wrd_sz [sha256_ctx_mgr_submit_dispatched]

sha256_ctx_mgr_submit_dispatch_submit:
	push    arg1 
	push    rax
	push    rbx
	push    rcx
	push    rdx

	; SSE by default
	lea     arg1, [sha256_ctx_mgr_submit_sse WRT_OPT] 

	; Test for AVX
	mov     eax, 1
	cpuid
	and	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	cmp	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	lea     rbx, [sha256_ctx_mgr_submit_avx WRT_OPT]

	; If AVX, set AVX symbol, else exit with SSE
	jne	_done_sha256_ctx_mgr_submit_dispatch_submit
	mov	arg1, rbx

	; Test AVX2
	xor	ecx, ecx
	mov	eax, 7
	cpuid
	test	ebx, FLAG_CPUID1_EBX_AVX2
	lea	rbx, [sha256_ctx_mgr_submit_avx2 WRT_OPT]
	cmovne	arg1, rbx

	; If it has the AVX2 flag, set the avx2 symbol, but check the YMM support
	xor	ecx, ecx
	xgetbv
	and	eax, FLAG_XGETBV_EAX_XMM_YMM 
	cmp	eax, FLAG_XGETBV_EAX_XMM_YMM 

	; If it has XMM/YMM, exit with AVX or AVX2 symbol, otherwise reset to SSE
	je	_done_sha256_ctx_mgr_submit_dispatch_submit
	lea	arg1, [sha256_ctx_mgr_submit_sse WRT_OPT]
	
_done_sha256_ctx_mgr_submit_dispatch_submit:
	pop     rdx
	pop     rcx
	pop     rbx
	pop     rax
	mov     [sha256_ctx_mgr_submit_dispatched], arg1 
	pop     arg1 
	ret


;;;;
; sha256_ctx_mgr_flush multibinary function
;;;;
global sha256_ctx_mgr_flush:function
sha256_ctx_mgr_flush_mbinit:
	call	sha256_ctx_mgr_flush_dispatch_flush

sha256_ctx_mgr_flush:
	jmp	wrd_sz [sha256_ctx_mgr_flush_dispatched]

sha256_ctx_mgr_flush_dispatch_flush:
	push    arg1 
	push    rax
	push    rbx
	push    rcx
	push    rdx

	; SSE by default
	lea     arg1, [sha256_ctx_mgr_flush_sse WRT_OPT] 

	; Test for AVX
	mov     eax, 1
	cpuid
	and	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	cmp	ecx, (FLAG_CPUID1_ECX_AVX | FLAG_CPUID1_ECX_OSXSAVE)	
	lea     rbx, [sha256_ctx_mgr_flush_avx WRT_OPT]

	; If AVX, set AVX symbol, else exit with SSE
	jne	_done_sha256_ctx_mgr_flush_dispatch_flush
	mov	arg1, rbx

	; Test AVX2
	xor	ecx, ecx
	mov	eax, 7
	cpuid
	test	ebx, FLAG_CPUID1_EBX_AVX2
	lea	rbx, [sha256_ctx_mgr_flush_avx2 WRT_OPT]
	cmovne	arg1, rbx

	; If it has the AVX2 flag, set the avx2 symbol, but check the YMM support
	xor	ecx, ecx
	xgetbv
	and	eax, FLAG_XGETBV_EAX_XMM_YMM 
	cmp	eax, FLAG_XGETBV_EAX_XMM_YMM 

	; If it has XMM/YMM, exit with AVX or AVX2 symbol, otherwise reset to SSE
	je	_done_sha256_ctx_mgr_flush_dispatch_flush
	lea	arg1, [sha256_ctx_mgr_flush_sse WRT_OPT]
	
_done_sha256_ctx_mgr_flush_dispatch_flush:
	pop     rdx
	pop     rcx
	pop     rbx
	pop     rax
	mov     [sha256_ctx_mgr_flush_dispatched], arg1 
	pop     arg1 
	ret




%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro

;;;       func				core, ver, snum
slversion sha256_ctx_mgr_init,		00,   01,  0160
slversion sha256_ctx_mgr_submit,	00,   01,  0161
slversion sha256_ctx_mgr_flush,		00,   01,  0162

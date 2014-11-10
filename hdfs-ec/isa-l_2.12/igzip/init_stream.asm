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
%include "data_struct2.asm"

; void init_stream(LZ_Stream2 *stream)
; arg 1: rcx: addr of stream
global init_stream:function
init_stream:
%ifidn __OUTPUT_FORMAT__, elf64
	mov	rcx, rdi
%endif

	xor	rax, rax
	mov	[rcx + _total_in], eax
	mov	[rcx + _total_out], eax
	mov	[rcx + _internal_state_b_bytes_valid], eax
	mov	[rcx + _flush], eax
	mov	[rcx + _bytes_consumed], eax
	mov	[rcx + _internal_state_last_flush], eax
	mov	[rcx + _internal_state_b_bytes_processed], eax
	mov	[rcx + _internal_state_submitted], eax
	mov	[rcx + _internal_state_has_eob], eax
	mov	[rcx + _internal_state_has_eob_hdr], eax
	mov	[rcx + _internal_state_stored_blk_len], eax
	mov	[rcx + _internal_state_no_comp], eax
	mov	[rcx + _internal_state_left_over], eax
	mov	[rcx + _internal_state_overflow_submitted], eax
	mov	[rcx + _internal_state_overflow], eax
	mov	[rcx + _internal_state_had_overflow], eax
	mov	[rcx + _internal_state_last_flush], eax
	mov	dword [rcx + _internal_state_state], LZS2_HDR
	mov	[rcx + _internal_state_count], eax

	; tmp_out_start = tmp_out_end = 0
	mov	[rcx + _internal_state_tmp_out_start], eax
	mov	[rcx + _internal_state_tmp_out_end], eax

	; file_start = &buffer[0];
	lea	rdx, [rcx + _internal_state_buffer]
	mov	[rcx + _internal_state_file_start], rdx

	; state->bitbuf.init();
	mov	[rcx + _internal_state_bitbuf_m_bits], rax
	mov	[rcx + _internal_state_bitbuf_m_bit_count], eax

%if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP1))
	; init crc
	not	rax
	mov	[rcx + _internal_state_crc], eax
%elif ((MAJOR_VERSION == IGZIP0C) || (MAJOR_VERSION == IGZIP1C))
	;;; MAGIC 512-bit number that will become 0xFFFFFFFF after folding
	pxor xmm0, xmm0
	movdqa	[rcx + _internal_state_crc + 48], xmm0
	movdqa	[rcx + _internal_state_crc + 32], xmm0
	movdqa	[rcx + _internal_state_crc + 16], xmm0
	mov eax, 0x9db42487
	movd xmm0, eax
	movdqa	[rcx + _internal_state_crc], xmm0
%else
 %error NO MAJOR VERSION SELECTED
 % error
%endif

	; for (i=0; i<HASH_SIZE; i++) state->head[i] = (uint16_t) -(int)(D + 1);
	movdqa	xmm0, [init_val wrt rip]
	add	rcx, _internal_state_head


%if ((MAJOR_VERSION == IGZIP0) || (MAJOR_VERSION == IGZIP0C))
	mov	rax, HASH_SIZE / (4 * 8)
%elif ((MAJOR_VERSION == IGZIP1) || (MAJOR_VERSION == IGZIP1C))
	mov	rax, HASH_SIZE / (4 * 2)
%else
	%error NO MAJOR VERSION SELECTED
		% error
%endif

init_loop:
	movdqa	[rcx + 16 * 0], xmm0
	movdqa	[rcx + 16 * 1], xmm0
	movdqa	[rcx + 16 * 2], xmm0
	movdqa	[rcx + 16 * 3], xmm0
	add	rcx, 16*4
	sub	rax, 1
	jne	init_loop

	ret

%assign VAL (-(D + 1)) & 0xFFFF
align 16
init_val:
	dw	VAL, VAL, VAL, VAL, VAL, VAL, VAL, VAL

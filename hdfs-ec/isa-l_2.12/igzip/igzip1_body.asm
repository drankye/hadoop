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

%if (MAJOR_VERSION == IGZIP1)

%include "lz0a_const.asm"
%include "data_struct2.asm"
%include "bitbuf2.asm"
%include "huffman.asm"
%include "utils.asm"
%include "crc.asm"
%include "hash.asm"

%include "stdmac.asm"
%include "reg_sizes.asm"

%macro MARK 1
global %1
%1:
%endm


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
%if (COMPARE_TYPE == 2)

%define tmp1		rax
%define code		rax

%define tmp2		rbx
%define tmp5		rbx
%define code2		rbx

%define tmp3		rcx
%define f_end_i		rcx
%define tmp_dist	rcx

%define in_buf		rdx
%define crc			rdx
%define tmp_c1		rdx

%define f_i			rsi

%define tmp_c2		rdi

%define b_bytes_valid	rbp
%define f_indices	rbp
%define code_len2	rbp

%define tmp4		r8
%define len			r8

%define dist		r9

%define x			r10
%define file_start	r10

%define	stream		r11

%define blen		r12
%define hash		r12
%define tmp_len		r12

%define m_bit_count	r13

%define m_bits		r14

%define	m_out_buf	r15

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
%elif (COMPARE_TYPE == 3) || (COMPARE_TYPE == 1) || (COMPARE_TYPE == 4)
	%error not yet implemented
	 % error

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
%else
	%error Unknown Compare type COMPARE_TYPE
	 % error
%endif
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

%define b_bytes_processed	f_i

%define blen_mem_offset		0
%define in_buf_mem_offset	8
%define f_end_i_mem_offset	16
%define stack_size		24


; void fast_lz2_body ( LZ_Stream2 *stream )
; arg 1: rcx: addr of stream
global fast_lz2_body
fast_lz2_body:
%ifidn __OUTPUT_FORMAT__, elf64
	mov	rcx, rdi
%endif

	;; do nothing if (avail_in == 0)
	cmp	dword [rcx + _avail_in], 0
	jne	skip1
	ret
skip1:

	PUSH_ALL	rbx, rsi, rdi, rbp, r12, r13, r14, r15

	sub	rsp, stack_size

	mov	stream, rcx

	mov dword [stream + _internal_state_has_eob], 0

	; state->bitbuf.set_buf(stream->next_out, stream->avail_out);
	mov	m_out_buf, [stream + _next_out]
	mov	[stream + _internal_state_bitbuf_m_out_start], m_out_buf
	mov	tmp1 %+ d, [stream + _avail_out]
	add	tmp1, m_out_buf
	cmp dword [stream + _internal_state_no_comp], 1
	je skip_SLOP
	sub	tmp1, SLOP
skip_SLOP:
	mov	[stream + _internal_state_bitbuf_m_out_end], tmp1

	mov	m_bits, [stream + _internal_state_bitbuf_m_bits]
	mov	m_bit_count %+ d, [stream + _internal_state_bitbuf_m_bit_count]

	; in_buf = stream->next_in
	mov	in_buf, [stream + _next_in]
	mov	blen %+ d, [stream + _avail_in]

	; while (blen != 0)
MARK __Compute_X
loop1:
	; x = D + LA - (state->b_bytes_valid - state->b_bytes_processed);
	mov	b_bytes_valid %+ d, [stream + _internal_state_b_bytes_valid]
	mov	b_bytes_processed %+ d, [stream + _internal_state_b_bytes_processed]
	lea	x, [b_bytes_processed + D + LA]
	sub	x, b_bytes_valid

	; if (x > D) x = D;
	cmp	x, D
	cmova	x, [const_D wrt rip]

	; if (blen < D) x = blen;
	cmp	blen, D
	cmovb	x, blen

	;; process x bytes starting at in_buf

	;; If there isn't enough room, shift buffer down
	; if (x > BSIZE - state->b_bytes_valid) {
	mov	tmp1, BSIZE
	sub	tmp1, b_bytes_valid
	cmp	x, tmp1
	jbe	skip_move

	; if (state->b_bytes_processed < state->b_bytes_valid - LA) {
	mov	tmp1, b_bytes_valid
	sub	tmp1, LA
	cmp	b_bytes_processed, tmp1
	jae	do_move

	;; We need to move an odd amount, skip move for this copy of loop
	xor	x,x
	mov	[rsp + blen_mem_offset], blen
	jmp	skip_move_zero

MARK __shift_data_down
do_move:
	; offset = state->b_bytes_valid - (D + LA);
	mov	tmp4, b_bytes_valid
	sub	tmp4, D + LA
	; copy_D_LA(state->buffer, state->buffer + offset);
	lea	tmp1, [stream + _internal_state_buffer]
	lea	tmp2, [tmp1 + tmp4]
	copy_D_LA	tmp1, tmp2, tmp3, xmm0, xmm1, xmm2, xmm3	; tmp1 clobbered
	
	; state->file_start        -= offset;
	sub	[stream + _internal_state_file_start], tmp4
	; state->b_bytes_processed -= offset;
	sub	b_bytes_processed, tmp4
	mov	b_bytes_valid, D + LA

MARK __copy_in
skip_move:
	sub	blen, x

	mov	[rsp + blen_mem_offset], blen
	
	; copy_in(state->buffer + state->b_bytes_valid, in_buf, x);
	lea	tmp1, [stream + _internal_state_buffer + b_bytes_valid]
	mov	tmp2, in_buf
	mov	tmp3, x
	copy_in	tmp1, tmp2, tmp3, tmp4

	; in_buf += x;
	add	in_buf, x
MARK __prepare_loop
skip_move_zero:
	mov	[rsp + in_buf_mem_offset], in_buf
	; state->b_bytes_valid += x;
	add	b_bytes_valid, x
	mov	[stream + _internal_state_b_bytes_valid], b_bytes_valid %+ d

	; f_end_i   = state->b_bytes_valid - LA;
%ifnidn f_end_i, b_bytes_valid
	mov	f_end_i, b_bytes_valid
%endif
	sub	f_end_i, LA
	; if (f_end_i <= 0) continue;
	cmp	f_end_i, 0
	jle	continue_while

	; f_start_i = state->b_bytes_processed;
	;; f_i and b_bytes_processed are same register, just store b_bytes_proc
	mov	[stream + _internal_state_b_bytes_processed], b_bytes_processed %+ d

	; f_start_i += (uint32_t)(state->buffer - state->file_start);
	mov	file_start, [stream + _internal_state_file_start]
	lea	tmp1, [stream + _internal_state_buffer]
	sub	tmp1, file_start
	add	f_i, tmp1
	add	f_end_i, tmp1

	; for (f_i = f_start_i; f_i < f_end_i; f_i++) {
	cmp	f_i, f_end_i
	jge	end_loop_2

	mov	[rsp + f_end_i_mem_offset], f_end_i

MARK __compute_hash
	mov	tmp1 %+ d, [file_start + f_i]
loop2:
	cmp dword [stream + _internal_state_no_comp], 1
	je bitbuf_full
	mov tmp4, f_i
	and tmp4, 0x000000000000FFFF
	; if (state->bitbuf.is_full()) {
	cmp	m_out_buf, [stream + _internal_state_bitbuf_m_out_end]
	ja	bitbuf_full

	; hash = compute_hash(state->file_start + f_i) & HASH_MASK;
	; update_crc(state->crc, *(state->file_start + f_i));
	mov	crc %+ d, [stream + _internal_state_crc]
	compute_hash	hash, tmp1, tmp2
MARK __compute_crc
	update_crc	crc, tmp1, CrcTable, tmp5	; tmp1 is now clobbered
MARK __misc_compute_hash_lookup
	and	hash %+ d, HASH_MASK
	mov	[stream + _internal_state_crc], crc %+ d

	; f_indices = state->head[hash];
	mov	f_indices, [stream + _internal_state_head + 8 * hash]

	; state->head[hash] = (f_indices << 16) | (f_i & 0xFFFF);
	mov	tmp1, f_indices
	shl	tmp1, 16
	or	tmp1, tmp4
	mov	[stream + _internal_state_head + 8 * hash], tmp1

	xor	len, len

	; tmp5 = (f_i + 0x8000) << (64-16)
	mov	tmp5, f_i
	add	tmp5, 0x8000
	shl	tmp5, (64-16)

	; logical start of index_loop
	; tmp_dist = (uint16_t)(f_i - f_indices); // mod 64k
	mov	tmp_dist %+ d, f_i %+ d
	sub	tmp_dist %+ d, f_indices %+ d
	and	tmp_dist %+ d, 0xFFFF

	; if ((tmp_dist-1) <= (D-1)) {
	mov	tmp1 %+ d, tmp_dist %+ d
	sub	tmp1 %+ d, 1
	cmp	tmp1 %+ d, (D-1)
	ja	end_index_loop

	; f_indices >>= 16;
	; f_indices |= ((uint64_t)(f_i + 0x8000)) << (64-16);
	shr	f_indices, 16
	or	f_indices, tmp5	
index_loop:

MARK __compare
	; tmp_len = compare258(state->file_start + f_i,
	;                      state->file_start + f_i - tmp_dist);
	lea	tmp1, [file_start + f_i]
	mov	tmp2, tmp1
	sub	tmp2, tmp_dist	;; tmp2 := tmp1 - tmp_dist
	
%if (COMPARE_TYPE == 1)
	%error not yet implemented
	 % error
	compare258	tmp1, tmp2, tmp_len, tmp3
%elif (COMPARE_TYPE == 2)
	compare258_2	tmp1, tmp2, tmp_len, tmp3, tmp_c1, tmp_c2
%elif (COMPARE_TYPE == 3)
	%error not yet implemented
	 % error
	compare258_s	tmp1, tmp2, tmp_len, xtmp0
%elif (COMPARE_TYPE == 4)
	%error not yet implemented
	 % error
	compare258_x	tmp1, tmp2, tmp_len, tmp3, xtmp0, xtmp1
%else
	%error Unknown Compare type COMPARE_TYPE
	 % error
%endif

	sub tmp1, tmp2		;; tmp1 := tmp1 - (tmp1 - tmp_dist) = tmp_dist
	; if (tmp_len > len)
	;   len = tmp_len;
	;   dist = tmp_dist;
	cmp	tmp_len, len
	cmova	len, tmp_len
	cmova	dist, tmp1	;; tmp1 = tmp_dist

	;; duplicate start of loop above
	; tmp_dist = (uint16_t)(f_i - f_indices); // mod 64k
	mov	tmp_dist %+ d, f_i %+ d
	sub	tmp_dist %+ d, f_indices %+ d
	and	tmp_dist %+ d, 0xFFFF
	
	; f_indices >>= 16;
	shr	f_indices, 16

	; if ((tmp_dist-1) > (D-1)) {
	mov	tmp1 %+ d, tmp_dist %+ d
	sub	tmp1 %+ d, 1
	cmp	tmp1 %+ d, (D-1)
%ifndef LONGER_HUFFTABLE
	jb	index_loop
%else
	jbe	index_loop
%endif

end_index_loop:

	; if (len >= SHORTEST_MATCH) {
	cmp	len, SHORTEST_MATCH
	jb	encode_literal

	;; encode as dist/len

MARK __len_dist_huffman
	; get_dist_code(dist, &code2, &code_len2);
	get_dist_code	dist, code2, code_len2 ;; clobbers dist, rcx

	; get_len_code(len, &code, &code_len);
	get_len_code	len, code, rcx		;; rcx is code_len

	; code2 <<= code_len
	; code2 |= code
	; code_len2 += code_len
%ifdef USE_HSWNI
	shlx	code2, code2, rcx
%else
	shl	code2, cl
%endif
	or	code2, code
	add	code_len2, rcx

	mov	crc %+ d, [stream + _internal_state_crc]
MARK __update_hash_for_symbol
	; for (k = f_i+1, f_i += len-1; k <= f_i; k++) {
	lea	tmp3, [f_i + 1]	; tmp3 <= k
	add	f_i, len
%ifdef LIMIT_HASH_UPDATE
%error not yet implemented
 % error
	; only update hash twice

	; hash = compute_hash(state->file_start + k) & HASH_MASK;
	; update_crc(state->crc, *(state->file_start + k));
	mov	tmp1 %+ d, [file_start + tmp3]
	compute_hash	hash, tmp1, tmp2
	update_crc	crc, tmp1, CrcTable, tmp4	; tmp1 is now clobbered
	and	hash %+ d, HASH_MASK
	; state->head[hash] = k;
	mov	[stream + _internal_state_head + 2 * hash], tmp3 %+ w

	add	tmp3,1

	; hash = compute_hash(state->file_start + k) & HASH_MASK;
	; update_crc(state->crc, *(state->file_start + k));
	mov	tmp1 %+ d, [file_start + tmp3]
	compute_hash	hash, tmp1, tmp2
	update_crc	crc, tmp1, CrcTable, tmp4	; tmp1 is now clobbered
	and	hash %+ d, HASH_MASK
	; state->head[hash] = k;
	mov	[stream + _internal_state_head + 2 * hash], tmp3 %+ w

	add	tmp3,1

	cmp	tmp3, f_i
	jge	skip_loop3
loop3:
	; hash = compute_hash(state->file_start + k) & HASH_MASK;
	; update_crc(state->crc, *(state->file_start + k));
	mov	tmp1 %+ d, [file_start + tmp3]
	update_crc	crc, tmp1, CrcTable, tmp4	; tmp1 is now clobbered
	; state->head[hash] = k;
	add	tmp3,1
	cmp	tmp3, f_i
	jl	loop3

%else

loop3:
	; hash = compute_hash(state->file_start + k) & HASH_MASK;
	; update_crc(state->crc, *(state->file_start + k));
	mov	tmp1 %+ d, [file_start + tmp3]
	compute_hash	hash, tmp1, tmp2
	update_crc	crc, tmp1, CrcTable, tmp4	; tmp1 is now clobbered

	mov tmp4, tmp3
	and tmp4, 0x000000000000FFFF
	and	hash %+ d, HASH_MASK
	; state->head[hash] = (state->head[hash] << 16) | (k & 0xFFFF);
	mov	tmp1, [stream + _internal_state_head + 8 * hash]
	shl	tmp1, 16
	or	tmp1 , tmp4
	mov	[stream + _internal_state_head + 8 * hash], tmp1

	add	tmp3,1
	cmp	tmp3, f_i
	jl	loop3
%endif

skip_loop3:
	
	mov	tmp1 %+ d, [file_start + f_i]

%ifdef USE_BITBUF8
	write_bits_safe	m_bits, m_bit_count, code2, code_len2, m_out_buf, tmp5
%elifdef USE_BITBUFB
	write_bits_always m_bits, m_bit_count, code2, code_len2, m_out_buf, tmp5
%else
	; state->bitbuf.check_space(code_len2);
	check_space	code_len2, m_bits, m_bit_count, m_out_buf, tmp5
	; state->bitbuf.write_bits(code2, code_len2);
	write_bits	m_bits, m_bit_count, code2, code_len2
	; code2 is clobbered, rcx is clobbered
%endif
	mov	[stream + _internal_state_crc], crc %+ d

	; continue
	cmp	f_i, [rsp + f_end_i_mem_offset]
	jl	loop2
	jmp	end_loop_2

MARK __literal_huffman
encode_literal:
	mov	tmp1 %+ d, [file_start + f_i + 1]
	
	; get_lit_code(state->file_start[f_i], &code2, &code_len2);
	movzx	tmp5, byte [file_start + f_i]
	get_lit_code	tmp5, code2, code_len2

	add	f_i,1

%ifdef USE_BITBUF8
	write_bits_safe	m_bits, m_bit_count, code2, code_len2, m_out_buf, tmp5
%elifdef USE_BITBUFB
	write_bits_always m_bits, m_bit_count, code2, code_len2, m_out_buf, tmp5
%else
	; state->bitbuf.check_space(code_len2);
	check_space	code_len2, m_bits, m_bit_count, m_out_buf, tmp5
	; state->bitbuf.write_bits(code2, code_len2);
	write_bits	m_bits, m_bit_count, code2, code_len2
	; code2 is clobbered, rcx is clobbered
%endif

	; continue
	cmp	f_i, [rsp + f_end_i_mem_offset]
	jl	loop2

MARK __end_loops
end_loop_2:

	; state->b_bytes_processed = f_i - (state->buffer - state->file_start);
	add	f_i, [stream + _internal_state_file_start]
	sub	f_i, stream
	sub	f_i, _internal_state_buffer
	mov	[stream + _internal_state_b_bytes_processed], f_i %+ d

	; continue
continue_while:
	mov	blen, [rsp + blen_mem_offset]
	mov	in_buf, [rsp + in_buf_mem_offset]
	cmp	blen, 0
	jnz	loop1

end:
	;; update input buffer
	; stream->total_in += (uint32_t)(in_buf - stream->next_in); // bytes copied
	mov	tmp1 %+ d, [stream + _total_in]
	add	tmp1, in_buf
	sub	tmp1, [stream + _next_in]
	mov	[stream + _total_in], tmp1

	mov	[stream + _next_in], in_buf
	mov	[stream + _avail_in], blen %+ d

	mov	[stream + _next_out], m_out_buf
	; offset = state->bitbuf.buffer_used();
	sub	m_out_buf, [stream + _internal_state_bitbuf_m_out_start]
	sub	[stream + _avail_out], m_out_buf %+ d
	add	[stream + _total_out], m_out_buf %+ d

	mov	[stream + _internal_state_bitbuf_m_bits], m_bits
	mov	[stream + _internal_state_bitbuf_m_bit_count], m_bit_count %+ d

	add	rsp, stack_size

	POP_ALL
	ret

MARK __bitbuf_full
bitbuf_full:
	mov	blen, [rsp + blen_mem_offset]
	mov	in_buf, [rsp + in_buf_mem_offset]
	; state->b_bytes_processed = f_i - (state->buffer - state->file_start);
	add	f_i, [stream + _internal_state_file_start]
	sub	f_i, stream
	sub	f_i, _internal_state_buffer
	mov	[stream + _internal_state_b_bytes_processed], f_i %+ d
	jmp	end

	define_crc_functions

section .data
	Crc_table

	align 4
const_D: dq	D


%else
; Place marker in library to avoid linker warning
%ifidn __OUTPUT_FORMAT__, win64
global __IGZIP1_BODY_DISABLED
__IGZIP1_BODY_DISABLED:
%endif

%endif ;; if (MAJOR_VERSION == IGZIP1)

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

%include "job_sha1.asm"
%include "mb_mgr_sha1_datastruct.asm"

%include "reg_sizes.asm"

extern sha1_mult_avx

%if 1
%ifidn __OUTPUT_FORMAT__, win64
; WINDOWS register definitions
%define arg1    rcx
%define arg2    rdx

; idx needs to be other than ARG2, rax, r8-r11
%define last_len        rsi
%define idx             rsi
                        
%define size_offset     rdi
%define tmp2            rdi

%else
; UN*X register definitions
%define arg1    rdi
%define arg2    rsi

; idx needs to be other than ARG2, rax, r8-r11
%define last_len        rdx
%define idx             rdx
                        
%define size_offset     rcx
%define tmp2            rcx

%endif

; Common definitions
%define state   arg1
%define job     arg2
%define len2    arg2
%define p2      arg2

%define p               r11
%define start_offset    r11

; unused_lanes must be in rax-rdx
%define unused_lanes    rbx
                        
%define job_rax         rax
%define len             rax

%define lane            rbp
%define tmp3            rbp
%define lens3           rbp
                        
%define extra_blocks    r8
%define lens0           r8
%define tmp4            r8

%define tmp             r9
%define lens1           r9
                        
%define lane_data       r10
%define lens2           r10

%endif ; if 1

; STACK_SPACE needs to be an odd multiple of 8
%define STACK_SPACE     8*4 + 16*10 + 8


; JOB_SHA1* sha1_submit_job_avx(SHA1_MB_MGR *state, JOB_SHA1 *job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global sha1_submit_job_avx:function
sha1_submit_job_avx:

%ifdef ALIGN_STACK
        push    rbp
        mov     rbp, rsp
        sub     rsp, STACK_SPACE
        and     rsp, ~15
%else
        sub     rsp, STACK_SPACE
%endif

        mov     [rsp + 8*0], rbx
        mov     [rsp + 8*3], rbp

%ifidn __OUTPUT_FORMAT__, win64
        mov     [rsp + 8*1], rsi
        mov     [rsp + 8*2], rdi
        vmovdqa  [rsp + 8*4 + 16*0], xmm6
        vmovdqa  [rsp + 8*4 + 16*1], xmm7
        vmovdqa  [rsp + 8*4 + 16*2], xmm8
        vmovdqa  [rsp + 8*4 + 16*3], xmm9
        vmovdqa  [rsp + 8*4 + 16*4], xmm10
        vmovdqa  [rsp + 8*4 + 16*5], xmm11
        vmovdqa  [rsp + 8*4 + 16*6], xmm12
        vmovdqa  [rsp + 8*4 + 16*7], xmm13
        vmovdqa  [rsp + 8*4 + 16*8], xmm14
        vmovdqa  [rsp + 8*4 + 16*9], xmm15
%endif

        mov     unused_lanes, [state + _unused_lanes]
        movzx   lane, BYTE(unused_lanes)
        shr     unused_lanes, 8
        imul    lane_data, lane, _LANE_DATA_size
        mov     dword [job + _status], STS_BEING_PROCESSED
        lea     lane_data, [state + _ldata + lane_data]
        mov     [state + _unused_lanes], unused_lanes
        mov     DWORD(len), [job + _len]
        mov     tmp, len
        shr     tmp, 6  ; divide by 64, len in terms of blocks

        mov     [lane_data + _job_in_lane], job
        mov     [state + _lens + 4 + 8*lane], DWORD(tmp)

        mov     last_len, len
        and     last_len, 63

        ;; If first flag set, init with H0-H4, else copy digest
        mov     DWORD(tmp), [job + _flags]
        test    tmp, HASH_MB_FIRST
        lea     tmp4, [H0 wrt rip]
        lea     tmp2, [job + _result_digest]
        cmovz   tmp4, tmp2

        vmovdqa xmm0, [tmp4]
        mov     DWORD(tmp4),  [tmp4 + 1*16]

        vmovd   [state + _args_digest + 4*lane + 0*16], xmm0
        vpextrd [state + _args_digest + 4*lane + 1*16], xmm0, 1
        vpextrd [state + _args_digest + 4*lane + 2*16], xmm0, 2
        vpextrd [state + _args_digest + 4*lane + 3*16], xmm0, 3
        mov     [state + _args_digest + 4*lane + 4*16], DWORD(tmp4)

        ;; if last flag set, use extra_blocks to finalize
        test    tmp, HASH_MB_LAST
        jz      skip_finalize

do_finalize:
        lea     extra_blocks, [last_len + 9 + 63]
        shr     extra_blocks, 6
        mov     [lane_data + _extra_blocks], DWORD(extra_blocks)

        mov     size_offset, extra_blocks
        shl     size_offset, 6
        sub     size_offset, last_len
        add     size_offset, 64-8
        mov     [lane_data + _size_offset], DWORD(size_offset)
        mov     start_offset, 64
        sub     start_offset, last_len
        mov     [lane_data + _start_offset], DWORD(start_offset)

        mov     DWORD(tmp), [job + _len_total]
        shl     tmp, 3
        bswap   tmp
        mov     [lane_data + _extra_block + size_offset], tmp

        test    len, ~63
        jz      copy_lt64_bytes
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + 8*lane], p

fast_copy:
        vmovdqu  xmm0, [p + len - 64 + 0*16]
        vmovdqu  xmm1, [p + len - 64 + 1*16]
        vmovdqu  xmm2, [p + len - 64 + 2*16]
        vmovdqu  xmm3, [p + len - 64 + 3*16]
        vmovdqa  [lane_data + _extra_block + 0*16], xmm0
        vmovdqa  [lane_data + _extra_block + 1*16], xmm1
        vmovdqa  [lane_data + _extra_block + 2*16], xmm2
        vmovdqa  [lane_data + _extra_block + 3*16], xmm3
end_fast_copy:

        cmp     unused_lanes, 0xff
        jne     return_null

start_loop:

        ; Find min length
        mov     lens0, [state + _lens + 0*8]
        mov     idx, lens0
        mov     lens1, [state + _lens + 1*8]
        cmp     lens1, idx
        cmovb   idx, lens1
        mov     lens2, [state + _lens + 2*8]
        cmp     lens2, idx
        cmovb   idx, lens2
        mov     lens3, [state + _lens + 3*8]
        cmp     lens3, idx
        cmovb   idx, lens3
        mov     len2, idx
        and     idx, 0xF
        and     len2, ~0xFF
        jz      len_is_0        

        sub     lens0, len2
        sub     lens1, len2
        sub     lens2, len2
        sub     lens3, len2
        shr     len2, 32
        mov     [state + _lens + 0*8], lens0
        mov     [state + _lens + 1*8], lens1
        mov     [state + _lens + 2*8], lens2
        mov     [state + _lens + 3*8], lens3

        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    sha1_mult_avx
        ; state and idx are intact


len_is_0:
        ; process completed job "idx"
        imul    lane_data, idx, _LANE_DATA_size
        lea     lane_data, [state + _ldata + lane_data]
        mov     DWORD(extra_blocks), [lane_data + _extra_blocks]
        cmp     extra_blocks, 0
        je      end_loop

proc_extra_blocks:
        mov     DWORD(start_offset), [lane_data + _start_offset]
        mov     [state + _lens + 4 + 8*idx], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     [state + _args_data_ptr + 8*idx], tmp
        mov     dword [lane_data + _extra_blocks], 0
        jmp     start_loop

skip_finalize:
        cmp     last_len, 0
        jnz     return_error_job
        mov     extra_blocks, 0
        mov     p, [job + _buffer]
        mov     [lane_data + _extra_blocks], DWORD(extra_blocks)
        mov     [lane_data + _size_offset], DWORD(extra_blocks)
        mov     [state + _args_data_ptr + 8*lane], p
        jmp     end_fast_copy

return_error_job:
        mov     job_rax, job
        mov     dword [job + _status], STS_ERROR
        jmp     return


        align   16
copy_lt64_bytes:
        mov     [state + _lens + 4 + 8*lane], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + 8*lane], tmp
        mov     dword [lane_data + _extra_blocks], 0

        ; copy last_len bytes from p to (_extra_block + 64 - last_len)
        lea     p2, [lane_data + _extra_block + 64]
        sub     p2, last_len

        test    last_len, 32
        jz      lt32
        vmovdqu  xmm0, [p + 0*16]
        vmovdqu  xmm1, [p + 1*16]
        vmovdqu  xmm2, [p + last_len - 2*16]
        vmovdqu  xmm3, [p + last_len - 1*16]
        vmovdqu  [p2 + 0*16], xmm0
        vmovdqu  [p2 + 1*16], xmm1
        vmovdqa  [lane_data + _extra_block + 64 - 2*16], xmm2
        vmovdqa  [lane_data + _extra_block + 64 - 1*16], xmm3
        jmp     end_fast_copy

lt32:
        test    last_len, 16
        jz      lt16
        vmovdqu  xmm0, [p + 0*16]
        vmovdqu  xmm1, [p + last_len - 1*16]
        vmovdqu  [p2 + 0*16], xmm0
        vmovdqa  [lane_data + _extra_block + 64 - 1*16], xmm1
        jmp     end_fast_copy

lt16:
        test    last_len, 8
        jz      lt8
        mov     tmp, [p]
        mov     tmp2, [p + last_len - 8]
        mov     [p2], tmp
        mov     [lane_data + _extra_block + 64 - 8], tmp2
        jmp     end_fast_copy

lt8:
        test    last_len, 4
        jz      lt4
        mov     DWORD(tmp), [p]
        mov     DWORD(tmp2), [p + last_len - 4]
        mov     [p2], DWORD(tmp)
        mov     [lane_data + _extra_block + 64 - 4], DWORD(tmp2)
        jmp     end_fast_copy

lt4:
        test    last_len, 2
        jz      lt2
        mov     WORD(tmp), [p]
        mov     BYTE(tmp2), [p + last_len - 1]
        mov     [p2], WORD(tmp)
        mov     [lane_data + _extra_block + 64 - 1], BYTE(tmp2)
        jmp     end_fast_copy

lt2:
        test    last_len, 1
        jz      end_fast_copy
        mov     BYTE(tmp), [p]
        mov     [p2], BYTE(tmp)
        jmp     end_fast_copy

return_null:
        xor     job_rax, job_rax
        jmp     return
        
        align   16
end_loop:
        ; zero size in extra block buffer
        mov     DWORD(size_offset), [lane_data + _size_offset]
        mov     qword [lane_data + _extra_block + size_offset], 0

        mov     job_rax, [lane_data + _job_in_lane]
        mov     unused_lanes, [state + _unused_lanes]
        mov     qword [lane_data + _job_in_lane], 0
        mov     dword [job_rax + _status], STS_COMPLETED
        shl     unused_lanes, 8
        or      unused_lanes, idx
        mov     [state + _unused_lanes], unused_lanes

        vmovd    xmm0, [state + _args_digest + 4*idx + 0*16]
        vpinsrd  xmm0, [state + _args_digest + 4*idx + 1*16], 1
        vpinsrd  xmm0, [state + _args_digest + 4*idx + 2*16], 2
        vpinsrd  xmm0, [state + _args_digest + 4*idx + 3*16], 3
        mov     DWORD(tmp),  [state + _args_digest + 4*idx + 4*16]

        vmovdqa  [job_rax + _result_digest + 0*16], xmm0
        mov     [job_rax + _result_digest + 1*16], DWORD(tmp)

return:

%ifidn __OUTPUT_FORMAT__, win64
        vmovdqa  xmm6, [rsp + 8*4 + 16*0]
        vmovdqa  xmm7, [rsp + 8*4 + 16*1]
        vmovdqa  xmm8, [rsp + 8*4 + 16*2]
        vmovdqa  xmm9, [rsp + 8*4 + 16*3]
        vmovdqa  xmm10, [rsp + 8*4 + 16*4]
        vmovdqa  xmm11, [rsp + 8*4 + 16*5]
        vmovdqa  xmm12, [rsp + 8*4 + 16*6]
        vmovdqa  xmm13, [rsp + 8*4 + 16*7]
        vmovdqa  xmm14, [rsp + 8*4 + 16*8]
        vmovdqa  xmm15, [rsp + 8*4 + 16*9]
        mov     rsi, [rsp + 8*1]
        mov     rdi, [rsp + 8*2]
%endif
        mov     rbx, [rsp + 8*0]
        mov     rbp, [rsp + 8*3]

%ifndef ALIGN_STACK
        add     rsp, STACK_SPACE
%else
        mov     rsp, rbp
        pop     rbp
%endif

        ret

section .data align=16

align 16
H0:     dd  0x67452301
H1:     dd  0xefcdab89
H2:     dd  0x98badcfe
H3:     dd  0x10325476
H4:     dd  0xc3d2e1f0

%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro
;;;       func                 core, ver, snum
slversion sha1_submit_job_avx, 02,   07,  0092

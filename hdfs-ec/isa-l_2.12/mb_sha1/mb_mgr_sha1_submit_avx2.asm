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
%include "memcpy.asm"
%include "mb_mgr_sha1_datastruct_x8.asm"
%include "reg_sizes.asm"

extern sha1_mult_avx2

%ifidn __OUTPUT_FORMAT__, elf64
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; UN*X register definitions
%define arg1    rdi ; rcx
%define arg2    rsi ; rdx

%define size_offset     rcx ; rdi
%define tmp2            rcx ; rdi

%define extra_blocks    rdx
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

%else

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; WINDOWS register definitions
%define arg1    rcx
%define arg2    rdx

%define size_offset     rdi
%define tmp2            rdi

%define extra_blocks    rsi
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
%endif

; Common register definitions

%define state   arg1
%define job     arg2
%define len2    arg2
%define p2      arg2

; idx must be a register not clobberred by sha1_mult
%define idx             r8
%define last_len        r8
                        
%define p               r11
%define start_offset    r11

%define unused_lanes    rbx
                        
%define job_rax         rax
%define len             rax
                        
%define lane            rbp
%define tmp3            rbp
                        
%define tmp             r9
                        
%define lane_data       r10


; STACK_SPACE needs to be an odd multiple of 8
%define STACK_SPACE     8*8 + 16*10 + 8

; JOB_SHA1 * sha1_submit_job_avx2(SHA1_MB_MGR_X8 *state, JOB_SHA1 *job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global sha1_submit_job_avx2:function
sha1_submit_job_avx2:
        sub     rsp, STACK_SPACE
        mov     [rsp + 8*0], rbx
        mov     [rsp + 8*3], rbp
        mov     [rsp + 8*4], r12
        mov     [rsp + 8*5], r13
        mov     [rsp + 8*6], r14
        mov     [rsp + 8*7], r15
%ifidn __OUTPUT_FORMAT__, win64
        mov     [rsp + 8*1], rsi
        mov     [rsp + 8*2], rdi
        vmovdqa  [rsp + 8*8 + 16*0], xmm6
        vmovdqa  [rsp + 8*8 + 16*1], xmm7
        vmovdqa  [rsp + 8*8 + 16*2], xmm8
        vmovdqa  [rsp + 8*8 + 16*3], xmm9
        vmovdqa  [rsp + 8*8 + 16*4], xmm10
        vmovdqa  [rsp + 8*8 + 16*5], xmm11
        vmovdqa  [rsp + 8*8 + 16*6], xmm12
        vmovdqa  [rsp + 8*8 + 16*7], xmm13
        vmovdqa  [rsp + 8*8 + 16*8], xmm14
        vmovdqa  [rsp + 8*8 + 16*9], xmm15
%endif

        mov     unused_lanes, [state + _unused_lanes]
        mov     lane, unused_lanes
        and     lane, 0xF
        shr     unused_lanes, 4

        imul    lane_data, lane, _LANE_DATA_size
        mov     dword [job + _status], STS_BEING_PROCESSED
        lea     lane_data, [state + _ldata + lane_data]
        mov     [state + _unused_lanes], unused_lanes
        mov     DWORD(len), [job + _len]
        mov     tmp, len
        shr     tmp, 6-4  ; divide by 64, len in terms of blocks, shl nibble

        mov     [lane_data + _job_in_lane], job
        and     tmp, ~0xF
        or      tmp, lane
        mov     [state + _lens + 4*lane], DWORD(tmp)

        mov     last_len, len
        and     last_len, 63
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


        lea     tmp, [8*len]
        bswap   tmp
        mov     [lane_data + _extra_block + size_offset], tmp

        vmovdqa xmm0, [H0 wrt rip]
        mov     DWORD(tmp),  [H4 wrt rip]
        vmovd   [state + _args_digest + 4*lane + 0*4*8], xmm0
        vpextrd [state + _args_digest + 4*lane + 1*4*8], xmm0, 1
        vpextrd [state + _args_digest + 4*lane + 2*4*8], xmm0, 2
        vpextrd [state + _args_digest + 4*lane + 3*4*8], xmm0, 3
        mov     [state + _args_digest + 4*lane + 4*4*8], DWORD(tmp)

        test    len, ~63
        jz      copy_lt64_bytes

        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + 8*lane], p

fast_copy:
        vmovdqu ymm0, [p + len - 64 + 0*32]
        vmovdqu ymm1, [p + len - 64 + 1*32]
        vmovdqu [lane_data + _extra_block + 0*32], ymm0
        vmovdqu [lane_data + _extra_block + 1*32], ymm1
end_fast_copy:

        cmp     unused_lanes, 0xf
        jne     return_null

start_loop:
        ; Find min length
        vmovdqa xmm0, [state + _lens + 0*16]
        vmovdqa xmm1, [state + _lens + 1*16]
        
        vpminud xmm2, xmm0, xmm1        ; xmm2 has {D,C,B,A}
        vpalignr xmm3, xmm3, xmm2, 8    ; xmm3 has {x,x,D,C}
        vpminud xmm2, xmm2, xmm3        ; xmm2 has {x,x,E,F}
        vpalignr xmm3, xmm3, xmm2, 4    ; xmm3 has {x,x,x,E}
        vpminud xmm2, xmm2, xmm3        ; xmm2 has min value in low dword
        
        vmovd   DWORD(idx), xmm2
        mov     len2, idx
        and     idx, 0xF
        shr     len2, 4
        jz      len_is_0
       
        vpand   xmm2, xmm2, [rel clear_low_nibble]
        vpshufd xmm2, xmm2, 0

        vpsubd  xmm0, xmm0, xmm2
        vpsubd  xmm1, xmm1, xmm2

        vmovdqa [state + _lens + 0*16], xmm0
        vmovdqa [state + _lens + 1*16], xmm1


        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    sha1_mult_avx2
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
        shl     extra_blocks, 4
        or      extra_blocks, idx
        mov     [state + _lens + 4*idx], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     [state + _args_data_ptr + 8*idx], tmp
        mov     dword [lane_data + _extra_blocks], 0
        jmp     start_loop

        align   16
copy_lt64_bytes:
        shl     extra_blocks, 4
        or      extra_blocks, lane
        mov     [state + _lens + 4*lane], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + 8*lane], tmp
        mov     dword [lane_data + _extra_blocks], 0
        ; copy last_len bytes from p to (_extra_block + 64 - last_len)
        lea     p2, [lane_data + _extra_block + 64]
        sub     p2, last_len

        add     p, len
        sub     p, last_len
        memcpy_avx2_64 p2, p, last_len, r13, r14, ymm0, ymm1
        jmp     end_fast_copy

return_null:
        xor     job_rax, job_rax
        jmp     return
        
        align   16
end_loop:
        ; Zero the size at the size_offset location of the extra_block buffer
        mov     DWORD(size_offset), [lane_data + _size_offset]
        mov     qword [lane_data + _extra_block + size_offset], 0

        mov     job_rax, [lane_data + _job_in_lane]
        mov     unused_lanes, [state + _unused_lanes]
        mov     qword [lane_data + _job_in_lane], 0
        mov     dword [job_rax + _status], STS_COMPLETED
        shl     unused_lanes, 4
        or      unused_lanes, idx
        mov     [state + _unused_lanes], unused_lanes

        vmovd   xmm0, [state + _args_digest + 4*idx + 0*4*8]
        vpinsrd xmm0, [state + _args_digest + 4*idx + 1*4*8], 1
        vpinsrd xmm0, [state + _args_digest + 4*idx + 2*4*8], 2
        vpinsrd xmm0, [state + _args_digest + 4*idx + 3*4*8], 3
        mov     DWORD(tmp),  [state + _args_digest + 4*idx + 4*4*8]

        vmovdqa [job_rax + _result_digest + 0*16], xmm0
        mov     [job_rax + _result_digest + 1*16], DWORD(tmp)

return:

%ifidn __OUTPUT_FORMAT__, win64
        vmovdqa  xmm6, [rsp + 8*8 + 16*0]
        vmovdqa  xmm7, [rsp + 8*8 + 16*1]
        vmovdqa  xmm8, [rsp + 8*8 + 16*2]
        vmovdqa  xmm9, [rsp + 8*8 + 16*3]
        vmovdqa  xmm10, [rsp + 8*8 + 16*4]
        vmovdqa  xmm11, [rsp + 8*8 + 16*5]
        vmovdqa  xmm12, [rsp + 8*8 + 16*6]
        vmovdqa  xmm13, [rsp + 8*8 + 16*7]
        vmovdqa  xmm14, [rsp + 8*8 + 16*8]
        vmovdqa  xmm15, [rsp + 8*8 + 16*9]
        mov     rsi, [rsp + 8*1] 
        mov     rdi, [rsp + 8*2] 
%endif
        mov     rbx, [rsp + 8*0] 
        mov     rbp, [rsp + 8*3] 
        mov     r12, [rsp + 8*4] 
        mov     r13, [rsp + 8*5] 
        mov     r14, [rsp + 8*6] 
        mov     r15, [rsp + 8*7] 
        add     rsp, STACK_SPACE

        ret

section .data align=16

align 16
clear_low_nibble:
        ddq 0x000000000000000000000000FFFFFFF0

H0:             dd 0x67452301
H1:             dd 0xefcdab89
H2:             dd 0x98badcfe
H3:             dd 0x10325476
H4:             dd 0xc3d2e1f0

%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
        dw 0x%4
        db 0x%3, 0x%2
%endmacro
;;;       func                 core, ver, snum
slversion sha1_submit_job_avx2, 04,   01,  0102

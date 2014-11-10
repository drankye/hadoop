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

%include "job_md5.asm"
%include "mb_mgr_md5_datastruct.asm"

%include "reg_sizes.asm"

extern md5_x4x2_sse

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

; idx needs to be in r8
%define last_len        r8
%define idx             r8
                        
%define p               r11
%define start_offset    r11

; unused_lanes must be in rax-rdx
%define unused_lanes    rbx
                        
%define job_rax         rax
%define len             rax
                        
%define lane            rbp
%define tmp3            rbp
                        
%define tmp             r9
                        
%define lane_data       r10

; Temporary variable for update
%define tmp4		r12

; STACK_SPACE needs to be an odd multiple of 8
%define STACK_SPACE     8*8 + 16*10 + 8

; JOB_MD5* md5_submit_job(MD5_MB_MGR* state, JOB_MD5* job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global md5_submit_job:function
md5_submit_job:

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
        movdqa  [rsp + 8*8 + 16*0], xmm6
        movdqa  [rsp + 8*8 + 16*1], xmm7
        movdqa  [rsp + 8*8 + 16*2], xmm8
        movdqa  [rsp + 8*8 + 16*3], xmm9
        movdqa  [rsp + 8*8 + 16*4], xmm10
        movdqa  [rsp + 8*8 + 16*5], xmm11
        movdqa  [rsp + 8*8 + 16*6], xmm12
        movdqa  [rsp + 8*8 + 16*7], xmm13
        movdqa  [rsp + 8*8 + 16*8], xmm14
        movdqa  [rsp + 8*8 + 16*9], xmm15
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

	; Update
        ;; If 'first' flag set, init with H0-H3, else copy digest
        mov     DWORD(tmp), [job + _flags]
        test    tmp, HASH_MB_FIRST
        lea     tmp4, [H0 wrt rip]
        lea     tmp2, [job + _result_digest]
        cmovz   tmp4, tmp2

        movdqa  xmm0, [tmp4]

        movd    [state + _args_digest + 4*lane + 0*4*8], xmm0
        pextrd  [state + _args_digest + 4*lane + 1*4*8], xmm0, 1
        pextrd  [state + _args_digest + 4*lane + 2*4*8], xmm0, 2
        pextrd  [state + _args_digest + 4*lane + 3*4*8], xmm0, 3

        ; If 'last' flag set, use extra_blocks to finalize
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
        
        mov     [lane_data + _extra_block + size_offset], tmp

        test    len, ~63
        jz      copy_lt64_bytes

        mov     p, [job + _buffer]

        mov     [state + _args_data_ptr + 8*lane], p

fast_copy:
        movdqu  xmm0, [p + len - 64 + 0*16]
        movdqu  xmm1, [p + len - 64 + 1*16]
        movdqu  xmm2, [p + len - 64 + 2*16]
        movdqu  xmm3, [p + len - 64 + 3*16]
        movdqa  [lane_data + _extra_block + 0*16], xmm0
        movdqa  [lane_data + _extra_block + 1*16], xmm1
        movdqa  [lane_data + _extra_block + 2*16], xmm2
        movdqa  [lane_data + _extra_block + 3*16], xmm3
end_fast_copy:

        cmp     unused_lanes, 0xf
        jne     return_null

start_loop:
        ; Find min length
        movdqa  xmm0, [state + _lens + 0*16]
        movdqa  xmm1, [state + _lens + 1*16]
        movdqa  xmm2, xmm0
        pminud  xmm2, xmm1      ; xmm2 has {D,C,B,A}
        palignr xmm3, xmm2, 8   ; xmm3 has {x,x,D,C}
        pminud  xmm2, xmm3      ; xmm2 has {x,x,E,F}
        palignr xmm3, xmm2, 4   ; xmm3 has {x,x,x,E}
        pminud  xmm2, xmm3      ; xmm2 has min value in low dword
        movd    DWORD(idx), xmm2
        mov     len2, idx
        and     idx, 0xF
        shr     len2, 4
        jz      len_is_0

        pand    xmm2, [rel clear_low_nibble]
        pshufd  xmm2, xmm2, 0
        psubd   xmm0, xmm2
        psubd   xmm1, xmm2
        movdqa  [state + _lens + 0*16], xmm0
        movdqa  [state + _lens + 1*16], xmm1

        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    md5_x4x2_sse
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

; Update - skip finalize
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

        test    last_len, 32
        jz      lt32
        movdqu  xmm0, [p + 0*16]
        movdqu  xmm1, [p + 1*16]
        movdqu  xmm2, [p + last_len - 2*16]
        movdqu  xmm3, [p + last_len - 1*16]
        movdqu  [p2 + 0*16], xmm0
        movdqu  [p2 + 1*16], xmm1
        movdqa  [lane_data + _extra_block + 64 - 2*16], xmm2
        movdqa  [lane_data + _extra_block + 64 - 1*16], xmm3
        jmp     end_fast_copy

lt32:
        test    last_len, 16
        jz      lt16
        movdqu  xmm0, [p + 0*16]
        movdqu  xmm1, [p + last_len - 1*16]
        movdqu  [p2 + 0*16], xmm0
        movdqa  [lane_data + _extra_block + 64 - 1*16], xmm1
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

        movd    xmm0, [state + _args_digest + 4*idx + 0*4*8]
        pinsrd  xmm0, [state + _args_digest + 4*idx + 1*4*8], 1
        pinsrd  xmm0, [state + _args_digest + 4*idx + 2*4*8], 2
        pinsrd  xmm0, [state + _args_digest + 4*idx + 3*4*8], 3

        movdqa  [job_rax + _result_digest + 0*16], xmm0

return:

%ifidn __OUTPUT_FORMAT__, win64
        movdqa  xmm6, [rsp + 8*8 + 16*0]
        movdqa  xmm7, [rsp + 8*8 + 16*1]
        movdqa  xmm8, [rsp + 8*8 + 16*2]
        movdqa  xmm9, [rsp + 8*8 + 16*3]
        movdqa  xmm10, [rsp + 8*8 + 16*4]
        movdqa  xmm11, [rsp + 8*8 + 16*5]
        movdqa  xmm12, [rsp + 8*8 + 16*6]
        movdqa  xmm13, [rsp + 8*8 + 16*7]
        movdqa  xmm14, [rsp + 8*8 + 16*8]
        movdqa  xmm15, [rsp + 8*8 + 16*9]
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
H0:     dd  0x67452301
H1:     dd  0xefcdab89
H2:     dd  0x98badcfe
H3:     dd  0x10325476

%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro
;;;       func            core, ver, snum
slversion md5_submit_job, 00,   06,  0028

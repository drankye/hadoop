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

%include "job_sha512.asm"
%include "mb_mgr_sha512_datastruct.asm"

%include "reg_sizes.asm"

extern sha512_x2_avx


%ifidn __OUTPUT_FORMAT__, elf64
; UN*X register definitions
%define arg1    rdi ; rcx
%define arg2    rsi ; rdx

; idx needs to be other than arg1, arg2, rbx, r12
%define idx             rdx ; rsi
%define last_len        rdx ; rsi
                        
%define size_offset     rcx ; rdi
%define tmp2            rcx ; rdi

%else
; WINDOWS register definitions
%define arg1    rcx
%define arg2    rdx

; idx needs to be other than arg1, arg2, rbx, r12
%define idx             rsi
%define last_len        rsi
                        
%define size_offset     rdi
%define tmp2            rdi
                        
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


; STACK_SPACE needs to be an odd multiple of 8
%define _XMM_SAVE       16*10
%define _GPR_SAVE       8*5
%define STACK_SPACE     _GPR_SAVE + _XMM_SAVE

; JOB_SHA512* sha512_submit_job_avx(SHA512_MB_MGR *state, JOB_SHA512 *job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global sha512_submit_job_avx:function
sha512_submit_job_avx:

        sub     rsp, STACK_SPACE
        mov     [rsp + _XMM_SAVE + 8*0], rbx
        mov     [rsp + _XMM_SAVE + 8*1], rbp
        mov     [rsp + _XMM_SAVE + 8*2], r12
%ifidn __OUTPUT_FORMAT__, win64
        mov     [rsp + _XMM_SAVE + 8*3], rsi
        mov     [rsp + _XMM_SAVE + 8*4], rdi
        vmovdqa  [rsp + 16*0], xmm6
        vmovdqa  [rsp + 16*1], xmm7
        vmovdqa  [rsp + 16*2], xmm8
        vmovdqa  [rsp + 16*3], xmm9
        vmovdqa  [rsp + 16*4], xmm10
        vmovdqa  [rsp + 16*5], xmm11
        vmovdqa  [rsp + 16*6], xmm12
        vmovdqa  [rsp + 16*7], xmm13
        vmovdqa  [rsp + 16*8], xmm14
        vmovdqa  [rsp + 16*9], xmm15
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
        shr     tmp, 7  ; divide by 128, len in terms of blocks

        mov     [lane_data + _job_in_lane], job
        mov     [state + _lens + 4 + 8*lane], DWORD(tmp)

        mov     last_len, len
        and     last_len, 127

        ;Update code
        ;; Check "first" flag and init with H0-H7 if set, else copy digest
        mov     DWORD(tmp), [job + _flags]
        test    tmp, HASH_MB_FIRST
        lea     tmp4, [H0 wrt rip]
        lea     tmp2, [job + _result_digest]
        cmovz   tmp4, tmp2

        vmovdqa  xmm0, [tmp4]
        vmovdqa  xmm1, [tmp4 + 1*16]
        vmovdqa  xmm2, [tmp4 + 2*16]
        vmovdqa  xmm3, [tmp4 + 3*16]

        vmovq    [state + _args_digest + 8*lane + 0*16], xmm0	 ; 8*lane because of 
        vpextrq  [state + _args_digest + 8*lane + 1*16], xmm0, 1 ; 64bit digest words
        vmovq    [state + _args_digest + 8*lane + 2*16], xmm1
        vpextrq  [state + _args_digest + 8*lane + 3*16], xmm1, 1
        vmovq    [state + _args_digest + 8*lane + 4*16], xmm2
        vpextrq  [state + _args_digest + 8*lane + 5*16], xmm2, 1
        vmovq    [state + _args_digest + 8*lane + 6*16], xmm3
        vpextrq  [state + _args_digest + 8*lane + 7*16], xmm3, 1

        ;; if last flag set, use extra_blocks to finalize
        test    tmp, HASH_MB_LAST
        jz      skip_finalize

do_finalize:
        lea     extra_blocks, [last_len + 17 + 127]
        shr     extra_blocks, 7
        mov     [lane_data + _extra_blocks], DWORD(extra_blocks)

        mov     size_offset, extra_blocks
        shl     size_offset, 7
        sub     size_offset, last_len
        add     size_offset, 128-8
        mov     [lane_data + _size_offset], DWORD(size_offset)
        mov     start_offset, 128
        sub     start_offset, last_len
        mov     [lane_data + _start_offset], DWORD(start_offset)

        mov        DWORD(tmp), [job + _len_total]
        shl        tmp, 3

        bswap   tmp
        mov     [lane_data + _extra_block + size_offset], tmp


        test    len, ~127
        jz      copy_lt128_bytes

        mov     p, [job + _buffer]

        mov     [state + _args_data_ptr + 8*lane], p

fast_copy:
        add      p, len
        vmovdqu  xmm0, [p - 128 + 0*16]
        vmovdqu  xmm1, [p - 128 + 1*16]
        vmovdqu  xmm2, [p - 128 + 2*16]
        vmovdqu  xmm3, [p - 128 + 3*16]
        vmovdqa  [lane_data + _extra_block + 0*16], xmm0
        vmovdqa  [lane_data + _extra_block + 1*16], xmm1
        vmovdqa  [lane_data + _extra_block + 2*16], xmm2
        vmovdqa  [lane_data + _extra_block + 3*16], xmm3
        vmovdqu  xmm0, [p - 128 + 4*16]
        vmovdqu  xmm1, [p - 128 + 5*16]
        vmovdqu  xmm2, [p - 128 + 6*16]
        vmovdqu  xmm3, [p - 128 + 7*16]
        vmovdqa  [lane_data + _extra_block + 4*16], xmm0
        vmovdqa  [lane_data + _extra_block + 5*16], xmm1
        vmovdqa  [lane_data + _extra_block + 6*16], xmm2
        vmovdqa  [lane_data + _extra_block + 7*16], xmm3

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
        mov     len2, idx
        and     idx, 0xF
        and     len2, ~0xFF
        jz      len_is_0        

        sub     lens0, len2
        sub     lens1, len2
        shr     len2, 32
        mov     [state + _lens + 0*8], lens0
        mov     [state + _lens + 1*8], lens1

        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    sha512_x2_avx
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

;Update code
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
copy_lt128_bytes:
        mov     [state + _lens + 4 + 8*lane], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + 8*lane], tmp
        mov     dword [lane_data + _extra_blocks], 0

        ; copy last_len bytes from p to (_extra_block + 128 - last_len)
        lea     p2, [lane_data + _extra_block + 128]
        sub     p2, last_len

        test    last_len, 64
        jz      lt64
        vmovdqu  xmm0, [p + 0*16]
        vmovdqu  xmm1, [p + 1*16]
        vmovdqu  xmm2, [p + 2*16]
        vmovdqu  xmm3, [p + 3*16]
        vmovdqu  xmm4, [p + last_len - 4*16]
        vmovdqu  xmm5, [p + last_len - 3*16]
        vmovdqu  xmm6, [p + last_len - 2*16]
        vmovdqu  xmm7, [p + last_len - 1*16]
        vmovdqu  [p2 + 0*16], xmm0
        vmovdqu  [p2 + 1*16], xmm1
        vmovdqu  [p2 + 2*16], xmm2
        vmovdqu  [p2 + 3*16], xmm3
        vmovdqa  [lane_data + _extra_block + 128 - 4*16], xmm4
        vmovdqa  [lane_data + _extra_block + 128 - 3*16], xmm5
        vmovdqa  [lane_data + _extra_block + 128 - 2*16], xmm6
        vmovdqa  [lane_data + _extra_block + 128 - 1*16], xmm7
        jmp     end_fast_copy

lt64:
        test    last_len, 32
        jz      lt32
        vmovdqu  xmm0, [p + 0*16]
        vmovdqu  xmm1, [p + 1*16]
        vmovdqu  xmm2, [p + last_len - 2*16]
        vmovdqu  xmm3, [p + last_len - 1*16]
        vmovdqu  [p2 + 0*16], xmm0
        vmovdqu  [p2 + 1*16], xmm1
        vmovdqa  [lane_data + _extra_block + 128 - 2*16], xmm2
        vmovdqa  [lane_data + _extra_block + 128 - 1*16], xmm3
        jmp     end_fast_copy

lt32:
        test    last_len, 16
        jz      lt16
        vmovdqu  xmm0, [p + 0*16]
        vmovdqu  xmm1, [p + last_len - 1*16]
        vmovdqu  [p2 + 0*16], xmm0
        vmovdqa  [lane_data + _extra_block + 128 - 1*16], xmm1
        jmp     end_fast_copy

lt16:
        test    last_len, 8
        jz      lt8
        mov     tmp, [p]
        mov     tmp2, [p + last_len - 8]
        mov     [p2], tmp
        mov     [lane_data + _extra_block + 128 - 8], tmp2
        jmp     end_fast_copy

lt8:
        test    last_len, 4
        jz      lt4
        mov     DWORD(tmp), [p]
        mov     DWORD(tmp2), [p + last_len - 4]
        mov     [p2], DWORD(tmp)
        mov     [lane_data + _extra_block + 128 - 4], DWORD(tmp2)
        jmp     end_fast_copy

lt4:
        test    last_len, 2
        jz      lt2
        mov     WORD(tmp), [p]
        mov     BYTE(tmp2), [p + last_len - 1]
        mov     [p2], WORD(tmp)
        mov     [lane_data + _extra_block + 128 - 1], BYTE(tmp2)
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

        vmovq    xmm0, [state + _args_digest + 8*idx + 0*16]
        vpinsrq  xmm0, [state + _args_digest + 8*idx + 1*16], 1
        vmovq    xmm1, [state + _args_digest + 8*idx + 2*16]
        vpinsrq  xmm1, [state + _args_digest + 8*idx + 3*16], 1
        vmovq    xmm2, [state + _args_digest + 8*idx + 4*16]
        vpinsrq  xmm2, [state + _args_digest + 8*idx + 5*16], 1
        vmovq    xmm3, [state + _args_digest + 8*idx + 6*16]
        vpinsrq  xmm3, [state + _args_digest + 8*idx + 7*16], 1

        vmovdqa  [job_rax + _result_digest + 0*16], xmm0
        vmovdqa  [job_rax + _result_digest + 1*16], xmm1
        vmovdqa  [job_rax + _result_digest + 2*16], xmm2
        vmovdqa  [job_rax + _result_digest + 3*16], xmm3

return:

%ifidn __OUTPUT_FORMAT__, win64
        vmovdqa  xmm6,  [rsp + 16*0]
        vmovdqa  xmm7,  [rsp + 16*1]
        vmovdqa  xmm8,  [rsp + 16*2]
        vmovdqa  xmm9,  [rsp + 16*3]
        vmovdqa  xmm10, [rsp + 16*4]
        vmovdqa  xmm11, [rsp + 16*5]
        vmovdqa  xmm12, [rsp + 16*6]
        vmovdqa  xmm13, [rsp + 16*7]
        vmovdqa  xmm14, [rsp + 16*8]
        vmovdqa  xmm15, [rsp + 16*9]
        mov     rsi, [rsp + _XMM_SAVE + 8*3]
        mov     rdi, [rsp + _XMM_SAVE + 8*4]
%endif
        mov     rbx, [rsp + _XMM_SAVE + 8*0]
        mov     rbp, [rsp + _XMM_SAVE + 8*1]
        mov     r12, [rsp + _XMM_SAVE + 8*2]
        add     rsp, STACK_SPACE

        ret


section .data
align 16
H0:  dq 0x6a09e667f3bcc908
H1:  dq 0xbb67ae8584caa73b
H2:  dq 0x3c6ef372fe94f82b
H3:  dq 0xa54ff53a5f1d36f1
H4:  dq 0x510e527fade682d1
H5:  dq 0x9b05688c2b3e6c1f
H6:  dq 0x1f83d9abfb41bd6b
H7:  dq 0x5be0cd19137e2179


%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro
;;;       func                   core, ver, snum
slversion sha512_submit_job_avx, 02,   06,  009c

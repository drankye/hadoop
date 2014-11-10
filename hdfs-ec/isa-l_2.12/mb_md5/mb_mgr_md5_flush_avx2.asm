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
%include "mb_mgr_md5_datastruct_x8x2.asm"
%include "reg_sizes.asm"

extern md5_x8x2_avx2

%ifidn __OUTPUT_FORMAT__, elf64
%define arg1    rdi
%define arg2    rsi
%else
%define arg1    rcx
%define arg2    rdx
%endif

%define state   arg1
%define job     arg2
%define len2    arg2


; idx needs to be in rbp
%define idx             rbp

%define unused_lanes    rbx
%define lane_data       rbx
%define tmp2            rbx
                        
%define job_rax         rax
%define tmp1            rax
%define size_offset     rax
%define tmp             rax
%define start_offset    rax
                        
%define tmp3            arg1
                        
%define extra_blocks    arg2
%define p               arg2

%define tmp4                r8
%define num_lanes_inuse     r12
%define len_upper           r13 
%define idx_upper           r14

%define PTR_SZ                  8       ; for 64 bit systems
%define MD5_DIGEST_WORD_SIZE    4       ; 32bit words for MD5
%define MD5_MAX_LANES           16 
%define MD5_DIGEST_ROW_SIZE   MD5_MAX_LANES * MD5_DIGEST_WORD_SIZE

; STACK_SPACE needs to be an odd multiple of 8
%define _XMM_SAVE       16*10
%define _GPR_SAVE       8*8
%define _RSP_SAVE       8*1
%define STACK_SPACE     _GPR_SAVE + _XMM_SAVE + _RSP_SAVE


%define APPEND(a,b) a %+ b

; JOB_MD5* md5_flush_job_avx2(MD5_MB_MGR_X8X2 *state)
; arg 1 : rcx : state
global md5_flush_job_avx2 :function
md5_flush_job_avx2 :

        mov     rax, rsp                        ; Back up original SP
        sub     rsp, STACK_SPACE
        and     rsp, -32                        ;align stack
        mov     [rsp + _XMM_SAVE + 8*0], rbx
        mov     [rsp + _XMM_SAVE + 8*3], rbp
        mov     [rsp + _XMM_SAVE + 8*4], r12
        mov     [rsp + _XMM_SAVE + 8*5], r13
        mov     [rsp + _XMM_SAVE + 8*6], r14
        mov     [rsp + _XMM_SAVE + 8*7], r15
%ifidn __OUTPUT_FORMAT__, win64
        mov     [rsp + _XMM_SAVE + 8*1], rsi
        mov     [rsp + _XMM_SAVE + 8*2], rdi
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
        mov     [rsp + _XMM_SAVE + _GPR_SAVE], rax          ; Back up original SP

        ; Exit function if no jobs to flush
        mov DWORD(num_lanes_inuse), [state + _num_lanes_inuse]  
        cmp     num_lanes_inuse, 0
        jz  return_null

        ; find a lane with a non-null job -- flush does not have to be efficient!
        xor     idx, idx
        %assign I 1
%rep 15 
        cmp     qword [state + _ldata + I * _LANE_DATA_size + _job_in_lane], 0
        cmovne  idx, [APPEND(lane_,I) wrt rip]
%assign I (I+1)
%endrep   

copy_lane_data: 
        ; copy good lane (idx) to empty lanes   
        mov     tmp, [state + _args_data_ptr + PTR_SZ*idx]

        ; Assign FFFF to lanes with no job so they won't be selected in min search
        ; tackle lower 8 lanes
%assign I 0
%rep 8
        cmp     qword [state + _ldata + I * _LANE_DATA_size + _job_in_lane], 0
        jne     APPEND(lower_skip_,I)
        mov     [state + _args_data_ptr + PTR_SZ*I], tmp
        mov     dword [state + _lens + 4*I], 0xFFFFFFFF

APPEND(lower_skip_,I):
%assign I (I+1)
%endrep

        ;; tackle upper lanes
%assign  I 0
%rep 8
        cmp     qword [state + _ldata + (8 + I) * _LANE_DATA_size + _job_in_lane], 0
        jne     APPEND(upper_skip_,I)
        mov     [state + _args_data_ptr + PTR_SZ*(8+I)], tmp
        mov     dword [state + _lens + 4*(8 + I)], 0xFFFFFFFF 
APPEND(upper_skip_,I):
%assign I (I+1)
%endrep

        jmp     start_loop0
        align   32

start_loop0:
        ; This requires 32 byte alignment
        vmovdqa ymm0, [state + _lens + 0*32]    ;keep lengths in  ymm0 and ymm1 for subtraction later 
        vmovdqa ymm1, [state + _lens + 1*32]

        vpminud         ymm2, ymm0, ymm1        ;ymm2 now has the 8 lowest length lanes {H,G,F,E,D,C,B,A}

        vperm2i128      ymm3, ymm2, ymm2, 0x81  ;shift right by 128 bytes -> ymm3 has   {x,x,x,x,H,G,F,E}
        vpminud         ymm3, ymm3, ymm2        ;ymm3 now has the 4 lowest length lanes {x,x,x,x,I,J,K,L}
                                                ;Can switch to xmm now:            xmm3         {x,x,I,J}
        vpalignr        xmm4, xmm4, xmm3, 8     ;xmm4 has {x,x,I,J} (basically dumping original xmm4 values)
        vpminud         xmm3, xmm3, xmm4        ;xmm3 has 2 lowest length lanes {x,x,M,N}

        vpalignr        xmm4, xmm4, xmm3, 4     ;xmm4 now has {x,x,x,M}
        vpminud         xmm6, xmm3, xmm4        ;xmm6 now has min length in low dword

        ;Process the min value to get the length and index
        vpextrd DWORD(len2), xmm6, 0
        mov     idx, len2                       ; Make a copy
        and     idx, 0xF                        ; Mask out length to recover our lane index
        shr     len2, 4                         ; Mask out lane index to get length
        jz      len_is_0

        ; Now need to subtract this min value from all the lengths
        ; Broadcast the minimum into ymm4 and subtract dword-wise from ymm0 and ymm1
        vpand           xmm6, xmm6, [rel clear_low_nibble] 
        vpbroadcastd    ymm4, xmm6

        vpsubd          ymm3, ymm0, ymm4
        vmovdqa         [state + _lens + 0*32], ymm3    ; Update memory with our new lengths
        vpsubd          ymm3, ymm1, ymm4
        vmovdqa         [state + _lens + 1*32], ymm3    

        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    md5_x8x2_avx2
        ; state and idx are intact

        ; Do we have any extra blocks? If not, jump to end_loop
len_is_0:
        ; process completed job "idx"
        imul    lane_data, idx, _LANE_DATA_size
        lea     lane_data, [state + _ldata + lane_data]
        mov     DWORD(extra_blocks), [lane_data + _extra_blocks]
        cmp     extra_blocks, 0
        je      end_loop

        ; If we have extra blocks, put the number (1 or 2) into the length array (taking the place of the job
        ; we've just done) and point the state's arg_data pointer to the extra block (at the start offset)
        align   16
proc_extra_blocks:
        mov     DWORD(start_offset), [lane_data + _start_offset]
        shl     extra_blocks, 4
        or      extra_blocks, idx
        mov     [state + _lens + 4*idx], WORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     [state + _args_data_ptr + PTR_SZ*idx], tmp
        mov     dword [lane_data + _extra_blocks], 0
        jmp     copy_lane_data

return_null:
        xor     job_rax, job_rax
        jmp     return
        
        align   16
end_loop:
        ; Zero the size at the size_offset location of the extra_block buffer
        mov     DWORD(size_offset), [lane_data + _size_offset]
        mov     qword [lane_data + _extra_block + size_offset], 0

        mov     job_rax, [lane_data + _job_in_lane]
        mov     qword [lane_data + _job_in_lane], 0
        mov     dword [job_rax + _status], STS_COMPLETED
        mov     unused_lanes, [state + _unused_lanes]
        shl     unused_lanes, 4
        or      unused_lanes, idx
        mov     [state + _unused_lanes], unused_lanes
        
        mov     DWORD(num_lanes_inuse), [state + _num_lanes_inuse]  ;; update lanes inuse 
        sub     num_lanes_inuse, 1  
        mov     [state + _num_lanes_inuse], DWORD(num_lanes_inuse)

        vmovd   xmm0, [state + _args_digest + MD5_DIGEST_WORD_SIZE*idx + 0*MD5_DIGEST_ROW_SIZE]
        vpinsrd xmm0, [state + _args_digest + MD5_DIGEST_WORD_SIZE*idx + 1*MD5_DIGEST_ROW_SIZE], 1
        vpinsrd xmm0, [state + _args_digest + MD5_DIGEST_WORD_SIZE*idx + 2*MD5_DIGEST_ROW_SIZE], 2
        vpinsrd xmm0, [state + _args_digest + MD5_DIGEST_WORD_SIZE*idx + 3*MD5_DIGEST_ROW_SIZE], 3

        vmovdqa [job_rax + _result_digest], xmm0

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
        mov     rsi, [rsp + _XMM_SAVE + 8*1]
        mov     rdi, [rsp + _XMM_SAVE + 8*2]
%endif
        mov     rbx, [rsp + _XMM_SAVE + 8*0]
        mov     rbp, [rsp + _XMM_SAVE + 8*3]
        mov     r12, [rsp + _XMM_SAVE + 8*4]
        mov     r13, [rsp + _XMM_SAVE + 8*5]
        mov     r14, [rsp + _XMM_SAVE + 8*6]
        mov     r15, [rsp + _XMM_SAVE + 8*7]
        mov     rsp, [rsp + _XMM_SAVE + _GPR_SAVE]  ; original SP

        ret

section .data
align 16

;Value for masking the lane index from the compound "length+index" value
clear_low_nibble:
        ddq 0x000000000000000000000000FFFFFFF0
        
lane_1:     dq  1
lane_2:     dq  2
lane_3:     dq  3
lane_4:     dq  4
lane_5:     dq  5
lane_6:     dq  6
lane_7:     dq  7
lane_8:     dq  8  
lane_9:     dq  9
lane_10:    dq  10
lane_11:    dq  11
lane_12:    dq  12
lane_13:    dq  13
lane_14:    dq  14
lane_15:    dq  15


%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
        dw 0x%4
        db 0x%3, 0x%2
%endmacro
;;;       func                core, ver, snum
slversion md5_flush_job_avx2, 04,  01,  0108

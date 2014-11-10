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
default rel
%include "job_md5.asm"
%include "mb_mgr_md5_datastruct_x8x2.asm"
%include "memcpy.asm"
%include "reg_sizes.asm"

extern md5_x8x2_avx2

%ifidn __OUTPUT_FORMAT__, elf64 ; UN*X register definitions
%define arg1    rdi
%define arg2    rsi
%define reg3    rcx
%define reg4    rdx
%else                           ; WINDOWS register definitions
%define arg1    rcx
%define arg2    rdx
%define reg3    rdi
%define reg4    rsi
%endif

%define state   arg1
%define job     arg2
%define len2    arg2


; idx needs to be in rbp
%define last_len        rbp
%define idx             rbp
                        
%define p               r11
%define start_offset    r11

%define unused_lanes    rbx
%define tmp4            rbx
        
%define job_rax         rax
%define len             rax
                        
%define size_offset     reg3
%define tmp2            reg3
                        
%define lane            reg4
%define tmp3            reg4

%define extra_blocks    r8
                        
%define tmp             r9
%define p2              r9
        
%define lane_data       r10
%define num_lanes_inuse r12
%define len_upper       r13 
%define idx_upper       r14

%define PTR_SZ                  8       ; for 64 bit systems
%define MD5_DIGEST_WORD_SIZE    4       ; 32bit words for MD5
%define MD5_MAX_LANES           16 
%define MD5_DIGEST_ROW_SIZE   MD5_MAX_LANES * MD5_DIGEST_WORD_SIZE

; STACK_SPACE needs to be an odd multiple of 8
%define _XMM_SAVE       16*10
%define _GPR_SAVE       8*8
%define _RSP_SAVE       8*1
%define STACK_SPACE     _GPR_SAVE + _XMM_SAVE + _RSP_SAVE

; JOB_MD5* md5_submit_job_avx2(MD5_MB_MGR_X8X2 *state, JOB_MD5 *job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global md5_submit_job_avx2:function
md5_submit_job_avx2:

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

        ;Copy the mb_mgr structure's unused_lanes variable and get next free lane index
        mov     unused_lanes, [state + _unused_lanes]
        mov     DWORD(num_lanes_inuse), [state + _num_lanes_inuse]
        mov     lane, unused_lanes
        and     lane, 0xF
        shr     unused_lanes, 4

        ;Update the global unused_lanes variable and used lane count
        mov     [state + _unused_lanes], unused_lanes
        add     num_lanes_inuse, 1  
        mov     [state + _num_lanes_inuse], DWORD(num_lanes_inuse)

        ; lane_data is the index of this lane's entry in the ldata variable
        imul    lane_data, lane, _LANE_DATA_size
        mov     dword [job + _status], STS_BEING_PROCESSED
        lea     lane_data, [state + _ldata + lane_data]

        ; Get the current job length in blocks
        mov     DWORD(len), [job + _len]                ; len in number of bytes
        mov     tmp, len
        shr     tmp, 6-4        ; divide by 64, to get len in terms of blocks and shl by 
                                ; one nibble (we stamp the lane index on this nibble)

        ; Copy the job address into the lane's lane_data->JOB_MD5 structure pointer
        mov     [lane_data + _job_in_lane], job

        and     tmp, ~0xF       ; Zero the nibble and stamp the lane index on the length
        or      tmp, lane 

        ;Put the length (#blocks) of our lane's job into the mb_mgr's len array
        mov     [state + _lens + 4*lane], DWORD(tmp)  ;(32bit elements in len array)

        ; Based on the length of the last block, determine if one or two blocks need to be
        ; added for the padding
        mov     last_len, len
        and     last_len, 63                    ; modulo with block length (i.e. consider last block)
        lea     extra_blocks, [last_len + 9 + 63]
        shr     extra_blocks, 6                 ; will be 1 or 2 
        mov     [lane_data + _extra_blocks], DWORD(extra_blocks)

        mov     size_offset, extra_blocks       ; multiply number of extra blocks by size of the block
        shl     size_offset, 6                  ; to get offset in bytes within the 2block array 
        sub     size_offset, last_len
        add     size_offset, 64-8
        mov     [lane_data + _size_offset], DWORD(size_offset)
        mov     start_offset, 64
        sub     start_offset, last_len
        mov     [lane_data + _start_offset], DWORD(start_offset)

        lea     tmp, [8*len]
        mov     [lane_data + _extra_block + size_offset], tmp

        ; Copy in H0-H3 as initial digest for our lane 
        vmovdqa xmm0, [H0 wrt rip]
        vmovd   [state + _args_digest + 4*lane + 0*4*16], xmm0
        vpextrd [state + _args_digest + 4*lane + 1*4*16], xmm0, 1
        vpextrd [state + _args_digest + 4*lane + 2*4*16], xmm0, 2
        vpextrd [state + _args_digest + 4*lane + 3*4*16], xmm0, 3

        ; Check job is greater than one complete block, if not, work on the extra_block
        cmp     len, 64
        jb      copy_lt64_bytes

        ;Point the state's data pointer to the job's buffer
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + PTR_SZ*lane], p

        ; Copy the last block of the  buffer into the lane's extra block array for messages greater than 1 block
fast_copy:
        add     p, len
        vmovdqu ymm0, [p - 64 + 0 * 32]
        vmovdqu ymm1, [p - 64 + 1 * 32]
        vmovdqu [lane_data + _extra_block + 0*32], ymm0
        vmovdqu [lane_data + _extra_block + 1*32], ymm1
end_fast_copy:
        ; Check if we've filled all the lanes (proceed if yes, otherwise exit until all filled)
        cmp     num_lanes_inuse, 0x10  ; all 16 lanes loaded?
        jne     return_null

        align   16
start_loop:
        ; Find min length
        ; This requires 32 byte alignment 
        vmovdqa         ymm0, [state + _lens + 0*32]    ;keep lengths in  ymm0 and ymm1 for subtraction later 
        vmovdqa         ymm1, [state + _lens + 1*32]

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
        mov     idx, len2               ; Make a copy
        and     idx, 0xF                ; Mask out length to recover our lane index
        shr     len2, 4                 ; Mask out lane index to get length
        jz      len_is_0

        ; Now need to subtract this min value from all the lengths
        ; Broadcast the minimum into ymm4 and subtract dword-wise from ymm0 and ymm1
        vpand   xmm6, xmm6, [rel clear_low_nibble]
        vpbroadcastd    ymm4, xmm6

        vpsubd          ymm3, ymm0, ymm4
        vmovdqa [state + _lens + 0*32], ymm3    ; Update memory with our new lengths
        vpsubd          ymm3, ymm1, ymm4
        vmovdqa [state + _lens + 1*32], ymm3    

        ; "state" and "args" are the same address, arg1 (rdi)
        ; len is arg2 (rsi)
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
        shl     extra_blocks, 4                 ; Again imprint the lane index on the length variable
        or      extra_blocks, idx
        mov     [state + _lens + 4*idx], DWORD(extra_blocks) 
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     [state + _args_data_ptr + PTR_SZ*idx], tmp
        mov     dword [lane_data + _extra_blocks], 0
        jmp     start_loop

        align   16

        ; For messages less than one block, the extra blocks become our working buffer
copy_lt64_bytes:
        shl     extra_blocks, 4
        or      extra_blocks, lane
        mov     [state + _lens + 4*lane], DWORD(extra_blocks) 
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + PTR_SZ*lane], tmp
        mov     dword [lane_data + _extra_blocks], 0

        ; destination extrablock but backwards by len from where 0x80 pre-populated 
        ; p2 clobbers unused_lanes, undo before exiting
        lea     p2, [lane_data + _extra_block  + 64]
        sub     p2, len

        ; Point to the last_len bytes of the job's buffer and copy to our extra block
        add     p, len
        sub     p, last_len

        memcpy_avx2_64 p2, p, len, tmp4, tmp2, ymm0, ymm1 
        mov     unused_lanes, [state + _unused_lanes]
        jmp     end_fast_copy

        align   16
        
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
        or      dword [job_rax + _status], STS_COMPLETED
        shl     unused_lanes, 4
        or      unused_lanes, idx
        mov     [state + _unused_lanes], unused_lanes

        mov     DWORD(num_lanes_inuse), [state + _num_lanes_inuse]
        sub     num_lanes_inuse, 1
        mov     [state + _num_lanes_inuse], DWORD(num_lanes_inuse)

         ;Copy result into the job's digest array, 1 XMM sufficient to hold MD5 digest
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

; Initial constants H0-H3
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
;;;       func                core, ver, snum
slversion md5_submit_job_avx2, 04,  01,  0107

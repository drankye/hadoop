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
%include "mb_mgr_sha512_datastruct_x4.asm"
%include "reg_sizes.asm"

extern sha512_x4_avx2

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


; idx needs to be in rbp, r15
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
%define p               arg2    ; temp var for buffer address

%define tmp4            r8

%define PTR_SZ 8
%define SHA512_DIGEST_WORD_SIZE 8
%define SHA512_MAX_LANES         4 
%define SHA512_DIGEST_ROW_SIZE   SHA512_MAX_LANES * SHA512_DIGEST_WORD_SIZE

;Temporary registers for computing minimum length
%define lens0           r8
%define lens1           r9
%define lens2           r10
%define lens3           rax

; STACK_SPACE needs to be an odd multiple of 8
%define _XMM_SAVE       16*10
%define _GPR_SAVE       8*8
%define _RSP_SAVE       8*1
%define STACK_SPACE     _GPR_SAVE + _XMM_SAVE + _RSP_SAVE

%define APPEND(a,b) a %+ b

; JOB_SHA512* sha512_flush_job_avx2(SHA512_MB_MGR_X4 *state)
; arg 1 : rcx : state
global sha512_flush_job_avx2:function
sha512_flush_job_avx2:

        mov     rax, rsp
        sub     rsp, STACK_SPACE
        and     rsp, -32                ; align stack
        mov     [rsp + _XMM_SAVE + 8*0], rbx
        mov     [rsp + _XMM_SAVE + 8*3], rbp
        mov     [rsp + _XMM_SAVE + 8*4], r12
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
        mov     [rsp + _RSP_SAVE], rax  ; original SP

       ; if bit (32+7) is set, then all lanes are empty 
        mov     unused_lanes, [state + _unused_lanes]
        bt      unused_lanes, 32+7
        jc      return_null

        ; find a lane with a non-null job
        xor     idx, idx
%assign I 1
%rep 3
        cmp     qword [state + _ldata + I * _LANE_DATA_size + _job_in_lane], 0
        cmovne  idx, [APPEND(lane_, I) wrt rip]
%assign I (I+1)
%endrep

copy_lane_data: 
        ; copy good lane (idx) to empty lanes
        mov     tmp, [state + _args + _data_ptr + PTR_SZ*idx]

; Assign FFFF to lanes with no job so they won't be selected in min search
%assign I 0
%rep 4
        cmp     qword [state + _ldata + I * _LANE_DATA_size + _job_in_lane], 0
        jne     APPEND(skip_,I)
        mov     [state + _args + _data_ptr + PTR_SZ*I], tmp
        mov     dword [state + _lens + 4*I], 0xFFFFFFFF
APPEND(skip_,I):
%assign I (I+1)
%endrep
    
        ; Find min length
        mov     DWORD(lens0), [state + _lens + 0*4]
        mov     idx, lens0
        mov     DWORD(lens1), [state + _lens + 1*4]
        cmp     lens1, idx
        cmovb   idx, lens1
        mov     DWORD(lens2), [state + _lens + 2*4]
        cmp     lens2, idx
        cmovb   idx, lens2
        mov     DWORD(lens3), [state + _lens + 3*4]
        cmp     lens3, idx
        cmovb   idx, lens3
        mov     len2, idx               ; Make a copy
        and     idx, 0xF                ; Mask out length to recover our lane index
        and     len2, ~0xF              ; Mask out lane index to get length
        jz      len_is_0

        ; Subtract the minimum message length (this amount of each lane's message will be processed
        ; in the call to the hashing function)
        sub     lens0, len2
        sub     lens1, len2
        sub     lens2, len2
        sub     lens3, len2

        shr     len2, 4

        ; Update the length array with the number of remaining blocks in each lane
        mov     [state + _lens + 0*4], DWORD(lens0)
        mov     [state + _lens + 1*4], DWORD(lens1)
        mov     [state + _lens + 2*4], DWORD(lens2)
        mov     [state + _lens + 3*4], DWORD(lens3)


        ; "state" and "args" are the same address, arg1
        ; len is arg2
        call    sha512_x4_avx2
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
        mov     [state + _lens + 4*idx], DWORD(extra_blocks)
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
        or      dword [job_rax + _status], STS_COMPLETED
        mov     unused_lanes, [state + _unused_lanes]
        shl     unused_lanes, 8
        or      unused_lanes, idx
        mov     [state + _unused_lanes], unused_lanes

; Copy the state's digest into the job's digest
        vmovq   xmm0, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 0*SHA512_DIGEST_ROW_SIZE]
        vpinsrq xmm0, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 1*SHA512_DIGEST_ROW_SIZE], 1
        vmovq   xmm1, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 2*SHA512_DIGEST_ROW_SIZE]
        vpinsrq xmm1, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 3*SHA512_DIGEST_ROW_SIZE], 1
        vmovq   xmm2, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 4*SHA512_DIGEST_ROW_SIZE]
        vpinsrq xmm2, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 5*SHA512_DIGEST_ROW_SIZE], 1
        vmovq   xmm3, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 6*SHA512_DIGEST_ROW_SIZE]
        vpinsrq xmm3, [state + _args_digest + SHA512_DIGEST_WORD_SIZE*idx + 7*SHA512_DIGEST_ROW_SIZE], 1

        vmovdqa [job_rax + _result_digest + 0*16], xmm0 
        vmovdqa [job_rax + _result_digest + 1*16], xmm1 
        vmovdqa [job_rax + _result_digest + 2*16], xmm2 
        vmovdqa [job_rax + _result_digest + 3*16], xmm3 

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
        mov     rsp, [rsp + _RSP_SAVE]  ; original SP
        ret

section .data
align 16

lane_1: dq  1
lane_2: dq  2
lane_3: dq  3

%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
        dw 0x%4
        db 0x%3, 0x%2
%endmacro
;;;       func                  core, ver, snum
slversion sha512_flush_job_avx2, 04,   01,  0111

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
%include "job_sha512.asm"
%include "mb_mgr_sha512_datastruct_x4.asm"
%include "reg_sizes.asm"
%include "memcpy.asm"

extern sha512_x4_avx2

%ifidn __OUTPUT_FORMAT__, elf64
%define arg1    rdi
%define arg2    rsi
%define reg3    rcx
%define reg4    rdx
%else
%define arg1    rcx
%define arg2    rdx
%define reg3    rdi
%define reg4    rsi
%endif

%define state   arg1
%define job     arg2
%define len2    arg2


; idx needs to be in rbp, r13, r14, r16
%define last_len        rbp     ;variable to check if length (in bytes) is greater than 128
%define idx             rbp
            
%define p               r11     ;used to store buffer address
%define start_offset    r11     ;ldata[#lane]'s offset to start of data 

%define unused_lanes    rbx     ;local copy of the mb_mgr's unused_lanes variable
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
        
%define lane_data       r10     ;Pointer to current lane's entry in mb_mgr's ldata array


%define PTR_SZ 8                ; for 64 bit systems
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

; JOB_SHA512* sha512_submit_job_avx2(SHA512_MB_MGR_X4 *state, JOB_SHA512 *job)
; arg 1 : rcx : state
; arg 2 : rdx : job
global sha512_submit_job_avx2:function
sha512_submit_job_avx2:

        mov     rax, rsp                        ; Back up original SP
        sub     rsp, STACK_SPACE
        and     rsp, -32                        ;align stack
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
        mov     [rsp + _RSP_SAVE], rax          ; Back up original SP

        ;Copy the mb_mgr structure's unused_lanes variable and get next free lane index
        mov     unused_lanes, [state + _unused_lanes]
        movzx   lane, BYTE(unused_lanes)
        shr     unused_lanes, 8     ; Shift right 1 byte to remove the index entry we've taken         

        ;Calculate the address of our lane's entry in the mb_mgr's ldata array
        imul    lane_data, lane, _LANE_DATA_size
        lea     lane_data, [state + _ldata + lane_data]

        ;Update the global unused_lanes variable
        mov     [state + _unused_lanes], unused_lanes   
        mov     dword [job + _status], STS_BEING_PROCESSED
                                                        
        ;Get the current job length in blocks
        mov     DWORD(len), [job + _len]                ; len = message length in bytes
        mov     tmp, len
        shr     tmp, 7-4                ; divide by 128, to get len in terms of blocks and shl by 
                                        ; one nibble (we stamp the lane index on this nibble)

        ;Copy the job structure's address to the ldata entry's pointer (interleaved)
        mov     [lane_data + _job_in_lane], job         

        and     tmp, ~0xF       ; Zero the nibble and stamp the lane index on the length
        or      tmp, lane

        ;Put the length (#blocks) of our lane's job into the mb_mgr's len array
        mov     [state + _lens + 4*lane], DWORD(tmp)     ;(32bit elements in len array)

        ; Get the number of extra blocks we'll need for padding, this depends on the length of the
        ; last block of data; 2 for length of last block > 112
        mov     last_len, len          
        and     last_len, 127                           ; Take the remainder mod 128, add 17 (for the 
        lea     extra_blocks, [last_len + 17 + 127]     ; length and first padding byte) 
        shr     extra_blocks, 7                         ; extra_blocks will be 1 or 2
        mov     [lane_data + _extra_blocks], DWORD(extra_blocks)

        mov     size_offset, extra_blocks       ;size_offset is the offset between end of data and the
        shl     size_offset, 7                  ;binary representation of the message length
        sub     size_offset, last_len
        add     size_offset, 128-8              
        mov     [lane_data + _size_offset], DWORD(size_offset)
        mov     start_offset, 128               ;start_offset points to beginning last_len bytes
        sub     start_offset, last_len
        mov     [lane_data + _start_offset], DWORD(start_offset)

        lea     tmp, [8*len] 
        bswap   tmp                             
        mov     [lane_data + _extra_block + size_offset], tmp

; Copy in H0-H7 as initial digest for each lane in the state (transposed)
%assign I 0 
%rep 4
     vmovdqu    xmm0, [H0 + I * 2 * SHA512_DIGEST_WORD_SIZE]
     vmovq      [state + _args_digest + SHA512_DIGEST_WORD_SIZE*lane + (2*I + 0)*SHA512_DIGEST_ROW_SIZE], xmm0
     vpextrq    [state + _args_digest + SHA512_DIGEST_WORD_SIZE*lane + (2*I + 1)*SHA512_DIGEST_ROW_SIZE], xmm0, 1
%assign I (I+1)
%endrep

       ; Check job is greater than one complete block, if not, work on the extra_block
        cmp     len, 128
        jb      copy_lt128_bytes

        ;Point the state's data pointer to the job's buffer
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + PTR_SZ*lane], p

; Copy the last block of the  buffer into the lane's extra block array for messages greater than 1 block
fast_copy:
        add     p, len
        vmovdqu ymm0, [p - 128 + 0*32]
        vmovdqu ymm1, [p - 128 + 1*32]
        vmovdqu ymm2, [p - 128 + 2*32]
        vmovdqu ymm3, [p - 128 + 3*32]
        vmovdqu [lane_data + _extra_block + 0*32], ymm0
        vmovdqu [lane_data + _extra_block + 1*32], ymm1
        vmovdqu [lane_data + _extra_block + 2*32], ymm2
        vmovdqu [lane_data + _extra_block + 3*32], ymm3
end_fast_copy:
        cmp     unused_lanes, 0xff
        jne     return_null

    
start_loop:
        ; Find min length - length variable also contains the index
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
        shl     extra_blocks, 4                 ; Again imprint the lane index on the length variable
        or      extra_blocks, idx
        mov     [state + _lens + 4*idx], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     [state + _args_data_ptr + PTR_SZ*idx], tmp ; idx is index of shortest length message
        mov     dword [lane_data + _extra_blocks], 0
        jmp     start_loop

        align   16

; For messages less than one block, the extra blocks become our working buffer
copy_lt128_bytes:
        shl     extra_blocks, 4
        or      extra_blocks, lane
        mov     [state + _lens + 4*lane], DWORD(extra_blocks)
        lea     tmp, [lane_data + _extra_block + start_offset]
        mov     p, [job + _buffer]
        mov     [state + _args_data_ptr + PTR_SZ*lane], tmp 
        mov     dword [lane_data + _extra_blocks], 0

        ;; less than one message block of data
        ;; destination extra block but backwards by len from where 0x80 pre-populated 
        lea     p2, [lane_data + _extra_block  + 128]
        sub     p2, len

        ; Point to the last_len bytes of the job's buffer and copy to our extra block
        add     p, len
        sub     p, last_len
        ; dst = p2, src=p, 
        memcpy_avx2_128 p2, p, len, tmp4, tmp2, ymm0, ymm1, ymm2, ymm3
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
        shl     unused_lanes, 8
        or      unused_lanes, idx               ; Put our freed lane back
        mov     [state + _unused_lanes], unused_lanes

        ;Copy result into the job's digest array
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
;Initialisation vector 
H0:  dq 0x6a09e667f3bcc908
H1:  dq 0xbb67ae8584caa73b
H2:  dq 0x3c6ef372fe94f82b
H3:  dq 0xa54ff53a5f1d36f1
H4:  dq 0x510e527fade682d1
H5:  dq 0x9b05688c2b3e6c1f
H6:  dq 0x1f83d9abfb41bd6b
H7:  dq 0x5be0cd19137e2179

;version macro
%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
        dw 0x%4
        db 0x%3, 0x%2
%endmacro
;;;       func                   core, ver, snum
slversion sha512_submit_job_avx2, 04,   01,  0110

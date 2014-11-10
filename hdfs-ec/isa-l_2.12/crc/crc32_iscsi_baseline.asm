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

;;; ISCSI CRC 32 Implementation with crc32 Instruction

;;; unsigned int crc32_iscsi_withInst(unsigned char * buffer, int len, unsigned int crc_init)
;;;        *buf = rcx
;;;         len = rdx
;;;

global  crc32_iscsi_baseline:function
crc32_iscsi_baseline:    

%ifidn __OUTPUT_FORMAT__, elf64
%define bufp            rdi
%define len             rsi 
%define crc_init        rdx 
%else
%define bufp            rcx
%define len             rdx 
%define crc_init        r8 
%endif


	mov     rax, crc_init       ;; rax = crc_init;

	mov     r10, bufp
	neg     r10
	and     r10, 7        ;; calculate the unallignment amount of the address
	je      proc_crc      ;; Skip if alligned

	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;    
	cmp     len, 8
	jb      less_than_8
	mov     r9, [bufp]    ;; load a quadword from the buffer
	add     bufp, r10     ;; allign buffer pointer for quadword processing
	sub     len, r10      ;; update buffer length

	;;;; Calculate CRC of unalligned bytes of the buffer (if any) ;;;;
align_loop:
	crc32   rax, r9b      ;; compute crc32 of 1-byte
	shr     r9, 8         ;; get next byte
	dec     r10
	jne     align_loop    

proc_crc:   
	mov     r9, len       ;; r9 = len;
	shr     r9, 5         ;; r9 = r9/32  ; quad_quadword processing    
	je      proc_quad     ;; jump to proc_quad if # quad_quadwords = 0

	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
crc_loop:                     ;; for(i=r9; i>0 ; i--) {
	crc32   rax, [bufp]   ;;    compute crc32 of 64-bit data        
	crc32   rax, [bufp+8*1]
	crc32   rax, [bufp+8*2]
	crc32   rax, [bufp+8*3]
	add     bufp, 8*4     ;;    buf +=32; (next quad_quadword = 32 bytes)
	dec     r9
	jne     crc_loop      ;; }
	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

	;;;;;;; Calculate CRC of excess-quadword part of the buffer ;;;;;;;
proc_quad:      
	and     len, 31       ;; Calculate excess-quadword part of the buffer
	mov     r9, len
	shr     r9, 3         ;; r9 = excess quadword length    
	je      proc_byte     ;; jump to proc_byte if # quadwords = 0   

	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
quad_loop:
	crc32   rax, [bufp]   ;; compute crc32 of quadword
	add     bufp, 8       ;; buf +=8; (next quadword=8 bytes)
	dec     r9
	jne     quad_loop    
	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

	;;;;;;;;; Calculate CRC of excess-byte part of the buffer ;;;;;;;;;
proc_byte:      
	and     len, 7        ;; Calculate excess-byte part of the buffer
	je      do_return     ;; return if # bytes = 0  

	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	mov     r9, [bufp]    ;; load a quadword from the buffer
byte_loop:
	crc32   rax, r9b      ;; compute crc32 of 1-byte
	shr     r9, 8         ;; get next byte
	dec     len
	jne     byte_loop    
	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

do_return:
	ret

less_than_8:
	test    len,4
	jz      less_than_4
	crc32   eax, dword[bufp]
	add     bufp,4
less_than_4:
	test    len,2
	jz      less_than_2
	crc32   eax, word[bufp]
	add     bufp,2
less_than_2:
	test    len,1
	jz      do_return
	crc32   rax, byte[bufp]
	ret


%macro slversion 4
global %1_slver_%2%3%4
global %1_slver
%1_slver:
%1_slver_%2%3%4:
	dw 0x%4
	db 0x%3, 0x%2
%endmacro
;;;       func          core, ver, snum
slversion crc32_iscsi_baseline, 00,   01,  0013


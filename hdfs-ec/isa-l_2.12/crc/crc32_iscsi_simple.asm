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

;;; unsigned int crc32_iscsi_simple(unsigned char *buffer, unsigned int len, 
;;;                         unsigned int crc_init);
;;;
;;;        *buf = rcx
;;;         len = rdx
;;;    crc_init = r8

%ifidn __OUTPUT_FORMAT__, elf64
%define bufp     rdi 
%define len      rsi
%define crc_init rdx
%else
%define bufp     rcx 
%define len      rdx
%define crc_init r8
%endif

global  crc32_iscsi_simple:function
crc32_iscsi_simple:
	mov     rax, crc_init
	cmp     len, 8
	jb      lt8

loop1:
	crc32   rax, [bufp]
	add     bufp, 8
	sub     len, 8
	cmp     len, 8
	jae     loop1

lt8:
	cmp     len, 4
	jb      lt4
	crc32   eax, [bufp]
	add     bufp, 4
	sub     len, 4
lt4:
	cmp     len, 2
	jb      lt2
	crc32   eax, word [bufp]
	add     bufp, 2
	sub     len, 2
lt2:
	cmp     len, 1
	jb      lt1
	crc32   eax, byte [bufp]
lt1:
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
slversion crc32_iscsi_simple, 00,   01,  0012


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

%ifndef __OPTIONS_ASM__
%define __OPTIONS_ASM__

%define DEFLATE

; Options:dir
; c - Use PCLMULQDQ for CRC calc
; m - reschedule mem reads
; e b - bitbuff style
; t s x - compare style
; h - limit hash updates
; l - use longer huffman table
; f - fix cache read

; Invalid Combinations
; --------+----+-------+-------+-------+-------+------------------
;         |          COMPARE TYPE              | LIMIT_HASH_UPDATE
;         | 1  | 2 (t) | 3 (s) | 4 (x) | 5 (y) |
;         +----+-------+-------+-------+-------+------------------
; IGZIP0  | no |       |  no   |  no   |       |
; IGZIP0c |    |  no   |  no   |       |       |
; IGZIP1  | no |       |  no   |  no   |       |      no
; IGZIP1c |    |       |  no   |       |       |
; --------+----+-------+-------+-------+-------+------------------

; All versions are "m" variants
%define IGZIP0  1
%define IGZIP0C 2
%define IGZIP1  3
%define IGZIP1C 4

%ifndef MAJOR_VERSION
%define MAJOR_VERSION IGZIP0C
%endif

%ifdef LARGE_WINDOW
%define HIST_SIZE 32
%else
%define HIST_SIZE 8
%endif

; bit buffer types
; BITBUFB: (b) Always write data
%define USE_BITBUFB

; compare types
; 1: ( ) original
; 4: (x) with xmm / pmovbmsk
%ifndef COMPARE_TYPE
%define COMPARE_TYPE 1
%endif

%if ((MAJOR_VERSION == IGZIP0C || MAJOR_VERSION == IGZIP1C) && (COMPARE_TYPE == 2 || COMPARE_TYPE == 3))
%undef COMPARE_TYPE
%define COMPARE_TYPE 1
%elif ((MAJOR_VERSION == IGZIP0 || MAJOR_VERSION == IGZIP1) && COMPARE_TYPE != 2)
%undef COMPARE_TYPE
%define COMPARE_TYPE 2
%endif

; (h) limit hash update
;    not supported in version IGZIP1
%if (MAJOR_VERSION != IGZIP1)
%define LIMIT_HASH_UPDATE
%endif

; (l) longer huffman table
%define LONGER_HUFFTABLE

; (f) fix cache read problem
%define FIX_CACHE_READ

%if (HIST_SIZE > 8)
%undef LONGER_HUFFTABLE
%endif

%ifidn __OUTPUT_FORMAT__, elf64
%define WRT_OPT         wrt ..sym
%else
%define WRT_OPT
%endif

%endif ; ifndef __OPTIONS_ASM__

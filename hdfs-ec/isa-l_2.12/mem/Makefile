########################################################################
#  Copyright(c) 2011-2014 Intel Corporation All rights reserved.
#
#  Redistribution and use in source and binary forms, with or without
#  modification, are permitted provided that the following conditions 
#  are met:
#    * Redistributions of source code must retain the above copyright
#      notice, this list of conditions and the following disclaimer.
#    * Redistributions in binary form must reproduce the above copyright
#      notice, this list of conditions and the following disclaimer in
#      the documentation and/or other materials provided with the
#      distribution.
#    * Neither the name of Intel Corporation nor the names of its
#      contributors may be used to endorse or promote products derived
#      from this software without specific prior written permission.
#
#  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
#  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
#  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
#  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
#  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
#  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
#  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
#  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
#  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
#  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
#  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
########################################################################


lib_name    := 	mem_routines.a
lsrc        += 	mem_zero_detect_avx.asm \
		mem_cmp_sse.asm \
		mem_cmp_avx.asm \
		mem_cmp_avx2.asm \
		mem_cpy_sse.asm \
		mem_cpy_avx.asm
extern_hdrs +=	mem_routines.h test.h types.h
unit_tests  +=  mem_zero_detect_avx_test \
		mem_cmp_sse_test \
		mem_cmp_avx_test \
		mem_cmp_avx2_test \
		mem_cpy_sse_test \
		mem_cpy_avx_test
perf_tests  +=  mem_zero_detect_avx_perf \
		mem_cmp_sse_perf \
		mem_cmp_avx_perf \
		mem_cmp_avx2_perf \
		mem_cpy_sse_perf \
		mem_cpy_avx_perf

VPATH = .. ../include

-include ../make.inc

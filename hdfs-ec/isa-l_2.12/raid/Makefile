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


lib_name    := 	raid.a

lsrc        += 	xor_gen_sse.asm pq_gen_sse.asm xor_check_sse.asm \
		pq_check_sse.asm xor_gen_avx.asm pq_gen_avx.asm \
		xor_gen_avx.asm pq_gen_avx.asm pq_gen_avx2.asm \
		raid_base.c raid_multibinary.asm

extern_hdrs +=	raid.h test.h types.h

unit_tests  +=  xor_gen_test pq_gen_test xor_check_test pq_check_test
unit_tests  +=  xor_gen_sse_test pq_gen_sse_test xor_check_sse_test 
unit_tests  +=  pq_check_sse_test
unit_tests  +=  xor_gen_avx_test pq_gen_avx_test
unit_tests  +=  pq_gen_avx2_test
unit_tests  +=  pq_gen_base_test xor_gen_base_test

perf_tests  +=  xor_gen_perf pq_gen_perf 
perf_tests  +=  xor_gen_sse_perf pq_gen_sse_perf 
perf_tests  +=  xor_gen_avx_perf pq_gen_avx_perf
perf_tests  +=  pq_gen_avx2_perf

examples    += 	xor_example
other_tests +=  pq_load_test xor_load_test pq_gen_sse_compare_perf

lsrc32       += xor_gen_sse.asm pq_gen_sse_i32.asm xor_check_sse.asm pq_check_sse_i32.asm raid_base.c
unit_tests32 += xor_gen_sse_test pq_gen_sse_test xor_check_sse_test pq_check_sse_test pq_gen_base_test xor_gen_base_test
perf_tests32 += pq_gen_sse_perf xor_gen_sse_perf

VPATH = .. ../include

-include ../make.inc


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

lib_name   := md5_mb.a

lsrc += md5_ctx_sse.c \
	md5_ctx_avx.c \
	md5_ctx_avx2.c 

lsrc += md5_mb_mgr_init_sse.c \
	md5_mb_mgr_init_avx2.c 

lsrc += md5_mb_mgr_submit_sse.asm \
	md5_mb_mgr_submit_avx.asm \
	md5_mb_mgr_submit_avx2.asm \
	md5_mb_mgr_flush_sse.asm \
	md5_mb_mgr_flush_avx.asm \
	md5_mb_mgr_flush_avx2.asm \
	md5_mb_x4x2_sse.asm \
	md5_mb_x4x2_avx.asm \
	md5_mb_x8x2_avx2.asm \
	md5_multibinary.asm

extern_hdrs += md5_mb.h memcpy_inline.h intrinreg.h

other_src += 	datastruct.asm \
		md5_job.asm \
		md5_mb_mgr_datastruct.asm \
		reg_sizes.asm \
		md5_ref.c

unit_tests  += 	md5_mb_test \
		md5_mb_rand_test \
		md5_mb_rand_update_test \
		md5_mb_rand_ssl_test

perf_tests  +=  md5_mb_vs_ossl_perf 

-include ../make.inc

VPATH = ../include

md5_mb_rand_test: md5_ref.o
md5_mb_rand_update_test: md5_ref.o
md5_mb_rand_ssl_test: LDLIBS += -lcrypto
md5_mb_vs_ossl_perf: LDLIBS += -lcrypto

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


lib_name   := mb_md5.a

lsrc        += mb_mgr_md5.c md5_x4x2_sse.asm mb_mgr_md5_submit.asm mb_mgr_md5_flush.asm \
		md5_x4x2_avx.asm mb_mgr_md5_submit_avx.asm mb_mgr_md5_flush_avx.asm \
		md5_x8x2_avx2.asm mb_mgr_md5_submit_avx2.asm mb_mgr_md5_flush_avx2.asm
other_src   += datastruct.asm job_md5.asm mb_mgr_md5_datastruct.asm reg_sizes.asm md5_reference.c \
		mb_mgr_md5_datastruct_x8x2.asm
extern_hdrs += mb_md5.h multi_buffer.h types.h test.h

unit_tests  += multi_buffer_md5_test
unit_tests  += multi_buffer_md5_rand_test
unit_tests  += multi_buffer_md5_avx_test multi_buffer_md5_avx_rand_test
unit_tests  += multi_buffer_md5_avx2_test multi_buffer_md5_avx2_rand_test 
unit_tests  += multi_buffer_md5_rand_update_test multi_buffer_md5_avx_rand_update_test

perf_tests  += multi_buffer_md5_vs_ossl_perf multi_buffer_md5_avx_vs_ossl_perf multi_buffer_md5_avx2_vs_ossl_perf
examples    +=

-include ../make.inc

multi_buffer_md5_rand_test: md5_reference.o
multi_buffer_md5_avx_rand_test: md5_reference.o
multi_buffer_md5_avx2_rand_test: md5_reference.o
multi_buffer_md5_rand_update_test: md5_reference.o
multi_buffer_md5_avx_rand_update_test: md5_reference.o
multi_buffer_md5_vs_ossl_perf:   LDLIBS += -lcrypto
multi_buffer_md5_avx_vs_ossl_perf:   LDLIBS += -lcrypto
multi_buffer_md5_avx2_vs_ossl_perf:   LDLIBS += -lcrypto

VPATH = ../include

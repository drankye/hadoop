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


lib_name   := mb_sha1.a

lsrc        += mb_mgr_sha1.c sha1_mult.asm mb_mgr_sha1_submit.asm mb_mgr_sha1_flush.asm \
               sha1_mult_avx.asm mb_mgr_sha1_submit_avx.asm mb_mgr_sha1_flush_avx.asm \
               sha1_mult_avx2.asm mb_mgr_sha1_submit_avx2.asm mb_mgr_sha1_flush_avx2.asm
other_src   += datastruct.asm job_sha1.asm mb_mgr_sha1_datastruct.asm \
               mb_mgr_sha1_datastruct_x8.asm reg_sizes.asm sha1_reference.c memcpy.asm
extern_hdrs += mb_sha1.h multi_buffer.h types.h test.h

unit_tests  += multi_buffer_sha1_test 
unit_tests  += multi_buffer_sha1_rand_test multi_buffer_sha1_rand_ssl_test
unit_tests  += multi_buffer_sha1_avx_test multi_buffer_sha1_avx_rand_test multi_buffer_sha1_avx_rand_ssl_test
unit_tests  += multi_buffer_sha1_rand_update_test multi_buffer_sha1_avx_rand_update_test
unit_tests  += multi_buffer_sha1_avx2_test multi_buffer_sha1_avx2_rand_ssl_test multi_buffer_sha1_avx2_rand_test 

perf_tests  += multi_buffer_sha1_vs_ossl_perf multi_buffer_sha1_avx_vs_ossl_perf multi_buffer_sha1_avx2_vs_ossl_perf
examples    += multi_buffer_sha1_example

-include ../make.inc

multi_buffer_sha1_rand_test:  sha1_reference.o
multi_buffer_sha1_avx_rand_test: sha1_reference.o
multi_buffer_sha1_rand_update_test: sha1_reference.o
multi_buffer_sha1_avx_rand_update_test: sha1_reference.o
multi_buffer_sha1_avx2_rand_test: sha1_reference.o
multi_buffer_sha1_rand_ssl_test: LDLIBS += -lcrypto
multi_buffer_sha1_avx_rand_ssl_test: LDLIBS += -lcrypto
multi_buffer_sha1_avx2_rand_ssl_test: LDLIBS += -lcrypto
multi_buffer_sha1_vs_ossl_perf: LDLIBS += -lcrypto 
multi_buffer_sha1_avx_vs_ossl_perf: LDLIBS += -lcrypto 
multi_buffer_sha1_avx2_vs_ossl_perf: LDLIBS += -lcrypto 


VPATH = ../include

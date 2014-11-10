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


lib_name := aes_xts_256.a
lsrc     += aes_keyexp_256.asm XTS_AES_256_dec.asm XTS_AES_256_dec_expanded_key.asm 
lsrc     += XTS_AES_256_enc.asm XTS_AES_256_enc_expanded_key.asm

other_test_src   += aes_xts_256_vect.h
extern_hdrs += test.h aes_xts.h
unit_tests  += aes_xts_256_test aes_xts_256_rand
unit_tests  += aes_xts_256_rand_ossl_test
unit_tests  += aes_xts_256_expanded_key_test
perf_tests  += aes_xts_256_enc_perf aes_xts_256_dec_perf
perf_tests  += aes_xts_256_enc_ossl_perf aes_xts_256_dec_ossl_perf

-include ../make.inc

VPATH = ../include

aes_xts_256_enc_ossl_perf : LDLIBS += -lcrypto
aes_xts_256_dec_ossl_perf : LDLIBS += -lcrypto
aes_xts_256_rand_ossl_test: LDLIBS += -lcrypto

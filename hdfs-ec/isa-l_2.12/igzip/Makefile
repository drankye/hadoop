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


lib_name    := 	igzip.a

lsrc        += 	igzip.c crc_utils.asm hufftables.asm hufftables_c.c \
		init_stream.asm utils_asm.asm \
		igzip0_body.asm igzip0_finish.asm igzip0c_body.asm igzip0c_finish.asm \
		igzip1_body.asm igzip1_finish.asm igzip1c_body.asm igzip1c_finish.asm \
		igzip0c_body_stateless.asm igzip0c_finish_stateless.asm \
		crc32_gzip.asm detect_repeated_char.asm

extern_hdrs +=	igzip_lib.h types.h test.h

unit_tests  += 	igzip_rand_test
perf_tests  +=  igzip_perf igzip_sync_flush_perf
other_tests +=  igzip_file_perf igzip_sync_flush_file_perf igzip_stateless_file_perf
other_src   += 	bitbuf2.asm crc_pcl.asm data_struct2.asm hash.asm crc.asm \
		igzip0_body.asm igzip0_finish.asm igzip0c_body.asm igzip0c_finish.asm \
		igzip1_body.asm igzip1_finish.asm igzip1c_body.asm igzip1c_finish.asm \
		lz0a_const.asm options.asm stdmac.asm utils.asm \
		bitbuf2.h fast_lz77.h huffman.h repeated_char_8k_result.h \
		huffman.asm options.inc reg_sizes.asm

examples    += 	igzip_example igzip_sync_flush_example igzip_bytes_consumed_example igzip_bytes_consumed_example_with_init

-include ../make.inc

VPATH = ../include

igzip_rand_test: LDLIBS += -lz -lm


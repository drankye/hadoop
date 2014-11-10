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


lib_name    :=  crc.a
lsrc        +=  crc16_t10dif_01.asm crc32_ieee_01.asm crc32_iscsi_baseline.asm \
		crc32_iscsi_01.asm crc32_iscsi_00.asm crc32_iscsi_simple.asm \
		crc_multibinary.asm crc_base.c crc16_t10dif_by4.asm \
		crc32_ieee_by4.asm
extern_hdrs +=  crc.h types.h test.h
unit_tests  +=  crc16_t10dif_test crc16_t10dif_01_test crc32_ieee_test \
		crc32_ieee_01_test crc32_iscsi_baseline_test crc32_iscsi_test \
		crc32_iscsi_01_test crc32_iscsi_00_test crc32_iscsi_simple_test \
		crc16_t10dif_by4_test crc32_ieee_by4_test
perf_tests  +=  crc16_t10dif_perf crc16_t10dif_01_perf crc32_ieee_perf \
		crc32_ieee_01_perf crc32_iscsi_baseline_perf crc32_iscsi_perf \
		crc32_iscsi_01_perf crc32_iscsi_00_perf crc32_iscsi_simple_perf \
		crc16_t10dif_by4_perf crc32_ieee_by4_perf
examples    +=  crc_simple_test

-include ../make.inc

VPATH = ../include

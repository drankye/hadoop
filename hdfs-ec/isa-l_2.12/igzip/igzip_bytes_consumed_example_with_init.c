/**********************************************************************
  Copyright(c) 2011-2014 Intel Corporation All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions 
  are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.
    * Neither the name of Intel Corporation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**********************************************************************/
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "igzip_lib.h"

#define BUF_SIZE 8 * 1024
LZ_Stream1 stream;

int main(int argc, char *argv[])
{
	uint8_t inbuf[BUF_SIZE], outbuf[BUF_SIZE];
	int size = 0;		/*keep track of what was read in in case it's < BUF_SIZE */
	FILE *in, *out;
	uint8_t *last_next_in = NULL;
	int bytes_consumed = 0, full_buffer = 0;

	if (argc != 3) {
		fprintf(stderr,
			"Usage: igzip_bytes_consumed_example_with_init infile outfile\n");
		exit(0);
	}
	in = fopen(argv[1], "rb");
	if (!in) {
		fprintf(stderr, "Can't open %s for reading\n", argv[1]);
		exit(0);
	}
	out = fopen(argv[2], "wb");
	if (!out) {
		fprintf(stderr, "Can't open %s for writing\n", argv[2]);
		exit(0);
	}
	printf("igzip_bytes_consumed_example_with_init\nWindow Size: %d K\n", HIST_SIZE);
	fflush(0);
	init_stream(&stream);
	stream.flush = SYNC_FLUSH;
	stream.avail_out = BUF_SIZE;
	stream.next_out = outbuf;

	do {
		/*Keep Calling compress while there is space in the output buffer */
		/*READ again if all input has been consumed */
		if (size == stream.bytes_consumed) {
			size = stream.avail_in = (uint32_t) fread(inbuf, 1, BUF_SIZE, in);
			stream.end_of_stream = feof(in);
			stream.next_in = inbuf;
			last_next_in = inbuf;
		}
		stream.next_out = outbuf + (BUF_SIZE - stream.avail_out);
		fast_lz(&stream);
		if (stream.avail_out < 13) {	/* Buffer is full */
			fwrite(outbuf, 1, BUF_SIZE - stream.avail_out, out);
			if (!stream.end_of_stream) {
				if (size == stream.bytes_consumed)
					full_buffer = 1;

				if (stream.bytes_consumed < size) {
					stream.next_in = last_next_in + stream.bytes_consumed;
					last_next_in = stream.next_in;
					stream.avail_in = size - stream.bytes_consumed;
					size -= stream.bytes_consumed;
				}
				if (full_buffer)
					bytes_consumed = stream.bytes_consumed;

				init_stream(&stream);
				stream.flush = SYNC_FLUSH;
				if (full_buffer) {
					stream.bytes_consumed = bytes_consumed;
					full_buffer = 0;
				}
			}
			stream.avail_out = BUF_SIZE;
			stream.next_out = outbuf;
		}

	} while (!stream.end_of_stream);

	/*didn't compress everything from the last buffer */
	while (stream.bytes_consumed < size) {
		stream.next_out = outbuf + (BUF_SIZE - stream.avail_out);
		fast_lz(&stream);
		fwrite(outbuf, 1, BUF_SIZE - stream.avail_out, out);
		stream.avail_out = BUF_SIZE;
		stream.next_out = outbuf;
	}
	if (stream.avail_out != BUF_SIZE) {
		fwrite(outbuf, 1, BUF_SIZE - stream.avail_out, out);
		stream.avail_out = BUF_SIZE;
		stream.next_out = outbuf;
	}

	fclose(out);
	fclose(in);
	printf("End of igzip_bytes_consumed_example_with_init\n\n");
	return 0;
}

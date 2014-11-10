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

#include<stdio.h>
#include<stdint.h>
#include<string.h>
#include<stdlib.h>
#include<sys/time.h>
#include<time.h>		// for nanosleep()
#include "raid.h"

//#define CACHED_TEST
#ifdef CACHED_TEST
// Cached test, loop many times over small dataset
# define TEST_SOURCES 16
# define TEST_LEN     8*1024
# define TEST_LOOPS   400
#else
# ifndef TEST_CUSTOM
// Uncached test.  Pull from large mem base.
#  define TEST_SOURCES 16
#  define GT_L3_CACHE  32*1024*1024	/* some number > last level cache */
#  define TEST_LEN     GT_L3_CACHE / TEST_SOURCES
#  define TEST_LOOPS   40
# else
#  ifndef TEST_LOOPS
#   define TEST_LOOPS  1000
#  endif
# endif
#endif

struct perf {
	struct timeval tv;
};

int perf_start(struct perf *p)
{
	return gettimeofday(&(p->tv), 0);
}

int perf_stop(struct perf *p)
{
	int end = gettimeofday(&(p->tv), 0);
	return end;
}

void perf_print(struct perf stop, struct perf start, long long dsize)
{
	long long secs = stop.tv.tv_sec - start.tv.tv_sec;
	long long usecs = secs * 1000000 + stop.tv.tv_usec - start.tv.tv_usec;
	printf("runtime = %10lld usecs, ", usecs);
	printf("bandwidth %lld MB ", dsize / (1024 * 1024));
	printf("in %.4f sec ", (double)usecs / 1000000);
	printf("= %.2f MB/s\n", (double)dsize / usecs);
}

int main(int argc, char *argv[])
{
	int i;
	int ret;

	// Defaults
	int verbose = 2;
	double duty_cycle = 0.5;
	double bandwidth = -1.0;	// Disabled by default
	long long pause_sec = 1;
	long long sources = TEST_SOURCES;
	long long len = TEST_LEN;
	long long loops = TEST_LOOPS;

	for (i = 1; i < argc; i++) {
		if (argv[i][0] != '-')
			continue;

		switch (argv[i][1]) {
		case 'q':
			--verbose;
			break;
		case 'l':
			if (argc >= i + 1) {
				loops = atoi(argv[++i]);
			}
			break;
		case 's':
			if (argc < i + 1)
				continue;
			sources = atoi(argv[++i]);
			if (sources <= 2) {
				printf("bad number of sources");
				return -1;
			}
			break;
		case 'd':
			if (argc < i + 1)
				continue;
			duty_cycle = atof(argv[++i]);
			if (duty_cycle <= 0 || duty_cycle >= 1) {
				printf("bad choice of duty cycle");
				return -1;
			}
			break;
		case 'p':
			if (argc < i + 1)
				continue;
			pause_sec = atoi(argv[++i]);
			if (pause_sec < 0) {
				printf("bad choise of pause time");
				return -1;
			}
			break;
		case 'n':
			if (argc < i + 1)
				continue;
			len = atoi(argv[++i]);
			if (i + 1 < argc && argv[i + 1][0] != '-') {
				switch (argv[++i][0]) {
				case 'M':
					len *= (1024 * 1024);
					break;
				case 'K':
					len *= 1024;
					break;
				}
			}
			if (len % 64 != 0) {
				printf("len must be a multiple of 64");
				return -1;
			}
			break;
		case 'b':
			if (argc < i + 1)
				continue;
			bandwidth = atof(argv[++i]);
			if (i + 1 < argc && argv[i + 1][0] != '-') {
				switch (argv[++i][0]) {
				case 'G':
					bandwidth *= (1000 * 1024 * 1024);
					break;
				case 'M':
					bandwidth *= (1024 * 1024);
					break;
				case 'K':
					bandwidth *= 1024;
					break;
				}
			}
			if (bandwidth < 0) {
				printf("bad bandwidth input");
				return -1;
			}

			break;
		case 'h':
			printf("%s: [options]\n"
			       "\t-q                - more quiet (can be repeated)\n"
			       "\t-p <secs>         - set pause time\n"
			       "\t-d <duty cycle>   - set duty cycle ratio\n"
			       "\t-b <bw/s> [G,M,K] - adjust for set memory bandwidth instead\n"
			       "\t-s <sources>      - number of sources\n"
			       "\t-n <len> [M,K]    - length of each source\n"
			       "\t-l <loops>        - initial number of loops\n", argv[0]);
			return 0;
			break;
		}

	}
	long long test_mem = ((sources + 2) * (len));

	printf("Test pq_gen_sse_perf %lld sources X %lld bytes\n", sources, len);
	if (pause_sec != 0 && bandwidth < 0)
		printf("duty cycle %f, pause time %lld s\n", duty_cycle, pause_sec);

	void **buffs;
	void *buff;
	ret = posix_memalign((void **)&buff, 8, sizeof(int *) * (sources + 6));
	if (ret) {
		printf("alloc error: Fail");
		return 1;
	}
	buffs = buff;

	// Allocate the arrays
	for (i = 0; i < sources + 2; i++) {
		void *buf;
		if (posix_memalign(&buf, 16, len)) {
			printf("alloc error: Fail");
			return 1;
		}
		buffs[i] = buf;
	}

	// Setup data
	for (i = 0; i < sources + 2; i++)
		memset(buffs[i], 0, len);

	struct perf start, stop, stop2;
	struct timespec ptime;
	double duty_ratio = duty_cycle / (1 - duty_cycle);

	ptime.tv_sec = pause_sec;
	ptime.tv_nsec = 0;

	// Warm up
	pq_gen_sse(sources + 2, len, buffs);

	// Special case if pause == 0
	if (pause_sec == 0) {
		printf("Running flat out with no pause between %lld loops\n", loops);
		while (1) {
			perf_start(&start);
			for (i = 0; i < loops; i++)
				pq_gen_sse(sources + 2, len, buffs);
			perf_stop(&stop);

			if (verbose) {
				printf("pq_gen_sse: ");
				perf_print(stop, start, test_mem * i);
			}
		}
	}

	if (bandwidth > 0) {
		printf("Running dynamic loops to target bandwidth = %.0f B/s\n", bandwidth);
		printf("  starting loops = %lld\n", loops);
		while (1) {
			perf_start(&start);
			for (i = 0; i < loops; i++)
				pq_gen_sse(sources + 2, len, buffs);
			perf_stop(&stop);
			nanosleep(&ptime, NULL);
			perf_stop(&stop2);

			long long t1 = (stop.tv.tv_sec - start.tv.tv_sec) * 1000000
			    + stop.tv.tv_usec - start.tv.tv_usec;

			long long throughput = i * test_mem;
			long long newloops;

			newloops =
			    (loops * bandwidth * pause_sec) / (throughput -
							       (t1 * bandwidth / 1000000));

			if (verbose) {
				printf("  new loops = %lld\n", loops);
				if (verbose > 1) {
					printf("pq_gen_sse_tst: ");
					perf_print(stop, start, throughput);
					printf("pq_gen_sse_ave: ");
					perf_print(stop2, start, throughput);
				}
			}

			if (throughput * 1000000 < bandwidth * t1) {
				do {
					printf
					    ("Warning - can not hit BW target - running full out until under\n");

					newloops = bandwidth / test_mem;	// measure ~ every 1 sec.

					perf_start(&start);
					for (i = 0; i < newloops; i++)
						pq_gen_sse(sources + 2, len, buffs);
					perf_stop(&stop);

					t1 = (stop.tv.tv_sec - start.tv.tv_sec) * 1000000
					    + stop.tv.tv_usec - start.tv.tv_usec;

					throughput = i * test_mem;
					if (verbose > 1) {
						printf("pq_gen_sse_tst: ");
						perf_print(stop, start, throughput);
					}
				} while (throughput * 1000000 < bandwidth * t1);
				// Target 4/5 ratio to restart
				newloops = (i * bandwidth * 5 /*sec */ ) / (throughput -
									    (t1 * bandwidth /
									     1000000));
			}

			if (newloops > 2 * loops) {
				loops *= 2;
				printf("Warning - may not hit BW target\n");
			} else if (newloops < 1) {
				loops = 1;
				printf("Warning - cannot get below min with 1 loop %lld\n",
				       newloops);
			} else
				loops = newloops;

		}
	}
	// General case with duty cycle
	printf("Running dynamic loops to target %f duty cycle of available bandwidth\n",
	       duty_cycle);
	do {
		perf_start(&start);
		for (i = 0; i < loops; i++)
			pq_gen_sse(sources + 2, len, buffs);
		perf_stop(&stop);
		nanosleep(&ptime, NULL);
		perf_stop(&stop2);

		long long usecs = (stop.tv.tv_sec - start.tv.tv_sec) * 1000000
		    + stop.tv.tv_usec - start.tv.tv_usec;
		loops = duty_ratio * (loops * 1000000 * pause_sec) / usecs;

		if (verbose) {
			printf("  new loops = %lld\n", loops);
			if (verbose > 1) {
				printf("pq_gen_sse_tst: ");
				perf_print(stop, start, test_mem * i);
				printf("pq_gen_sse_ave: ");
				perf_print(stop2, start, test_mem * i);
			}
		}

	} while (1);

	return 0;
}

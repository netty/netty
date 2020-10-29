/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "netty_unix_util.h"

static const uint64_t NETTY_BILLION = 1000000000L;

#ifdef NETTY_USE_MACH_INSTEAD_OF_CLOCK

#include <mach/mach.h>
#include <mach/mach_time.h>

#endif /* NETTY_USE_MACH_INSTEAD_OF_CLOCK */

// util methods
uint64_t netty_unix_util_timespec_elapsed_ns(const struct timespec* begin, const struct timespec* end) {
  return NETTY_BILLION * (end->tv_sec - begin->tv_sec) + (end->tv_nsec - begin->tv_nsec);
}

jboolean netty_unix_util_timespec_subtract_ns(struct timespec* ts, uint64_t nanos) {
  const uint64_t seconds = nanos / NETTY_BILLION;
  nanos -= seconds * NETTY_BILLION;
  // If there are too many nanos we steal from seconds to avoid underflow on nanos. This way we
  // only have to worry about underflow on tv_sec.
  if (nanos > ts->tv_nsec) {
    --(ts->tv_sec);
    ts->tv_nsec += NETTY_BILLION;
  }
  const jboolean underflow = ts->tv_sec < seconds;
  ts->tv_sec -= seconds;
  ts->tv_nsec -= nanos;
  return underflow;
}

int netty_unix_util_clock_gettime(clockid_t clockId, struct timespec* tp) {
#ifdef NETTY_USE_MACH_INSTEAD_OF_CLOCK
  uint64_t timeNs;
  switch (clockId) {
  case CLOCK_MONOTONIC_COARSE:
    timeNs = mach_approximate_time();
    break;
  case CLOCK_MONOTONIC:
    timeNs = mach_absolute_time();
    break;
  default:
    errno = EINVAL;
    return -1;
  }
  // NOTE: this could overflow if time_t is backed by a 32 bit number.
  tp->tv_sec = timeNs / NETTY_BILLION;
  tp->tv_nsec = timeNs - tp->tv_sec * NETTY_BILLION; // avoid using modulo if not necessary
  return 0;
#else
  return clock_gettime(clockId, tp);
#endif /* NETTY_USE_MACH_INSTEAD_OF_CLOCK */
}

jboolean netty_unix_util_initialize_wait_clock(clockid_t* clockId) {
  struct timespec ts;
  // First try to get a monotonic clock, as we effectively measure execution time and don't want the underlying clock
  // moving unexpectedly/abruptly.
#ifdef CLOCK_MONOTONIC_COARSE
  *clockId = CLOCK_MONOTONIC_COARSE;
  if (netty_unix_util_clock_gettime(*clockId, &ts) == 0) {
    return JNI_TRUE;
  }
#endif
#ifdef CLOCK_MONOTONIC_RAW
  *clockId = CLOCK_MONOTONIC_RAW;
  if (netty_unix_util_clock_gettime(*clockId, &ts) == 0) {
    return JNI_TRUE;
  }
#endif
#ifdef CLOCK_MONOTONIC
  *clockId = CLOCK_MONOTONIC;
  if (netty_unix_util_clock_gettime(*clockId, &ts) == 0) {
    return JNI_TRUE;
  }
#endif

  // Fallback to realtime ... in this case we are subject to clock movements on the system.
#ifdef CLOCK_REALTIME_COARSE
  *clockId = CLOCK_REALTIME_COARSE;
  if (netty_unix_util_clock_gettime(*clockId, &ts) == 0) {
    return JNI_TRUE;
  }
#endif
#ifdef CLOCK_REALTIME
  *clockId = CLOCK_REALTIME;
  if (netty_unix_util_clock_gettime(*clockId, &ts) == 0) {
    return JNI_TRUE;
  }
#endif

  return JNI_FALSE;
}

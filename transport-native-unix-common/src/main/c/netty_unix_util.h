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

#ifndef NETTY_UNIX_UTIL_H_
#define NETTY_UNIX_UTIL_H_

#include <jni.h>
#include <stdint.h>
#include <time.h>
#include "netty_jni_util.h"


#if defined(__MACH__) && !defined(CLOCK_REALTIME)
#define NETTY_USE_MACH_INSTEAD_OF_CLOCK

typedef int clockid_t;

#ifndef CLOCK_MONOTONIC
#define CLOCK_MONOTONIC 1
#endif

#ifndef CLOCK_MONOTONIC_COARSE
#define CLOCK_MONOTONIC_COARSE 2
#endif

#endif /* __MACH__ */

/**
 * Get a clock which can be used to measure execution time.
 *
 * Returns true is a suitable clock was found.
 */
jboolean netty_unix_util_initialize_wait_clock(clockid_t* clockId);

/**
 * This will delegate to clock_gettime from time.h if the platform supports it.
 *
 * MacOS does not support clock_gettime.
 */
int netty_unix_util_clock_gettime(clockid_t clockId, struct timespec* tp);

/**
 * Calculate the number of nano seconds elapsed between begin and end.
 *
 * Returns the number of nano seconds.
 */
uint64_t netty_unix_util_timespec_elapsed_ns(const struct timespec* begin, const struct timespec* end);

/**
 * Subtract <pre>nanos</pre> nano seconds from a <pre>timespec</pre>.
 *
 * Returns true if there is underflow.
 */
jboolean netty_unix_util_timespec_subtract_ns(struct timespec* ts, uint64_t nanos);

#endif /* NETTY_UNIX_UTIL_H_ */

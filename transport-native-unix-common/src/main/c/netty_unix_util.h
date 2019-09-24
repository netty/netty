/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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


#define NETTY_BEGIN_MACRO     if (1) {
#define NETTY_END_MACRO       } else (void)(0)

#define NETTY_FIND_CLASS(E, C, N, R)                \
    NETTY_BEGIN_MACRO                               \
        C = (*(E))->FindClass((E), N);              \
        if (C == NULL) {                            \
            (*(E))->ExceptionClear((E));            \
            goto R;                                 \
        }                                           \
    NETTY_END_MACRO

#define NETTY_LOAD_CLASS(E, C, N, R)                \
    NETTY_BEGIN_MACRO                               \
        jclass _##C = (*(E))->FindClass((E), N);    \
        if (_##C == NULL) {                         \
            (*(E))->ExceptionClear((E));            \
            goto R;                                 \
        }                                           \
        C = (*(E))->NewGlobalRef((E), _##C);        \
        (*(E))->DeleteLocalRef((E), _##C);          \
        if (C == NULL) {                            \
            goto R;                                 \
        }                                           \
    NETTY_END_MACRO

#define NETTY_UNLOAD_CLASS(E, C)                    \
    NETTY_BEGIN_MACRO                               \
        if (C != NULL) {                            \
            (*(E))->DeleteGlobalRef((E), (C));      \
            C = NULL;                               \
        }                                           \
    NETTY_END_MACRO


#define NETTY_GET_METHOD(E, C, M, N, S, R)          \
    NETTY_BEGIN_MACRO                               \
        M = (*(E))->GetMethodID((E), C, N, S);      \
        if (M == NULL) {                            \
            goto R;                                 \
        }                                           \
    NETTY_END_MACRO

#define NETTY_GET_FIELD(E, C, F, N, S, R)           \
    NETTY_BEGIN_MACRO                               \
        F = (*(E))->GetFieldID((E), C, N, S);       \
        if (F == NULL) {                            \
            goto R;                                 \
        }                                           \
    NETTY_END_MACRO

#define NETTY_TRY_GET_FIELD(E, C, F, N, S)          \
    NETTY_BEGIN_MACRO                               \
        F = (*(E))->GetFieldID((E), C, N, S);       \
        if (F == NULL) {                            \
            (*(E))->ExceptionClear((E));            \
        }                                           \
    NETTY_END_MACRO

#define NETTY_PREPEND(P, S, N, R)                             \
    NETTY_BEGIN_MACRO                                         \
        if ((N = netty_unix_util_prepend(P, S)) == NULL) {    \
            goto R;                                           \
        }                                                     \
    NETTY_END_MACRO
/**
 * Return a new string (caller must free this string) which is equivalent to <pre>prefix + str</pre>.
 *
 * Caller must free the return value!
 */
char* netty_unix_util_prepend(const char* prefix, const char* str);

char* netty_unix_util_rstrstr(char* s1rbegin, const char* s1rend, const char* s2);

/**
 * The expected format of the library name is "lib<>$libraryName" where the <> portion is what we will return.
 * If status != JNI_ERR then the caller MUST call free on the return value.
 */
char* netty_unix_util_parse_package_prefix(const char* libraryPathName, const char* libraryName, jint* status);

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

/**
 * Return type is as defined in http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html#wp5833.
 */
jint netty_unix_util_register_natives(JNIEnv* env, const char* packagePrefix, const char* className, const JNINativeMethod* methods, jint numMethods);

void netty_unix_util_free_dynamic_methods_table(JNINativeMethod* dynamicMethods, jint fixedMethodTableSize, jint fullMethodTableSize);
void netty_unix_util_free_dynamic_name(char** dynamicName);

#endif /* NETTY_UNIX_UTIL_H_ */

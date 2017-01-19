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
#include <errno.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include <stddef.h>
#include <time.h>
#include <sys/event.h>
#include <sys/socket.h>
#include <sys/time.h>
#include "netty_unix_filedescriptor.h"
#include "netty_unix_socket.h"
#include "netty_unix_errors.h"
#include "netty_unix_util.h"
#include "netty_unix_limits.h"
#include "netty_kqueue_bsdsocket.h"
#include "netty_kqueue_eventarray.h"

clockid_t waitClockId = 0; // initialized by netty_unix_util_initialize_wait_clock

static jint netty_kqueue_native_kqueueCreate(JNIEnv* env, jclass clazz) {
    jint kq = kqueue();
    if (kq < 0) {
      netty_unix_errors_throwChannelExceptionErrorNo(env, "kqueue() failed: ", errno);
    }
    return kq;
}

static jint netty_kqueue_native_keventChangeSingleUserEvent(jint kqueueFd, struct kevent* userEvent) {
    int result, err;
    result = kevent(kqueueFd, userEvent, 1, NULL, 0, NULL);
    if (result < 0) {
        // https://www.freebsd.org/cgi/man.cgi?query=kqueue&sektion=2
        // When kevent() call fails with EINTR error, all changes in the changelist have been applied.
        return (err = errno) == EINTR ? 0 : -err;
    }

    return result;
}

static jint netty_kqueue_native_keventTriggerUserEvent(JNIEnv* env, jclass clazz, jint kqueueFd, jint ident) {
    struct kevent userEvent;
    EV_SET(&userEvent, ident, EVFILT_USER, 0, NOTE_TRIGGER | NOTE_FFNOP, 0, NULL);
    return netty_kqueue_native_keventChangeSingleUserEvent(kqueueFd, &userEvent);
}

static jint netty_kqueue_native_keventAddUserEvent(JNIEnv* env, jclass clazz, jint kqueueFd, jint ident) {
    struct kevent userEvent;
    EV_SET(&userEvent, ident, EVFILT_USER, EV_ADD | EV_ENABLE | EV_CLEAR, NOTE_FFNOP, 0, NULL);
    return netty_kqueue_native_keventChangeSingleUserEvent(kqueueFd, &userEvent);
}

static jint netty_kqueue_native_keventWait(JNIEnv* env, jclass clazz, jint kqueueFd, jlong changeListAddress, jint changeListLength,
                                           jlong eventListAddress, jint eventListLength, jint tvSec, jint tvNsec) {
    struct kevent* changeList = (struct kevent*) changeListAddress;
    struct kevent* eventList = (struct kevent*) eventListAddress;
    struct timespec beforeTs, nowTs, timeoutTs;
    int result, err;

    timeoutTs.tv_sec = tvSec;
    timeoutTs.tv_nsec = tvNsec;

    // Negatives = wait indefinitely, Zeros = poll (aka return immediately).
    if ((tvSec == 0 && tvNsec == 0) || tvSec < 0 || tvNsec < 0) {
        const struct timespec* fixedTs = (tvSec == 0 && tvNsec == 0) ? &timeoutTs : NULL;
        for (;;) {
            result = kevent(kqueueFd, changeList, changeListLength, eventList, eventListLength, fixedTs);
            if (result >= 0) {
                return result;
            }
            if ((err = errno) != EINTR) {
                return -err;
            }

            // https://www.freebsd.org/cgi/man.cgi?query=kqueue&sektion=2
            // When kevent() call fails with EINTR error, all changes in the changelist have been applied.
            changeListLength = 0;
        }
    }

    // Wait with a timeout value.
    netty_unix_util_clock_gettime(waitClockId, &beforeTs);
    for (;;) {
        result = kevent(kqueueFd, changeList, changeListLength, eventList, eventListLength, &timeoutTs);
        if (result >= 0) {
            return result;
        }
        if ((err = errno) != EINTR) {
            return -err;
        }

        netty_unix_util_clock_gettime(waitClockId, &nowTs);
        // beforeTs will store the time difference to check for overflow
        beforeTs.tv_sec = nowTs.tv_sec - beforeTs.tv_sec;
        beforeTs.tv_nsec = nowTs.tv_nsec - beforeTs.tv_nsec;
        // Now subtract the time difference
        timeoutTs.tv_sec -= beforeTs.tv_sec;
        timeoutTs.tv_nsec -= beforeTs.tv_nsec;
        if (beforeTs.tv_sec < 0 || beforeTs.tv_nsec < 0 || (timeoutTs.tv_sec <= 0 && timeoutTs.tv_nsec <= 0)) {
            return 0;
        }
        beforeTs = nowTs;
        // https://www.freebsd.org/cgi/man.cgi?query=kqueue&sektion=2
        // When kevent() call fails with EINTR error, all changes in the changelist have been applied.
        changeListLength = 0;
    }
}

static jint netty_kqueue_native_sizeofKEvent(JNIEnv* env, jclass clazz) {
    return sizeof(struct kevent);
}

static jint netty_kqueue_native_offsetofKEventIdent(JNIEnv* env, jclass clazz) {
    return offsetof(struct kevent, ident);
}

static jint netty_kqueue_native_offsetofKEventFlags(JNIEnv* env, jclass clazz) {
    return offsetof(struct kevent, flags);
}

static jint netty_kqueue_native_offsetofKEventFilter(JNIEnv* env, jclass clazz) {
    return offsetof(struct kevent, filter);
}

static jint netty_kqueue_native_offsetofKEventFFlags(JNIEnv* env, jclass clazz) {
    return offsetof(struct kevent, fflags);
}

static jint netty_kqueue_native_offsetofKeventData(JNIEnv* env, jclass clazz) {
    return offsetof(struct kevent, data);
}

static jshort netty_kqueue_native_evfiltRead(JNIEnv* env, jclass clazz) {
    return EVFILT_READ;
}

static jshort netty_kqueue_native_evfiltWrite(JNIEnv* env, jclass clazz) {
    return EVFILT_WRITE;
}

static jshort netty_kqueue_native_evfiltUser(JNIEnv* env, jclass clazz) {
    return EVFILT_USER;
}

static jshort netty_kqueue_native_evAdd(JNIEnv* env, jclass clazz) {
   return EV_ADD;
}

static jshort netty_kqueue_native_evEnable(JNIEnv* env, jclass clazz) {
   return EV_ENABLE;
}

static jshort netty_kqueue_native_evDisable(JNIEnv* env, jclass clazz) {
   return EV_DISABLE;
}

static jshort netty_kqueue_native_evDelete(JNIEnv* env, jclass clazz) {
   return EV_DELETE;
}

static jshort netty_kqueue_native_evClear(JNIEnv* env, jclass clazz) {
   return EV_CLEAR;
}

static jshort netty_kqueue_native_evEOF(JNIEnv* env, jclass clazz) {
   return EV_EOF;
}

static jshort netty_kqueue_native_evError(JNIEnv* env, jclass clazz) {
   return EV_ERROR;
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "evfiltRead", "()S", (void *) netty_kqueue_native_evfiltRead },
  { "evfiltWrite", "()S", (void *) netty_kqueue_native_evfiltWrite },
  { "evfiltUser", "()S", (void *) netty_kqueue_native_evfiltUser },
  { "evAdd", "()S", (void *) netty_kqueue_native_evAdd },
  { "evEnable", "()S", (void *) netty_kqueue_native_evEnable },
  { "evDisable", "()S", (void *) netty_kqueue_native_evDisable },
  { "evDelete", "()S", (void *) netty_kqueue_native_evDelete },
  { "evClear", "()S", (void *) netty_kqueue_native_evClear },
  { "evEOF", "()S", (void *) netty_kqueue_native_evEOF },
  { "evError", "()S", (void *) netty_kqueue_native_evError }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "kqueueCreate", "()I", (void *) netty_kqueue_native_kqueueCreate },
  { "keventTriggerUserEvent", "(II)I", (void *) netty_kqueue_native_keventTriggerUserEvent },
  { "keventAddUserEvent", "(II)I", (void *) netty_kqueue_native_keventAddUserEvent },
  { "keventWait", "(IJIJIII)I", (void *) netty_kqueue_native_keventWait },
  { "sizeofKEvent", "()I", (void *) netty_kqueue_native_sizeofKEvent },
  { "offsetofKEventIdent", "()I", (void *) netty_kqueue_native_offsetofKEventIdent },
  { "offsetofKEventFlags", "()I", (void *) netty_kqueue_native_offsetofKEventFlags },
  { "offsetofKEventFFlags", "()I", (void *) netty_kqueue_native_offsetofKEventFFlags },
  { "offsetofKEventFilter", "()I", (void *) netty_kqueue_native_offsetofKEventFilter },
  { "offsetofKeventData", "()I", (void *) netty_kqueue_native_offsetofKeventData }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);
// JNI Method Registration Table End

static jint netty_kqueue_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // We must register the statically referenced methods first!
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/kqueue/KQueueStaticallyReferencedJniMethods",
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }
    // Register the methods which are not referenced by static member variables
    if (netty_unix_util_register_natives(env, packagePrefix, "io/netty/channel/kqueue/Native", fixed_method_table, fixed_method_table_size) != 0) {
        return JNI_ERR;
    }
    // Load all c modules that we depend upon
    if (netty_unix_limits_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_unix_errors_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_unix_filedescriptor_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_unix_socket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_kqueue_bsdsocket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_kqueue_eventarray_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    // Initialize this module

    if (!netty_unix_util_initialize_wait_clock(&waitClockId)) {
      fprintf(stderr, "FATAL: could not find a clock for clock_gettime!\n");
      return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

static void netty_kqueue_native_JNI_OnUnLoad(JNIEnv* env) {
    netty_unix_limits_JNI_OnUnLoad(env);
    netty_unix_errors_JNI_OnUnLoad(env);
    netty_unix_filedescriptor_JNI_OnUnLoad(env);
    netty_unix_socket_JNI_OnUnLoad(env);
    netty_kqueue_bsdsocket_JNI_OnUnLoad(env);
    netty_kqueue_eventarray_JNI_OnUnLoad(env);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    Dl_info dlinfo;
    jint status = 0;
    // We need to use an address of a function that is uniquely part of this library, so choose a static
    // function. See https://github.com/netty/netty/issues/4840.
    if (!dladdr((void*) netty_kqueue_native_JNI_OnUnLoad, &dlinfo)) {
        fprintf(stderr, "FATAL: transport-native-kqueue JNI call to dladdr failed!\n");
        return JNI_ERR;
    }
    char* packagePrefix = netty_unix_util_parse_package_prefix(dlinfo.dli_fname, "netty-transport-native-kqueue", &status);
    if (status == JNI_ERR) {
        fprintf(stderr, "FATAL: transport-native-kqueue JNI encountered unexpected dlinfo.dli_fname: %s\n", dlinfo.dli_fname);
        return JNI_ERR;
    }

    jint ret = netty_kqueue_native_JNI_OnLoad(env, packagePrefix);

    if (packagePrefix != NULL) {
      free(packagePrefix);
      packagePrefix = NULL;
    }

    return ret;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    }
    netty_kqueue_native_JNI_OnUnLoad(env);
}

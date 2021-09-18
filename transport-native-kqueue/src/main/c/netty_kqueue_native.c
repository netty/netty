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

#include "netty_kqueue_bsdsocket.h"
#include "netty_kqueue_eventarray.h"
#include "netty_unix_buffer.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_limits.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"
#include "netty_unix.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/channel/kqueue/KQueueStaticallyReferencedJniMethods"
#define NATIVE_CLASSNAME "io/netty/channel/kqueue/Native"

// Currently only macOS supports EVFILT_SOCK, and it is currently only available in internal APIs.
// To make compiling easier we redefine the values here if they are not present.
#ifdef __APPLE__
#ifndef EVFILT_SOCK
#define EVFILT_SOCK -13
#endif /* EVFILT_SOCK */
#ifndef NOTE_CONNRESET
#define NOTE_CONNRESET 0x00000001
#endif /* NOTE_CONNRESET */
#ifndef NOTE_READCLOSED
#define NOTE_READCLOSED 0x00000002
#endif /* NOTE_READCLOSED */
#ifndef NOTE_DISCONNECTED
#define NOTE_DISCONNECTED 0x00001000
#endif /* NOTE_DISCONNECTED */
#ifndef CONNECT_RESUME_ON_READ_WRITE
#define CONNECT_RESUME_ON_READ_WRITE 0x1
#endif /* CONNECT_RESUME_ON_READ_WRITE */
#ifndef CONNECT_DATA_IDEMPOTENT
#define CONNECT_DATA_IDEMPOTENT 0x2
#endif /* CONNECT_DATA_IDEMPOTENT */
#else
#ifndef EVFILT_SOCK
#define EVFILT_SOCK 0 // Disabled
#endif /* EVFILT_SOCK */
#ifndef NOTE_CONNRESET
#define NOTE_CONNRESET 0
#endif /* NOTE_CONNRESET */
#ifndef NOTE_READCLOSED
#define NOTE_READCLOSED 0
#endif /* NOTE_READCLOSED */
#ifndef NOTE_DISCONNECTED
#define NOTE_DISCONNECTED 0
#endif /* NOTE_DISCONNECTED */
#ifndef CONNECT_RESUME_ON_READ_WRITE
#define CONNECT_RESUME_ON_READ_WRITE 0
#endif /* CONNECT_RESUME_ON_READ_WRITE */
#ifndef CONNECT_DATA_IDEMPOTENT
#define CONNECT_DATA_IDEMPOTENT 0
#endif /* CONNECT_DATA_IDEMPOTENT */
#endif /* __APPLE__ */

static clockid_t waitClockId = 0; // initialized by netty_unix_util_initialize_wait_clock
static const char* staticPackagePrefix = NULL;
static int register_unix_called = 0;

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

    if (tvSec == 0 && tvNsec == 0) {
        // Zeros = poll (aka return immediately).
        for (;;) {
            result = kevent(kqueueFd, changeList, changeListLength, eventList, eventListLength, &timeoutTs);
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
        if (netty_unix_util_timespec_subtract_ns(&timeoutTs,
              netty_unix_util_timespec_elapsed_ns(&beforeTs, &nowTs))) {
            return 0;
        }

        beforeTs = nowTs;
        // https://www.freebsd.org/cgi/man.cgi?query=kqueue&sektion=2
        // When kevent() call fails with EINTR error, all changes in the changelist have been applied.
        changeListLength = 0;
    }
}

static jint netty_kqueue_native_registerUnix(JNIEnv* env, jclass clazz) {
    register_unix_called = 1;
    return netty_unix_register(env, staticPackagePrefix);
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

static jshort netty_kqueue_native_evfiltSock(JNIEnv* env, jclass clazz) {
    return EVFILT_SOCK;
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

static jshort netty_kqueue_native_noteConnReset(JNIEnv* env, jclass clazz) {
   return NOTE_CONNRESET;
}

static jshort netty_kqueue_native_noteReadClosed(JNIEnv* env, jclass clazz) {
   return NOTE_READCLOSED;
}

static jshort netty_kqueue_native_noteDisconnected(JNIEnv* env, jclass clazz) {
   return NOTE_DISCONNECTED;
}

static jint netty_kqueue_bsdsocket_connectResumeOnReadWrite(JNIEnv *env) {
    return CONNECT_RESUME_ON_READ_WRITE;
}

static jint netty_kqueue_bsdsocket_connectDataIdempotent(JNIEnv *env) {
    return CONNECT_DATA_IDEMPOTENT;
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "evfiltRead", "()S", (void *) netty_kqueue_native_evfiltRead },
  { "evfiltWrite", "()S", (void *) netty_kqueue_native_evfiltWrite },
  { "evfiltUser", "()S", (void *) netty_kqueue_native_evfiltUser },
  { "evfiltSock", "()S", (void *) netty_kqueue_native_evfiltSock },
  { "evAdd", "()S", (void *) netty_kqueue_native_evAdd },
  { "evEnable", "()S", (void *) netty_kqueue_native_evEnable },
  { "evDisable", "()S", (void *) netty_kqueue_native_evDisable },
  { "evDelete", "()S", (void *) netty_kqueue_native_evDelete },
  { "evClear", "()S", (void *) netty_kqueue_native_evClear },
  { "evEOF", "()S", (void *) netty_kqueue_native_evEOF },
  { "evError", "()S", (void *) netty_kqueue_native_evError },
  { "noteReadClosed", "()S", (void *) netty_kqueue_native_noteReadClosed },
  { "noteConnReset", "()S", (void *) netty_kqueue_native_noteConnReset },
  { "noteDisconnected", "()S", (void *) netty_kqueue_native_noteDisconnected },
  { "connectResumeOnReadWrite", "()I", (void *) netty_kqueue_bsdsocket_connectResumeOnReadWrite },
  { "connectDataIdempotent", "()I", (void *) netty_kqueue_bsdsocket_connectDataIdempotent }
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
  { "offsetofKeventData", "()I", (void *) netty_kqueue_native_offsetofKeventData },
  { "registerUnix", "()I", (void *) netty_kqueue_native_registerUnix }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);
// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Native to reflect that.
static jint netty_kqueue_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    int bsdsocketOnLoadCalled = 0;
    int eventarrayOnLoadCalled = 0;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto error;
    }
    staticallyRegistered = 1;

    // Register the methods which are not referenced by static member variables
    if (netty_jni_util_register_natives(env, packagePrefix, NATIVE_CLASSNAME, fixed_method_table, fixed_method_table_size) != 0) {
        goto error;
    }
    nativeRegistered = 1;

    if (netty_kqueue_bsdsocket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    bsdsocketOnLoadCalled = 1;

    if (netty_kqueue_eventarray_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    eventarrayOnLoadCalled = 1;

    // Initialize this module

    if (!netty_unix_util_initialize_wait_clock(&waitClockId)) {
        fprintf(stderr, "FATAL: could not find a clock for clock_gettime!\n");
        fflush(stderr);
        goto error;
    }

    if (packagePrefix != NULL) {
        staticPackagePrefix = strdup(packagePrefix);
    }
    return NETTY_JNI_UTIL_JNI_VERSION;
error:
   if (staticallyRegistered == 1) {
        netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
   }
   if (nativeRegistered == 1) {
        netty_jni_util_unregister_natives(env, packagePrefix, NATIVE_CLASSNAME);
   }
   if (bsdsocketOnLoadCalled == 1) {
       netty_kqueue_bsdsocket_JNI_OnUnLoad(env, packagePrefix);
   }
   if (eventarrayOnLoadCalled == 1) {
       netty_kqueue_eventarray_JNI_OnUnLoad(env, packagePrefix);
   }
   return JNI_ERR;
}

static void netty_kqueue_native_JNI_OnUnload(JNIEnv* env) {
    netty_kqueue_bsdsocket_JNI_OnUnLoad(env, staticPackagePrefix);
    netty_kqueue_eventarray_JNI_OnUnLoad(env, staticPackagePrefix);

    if (register_unix_called == 1) {
        register_unix_called = 0;
        netty_unix_unregister(env, staticPackagePrefix);
    }

    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, NATIVE_CLASSNAME);

    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }
}

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_kqueue(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_kqueue", netty_kqueue_native_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_kqueue(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_kqueue_native_JNI_OnUnload);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_kqueue", netty_kqueue_native_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_kqueue_native_JNI_OnUnload);
}
#endif /* NETTY_BUILD_STATIC */

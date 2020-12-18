/*
 * Copyright 2020 The Netty Project
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
#include <jni.h>

#include "netty_unix_buffer.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_limits.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"

#include "internal/netty_unix_buffer_internal.h"
#include "internal/netty_unix_errors_internal.h"
#include "internal/netty_unix_filedescriptor_internal.h"
#include "internal/netty_unix_limits_internal.h"
#include "internal/netty_unix_socket_internal.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

static jint netty_unix_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int limitsOnLoadCalled = 0;
    int errorsOnLoadCalled = 0;
    int filedescriptorOnLoadCalled = 0;
    int socketOnLoadCalled = 0;
    int bufferOnLoadCalled = 0;

    // Load all c modules that we depend upon
    if (netty_unix_limits_internal_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    limitsOnLoadCalled = 1;

    if (netty_unix_errors_internal_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    errorsOnLoadCalled = 1;

    if (netty_unix_filedescriptor_internal_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    filedescriptorOnLoadCalled = 1;

    if (netty_unix_socket_internal_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    socketOnLoadCalled = 1;

    if (netty_unix_buffer_internal_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    bufferOnLoadCalled = 1;

    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    if (ret == JNI_ERR) {
        if (limitsOnLoadCalled == 1) {
            netty_unix_limits_internal_JNI_OnUnLoad(env, packagePrefix);
        }
        if (errorsOnLoadCalled == 1) {
            netty_unix_errors_internal_JNI_OnUnLoad(env, packagePrefix);
        }
        if (filedescriptorOnLoadCalled == 1) {
            netty_unix_filedescriptor_internal_JNI_OnUnLoad(env, packagePrefix);
        }
        if (socketOnLoadCalled == 1) {
            netty_unix_socket_internal_JNI_OnUnLoad(env, packagePrefix);
        }
        if (bufferOnLoadCalled == 1) {
            netty_unix_buffer_internal_JNI_OnUnLoad(env, packagePrefix);
        }
    }
    return ret;
}

static void netty_unix_native_JNI_OnUnload(JNIEnv* env, const char* packagePrefix) {
    netty_unix_limits_internal_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_errors_internal_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_filedescriptor_internal_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_socket_internal_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_buffer_internal_JNI_OnUnLoad(env, packagePrefix);
}

// Invoked by the JVM when statically linked

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_unix(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_unix", netty_unix_native_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_unix(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_unix_native_JNI_OnUnload);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_unix", netty_unix_native_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_unix_native_JNI_OnUnload);
}
#endif /* NETTY_BUILD_STATIC */

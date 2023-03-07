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
#include "netty_unix_jni.h"
#include "netty_unix.h"
#include "netty_unix_buffer.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_limits.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"
#include <stddef.h>

#define STATICALLY_CLASSNAME "io/netty/channel/unix/NativeStaticallyReferencedJniMethods"

static jint netty_unix_cmsghdrOffsetofCmsgLen(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_len);
}

static jint netty_unix_cmsghdrOffsetofCmsgLevel(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_level);
}

static jint netty_unix_cmsghdrOffsetofCmsgType(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_type);
}

static jint netty_unix_sizeofSizeT(JNIEnv* env, jclass clazz) {
    return sizeof(size_t);
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "sizeofSizeT", "()I", (void *) netty_unix_sizeofSizeT },
  { "cmsghdrOffsetofCmsgLen", "()I", (void *) netty_unix_cmsghdrOffsetofCmsgLen },
  { "cmsghdrOffsetofCmsgLevel", "()I", (void *) netty_unix_cmsghdrOffsetofCmsgLevel },
  { "cmsghdrOffsetofCmsgType", "()I", (void *) netty_unix_cmsghdrOffsetofCmsgType }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Unix to reflect that.
jint netty_unix_register(JNIEnv* env, const char* packagePrefix) {
    int limitsOnLoadCalled = 0;
    int errorsOnLoadCalled = 0;
    int filedescriptorOnLoadCalled = 0;
    int socketOnLoadCalled = 0;
    int bufferOnLoadCalled = 0;


    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto error;
    }

    // Load all c modules that we depend upon
    if (netty_unix_limits_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    limitsOnLoadCalled = 1;

    if (netty_unix_errors_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    errorsOnLoadCalled = 1;

    if (netty_unix_filedescriptor_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    filedescriptorOnLoadCalled = 1;

    if (netty_unix_socket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    socketOnLoadCalled = 1;

    if (netty_unix_buffer_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto error;
    }
    bufferOnLoadCalled = 1;

    return NETTY_JNI_UTIL_JNI_VERSION;
error:
   if (limitsOnLoadCalled == 1) {
       netty_unix_limits_JNI_OnUnLoad(env, packagePrefix);
   }
   if (errorsOnLoadCalled == 1) {
       netty_unix_errors_JNI_OnUnLoad(env, packagePrefix);
   }
   if (filedescriptorOnLoadCalled == 1) {
       netty_unix_filedescriptor_JNI_OnUnLoad(env, packagePrefix);
   }
   if (socketOnLoadCalled == 1) {
       netty_unix_socket_JNI_OnUnLoad(env, packagePrefix);
   }
   if (bufferOnLoadCalled == 1) {
      netty_unix_buffer_JNI_OnUnLoad(env, packagePrefix);
   }
   return JNI_ERR;
}

void netty_unix_unregister(JNIEnv* env, const char* packagePrefix) {
    netty_unix_limits_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_errors_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_filedescriptor_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_socket_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_buffer_JNI_OnUnLoad(env, packagePrefix);
}


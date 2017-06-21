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
#include <limits.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/un.h>
#include "netty_unix_jni.h"
#include "netty_unix_limits.h"
#include "netty_unix_util.h"

// Define IOV_MAX if not found to limit the iov size on writev calls
// See https://github.com/netty/netty/issues/2647
#ifndef IOV_MAX
#define IOV_MAX 1024
#endif /* IOV_MAX */

// Define UIO_MAXIOV if not found
#ifndef UIO_MAXIOV
#define UIO_MAXIOV 1024
#endif /* UIO_MAXIOV */

// JNI Registered Methods Begin
static jlong netty_unix_limits_ssizeMax(JNIEnv* env, jclass clazz) {
    return SSIZE_MAX;
}

static jint netty_unix_limits_iovMax(JNIEnv* env, jclass clazz) {
    return IOV_MAX;
}

static jint netty_unix_limits_uioMaxIov(JNIEnv* env, jclass clazz) {
    return UIO_MAXIOV;
}

static jint netty_unix_limits_sizeOfjlong(JNIEnv* env, jclass clazz) {
    return sizeof(jlong);
}

static jint netty_unix_limits_udsSunPathSize(JNIEnv* env, jclass clazz) {
    struct sockaddr_un udsAddr;
    return sizeof(udsAddr.sun_path) / sizeof(udsAddr.sun_path[0]);
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "ssizeMax", "()J", (void *) netty_unix_limits_ssizeMax },
  { "iovMax", "()I", (void *) netty_unix_limits_iovMax },
  { "uioMaxIov", "()I", (void *) netty_unix_limits_uioMaxIov },
  { "sizeOfjlong", "()I", (void *) netty_unix_limits_sizeOfjlong },
  { "udsSunPathSize", "()I", (void *) netty_unix_limits_udsSunPathSize }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
// JNI Method Registration Table End

jint netty_unix_limits_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // We must register the statically referenced methods first!
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/unix/LimitsStaticallyReferencedJniMethods",
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }

    return NETTY_JNI_VERSION;
}

void netty_unix_limits_JNI_OnUnLoad(JNIEnv* env) { }

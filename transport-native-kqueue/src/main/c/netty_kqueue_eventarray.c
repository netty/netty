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
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>

#include "netty_kqueue_eventarray.h"
#include "netty_unix_errors.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"

#define EVENT_ARRAY_CLASSNAME "io/netty/channel/kqueue/KQueueEventArray"

static void netty_kqueue_eventarray_evSet(JNIEnv* env, jclass clzz, jlong keventAddress, jint ident, jshort filter, jshort flags, jint fflags) {
    EV_SET((struct kevent*) keventAddress, ident, filter, flags, fflags, 0, NULL);
}

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "evSet", "(JISSI)V", (void *) netty_kqueue_eventarray_evSet }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

// JNI Method Registration Table End

jint netty_kqueue_eventarray_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            EVENT_ARRAY_CLASSNAME,
            fixed_method_table,
            fixed_method_table_size) != 0) {
        return JNI_ERR;
    }
    return NETTY_JNI_UTIL_JNI_VERSION;
}

void netty_kqueue_eventarray_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    netty_jni_util_unregister_natives(env, packagePrefix, EVENT_ARRAY_CLASSNAME);
}

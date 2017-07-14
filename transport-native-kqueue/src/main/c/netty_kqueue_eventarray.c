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
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>

#include "netty_kqueue_eventarray.h"
#include "netty_unix_errors.h"
#include "netty_unix_jni.h"
#include "netty_unix_util.h"

jfieldID kqueueJniPtrFieldId = NULL;

static void netty_kqueue_eventarray_evSet(JNIEnv* env, jclass clzz, jlong keventAddress, jobject channel, jint ident, jshort filter, jshort flags, jint fflags) {
    // Create a global pointer, cast it as a long, and retain it in java to re-use and free later.
    jlong jniSelfPtr = (*env)->GetLongField(env, channel, kqueueJniPtrFieldId);
    if (jniSelfPtr == 0) {
        jniSelfPtr = (jlong) (*env)->NewGlobalRef(env, channel);
        (*env)->SetLongField(env, channel, kqueueJniPtrFieldId, jniSelfPtr);
    } else if ((flags & EV_DELETE) != 0) {
        // If the event is deleted, make sure it no longer has a reference to the jniSelfPtr because it shouldn't be used after this point.
        jniSelfPtr = 0;
    }
    EV_SET((struct kevent*) keventAddress, ident, filter, flags, fflags, 0, (jobject) jniSelfPtr);
}

static jobject netty_kqueue_eventarray_getChannel(JNIEnv* env, jclass clazz, jlong keventAddress) {
    struct kevent* event = (struct kevent*) keventAddress;
    return event->udata == NULL ? NULL : (jobject) event->udata;
}

static void netty_kqueue_eventarray_deleteGlobalRefs(JNIEnv* env, jclass clazz, jlong channelAddressStart, jlong channelAddressEnd) {
    // Iterate over an array of longs, which are really pointers to the jobject NewGlobalRef created above in evSet
    // and delete each one. The field has already been set to 0 in java.
    jlong* itr = (jlong*) channelAddressStart;
    const jlong* end = (jlong*) channelAddressEnd;
    for (; itr != end; ++itr) {
        (*env)->DeleteGlobalRef(env, (jobject) *itr);
    }
}

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "deleteGlobalRefs", "(JJ)V", (void *) netty_kqueue_eventarray_deleteGlobalRefs }
  // "evSet" has a dynamic signature
  // "getChannel" has a dynamic signature
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * dynamicMethodsTableSize());
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/kqueue/AbstractKQueueChannel;ISSI)V");
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    dynamicMethod->name = "evSet";
    dynamicMethod->signature = netty_unix_util_prepend("(JL", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_kqueue_eventarray_evSet;
    free(dynamicTypeName);

    ++dynamicMethod;
    dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/kqueue/AbstractKQueueChannel;");
    dynamicMethod->name = "getChannel";
    dynamicMethod->signature = netty_unix_util_prepend("(J)L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_kqueue_eventarray_getChannel;
    free(dynamicTypeName);
    return dynamicMethods;
}

static void freeDynamicMethodsTable(JNINativeMethod* dynamicMethods) {
    jint fullMethodTableSize = dynamicMethodsTableSize();
    jint i = fixed_method_table_size;
    for (; i < fullMethodTableSize; ++i) {
        free(dynamicMethods[i].signature);
    }
    free(dynamicMethods);
}
// JNI Method Registration Table End

jint netty_kqueue_eventarray_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/kqueue/KQueueEventArray",
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;

    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/kqueue/AbstractKQueueChannel");
    jclass kqueueChannelCls = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (kqueueChannelCls == NULL) {
        return JNI_ERR;
    }

    kqueueJniPtrFieldId = (*env)->GetFieldID(env, kqueueChannelCls, "jniSelfPtr", "J");
    if (kqueueJniPtrFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: AbstractKQueueChannel.jniSelfPtr");
        return JNI_ERR;
    }

    return NETTY_JNI_VERSION;
}

void netty_kqueue_eventarray_JNI_OnUnLoad(JNIEnv* env) {
}

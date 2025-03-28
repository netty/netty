/*
 * Copyright 2018 The Netty Project
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
#include "netty_unix_util.h"
#include "netty_unix_buffer.h"
#include "netty_jni_util.h"

#define BUFFER_CLASSNAME "io/netty/channel/unix/Buffer"

// JNI Registered Methods Begin
static jlong netty_unix_buffer_memoryAddress0(JNIEnv* env, jclass clazz, jobject buffer) {
    return (jlong) (*env)->GetDirectBufferAddress(env, buffer);
}

static jint netty_unix_buffer_addressSize0(JNIEnv* env, jclass clazz) {
   return (jint) sizeof(int*);
}

static jobject netty_unix_buffer_wrapMemoryAddress(JNIEnv* env, jclass clazz, jlong address, jint capacity) {
    return (*env)->NewDirectByteBuffer(env, (void*) address, capacity);
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "memoryAddress0", "(Ljava/nio/ByteBuffer;)J", (void *) netty_unix_buffer_memoryAddress0 },
  { "addressSize0", "()I", (void *) netty_unix_buffer_addressSize0 },
  { "wrapMemoryAddress","(JI)Ljava/nio/ByteBuffer;", (void *) netty_unix_buffer_wrapMemoryAddress },
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Unix to reflect that.
jint netty_unix_buffer_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            BUFFER_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }

    return NETTY_JNI_UTIL_JNI_VERSION;
}

void netty_unix_buffer_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
     netty_jni_util_unregister_natives(env, packagePrefix, BUFFER_CLASSNAME);
}

/*
 * Copyright 2014 The Netty Project
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
#include <jni.h>

// Exported JNI Methods
jint Java_io_netty_channel_unix_FileDescriptor_close(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_FileDescriptor_open(JNIEnv* env, jclass clazz, jstring path);

jlong Java_io_netty_channel_unix_FileDescriptor_newPipe(JNIEnv* env, jclass clazz);

jint Java_io_netty_channel_unix_FileDescriptor_write(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_unix_FileDescriptor_writeAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
jlong Java_io_netty_channel_unix_FileDescriptor_writev(JNIEnv* env, jclass clazz, jint fd, jobjectArray buffers, jint offset, jint length);
jlong Java_io_netty_channel_unix_FileDescriptor_writevAddresses(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length);

jint Java_io_netty_channel_unix_FileDescriptor_read(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_unix_FileDescriptor_readAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit);

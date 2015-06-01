/*
 * Copyright 2015 The Netty Project
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

void Java_io_netty_channel_libaio_DirectFileDescriptorController_close(JNIEnv* env, jclass clazz, jint fd);
int Java_io_netty_channel_libaio_DirectFileDescriptorController_open(JNIEnv* env, jclass clazz, jstring path, jboolean direct);
jobject Java_io_netty_channel_libaio_DirectFileDescriptorController_newContext(JNIEnv * env, jclass clazz, jint queueSize);
void Java_io_netty_channel_libaio_DirectFileDescriptorController_deleteContext(JNIEnv* env, jclass clazz, jobject pointer);
jboolean Java_io_netty_channel_libaio_DirectFileDescriptorController_submitWrite(JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferWrite, jobject callback);
jboolean Java_io_netty_channel_libaio_DirectFileDescriptorController_submitRead(JNIEnv * env, jclass clazz, jint fileHandle, jobject contextPointer, jlong position, jint size, jobject bufferWrite, jobject callback);
jobject Java_io_netty_channel_libaio_DirectFileDescriptorController_newAlignedBuffer(JNIEnv * env, jclass clazz, jint size, jint alignment);
void Java_io_netty_channel_libaio_DirectFileDescriptorController_freeBuffer(JNIEnv * env, jclass clazz, jobject buffer);
jint Java_io_netty_channel_libaio_DirectFileDescriptorController_poll(JNIEnv * env, jobject clazz, jobject libaioContext, jobjectArray callbacks, jint min, jint max);

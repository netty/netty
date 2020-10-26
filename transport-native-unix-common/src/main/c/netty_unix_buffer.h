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
#ifndef NETTY_UNIX_BUFFER_H_
#define NETTY_UNIX_BUFFER_H_

#include <jni.h>

// JNI initialization hooks. Users of this file are responsible for calling these in the JNI_OnLoad and JNI_OnUnload methods.
jint netty_unix_buffer_JNI_OnLoad(JNIEnv* env, const char* packagePrefix);
void netty_unix_buffer_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix);

#endif /* NETTY_UNIX_BUFFER_H_ */

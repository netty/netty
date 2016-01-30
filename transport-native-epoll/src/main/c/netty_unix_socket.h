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
#ifndef NETTY_UNIX_SOCKET_H_
#define NETTY_UNIX_SOCKET_H_

#include <sys/socket.h>
#include <jni.h>

// External C methods
int netty_unix_socket_initSockaddr(JNIEnv* env, jbyteArray address, jint scopeId, jint jport, const struct sockaddr_storage* addr);
int netty_unix_socket_getOption(JNIEnv* env, jint fd, int level, int optname, void* optval, socklen_t optlen);
int netty_unix_socket_setOption(JNIEnv* env, jint fd, int level, int optname, const void* optval, socklen_t len);

// JNI initialization hooks. Users of this file are responsible for calling these in the JNI_OnLoad and JNI_OnUnload methods.
jint netty_unix_socket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix);
void netty_unix_socket_JNI_OnUnLoad(JNIEnv* env);

#endif /* NETTY_UNIX_SOCKET_H_ */

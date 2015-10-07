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

// Exported JNI Methods
jint Java_io_netty_channel_unix_Socket_shutdown(JNIEnv* env, jclass clazz, jint fd, jboolean read, jboolean write);

jint Java_io_netty_channel_unix_Socket_newSocketStreamFd(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Socket_newSocketDgramFd(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_unix_Socket_newSocketDomainFd(JNIEnv* env, jclass clazz);

jint Java_io_netty_channel_unix_Socket_sendTo(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_unix_Socket_sendToAddress(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_unix_Socket_sendToAddresses(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length, jbyteArray address, jint scopeId, jint port);

jobject Java_io_netty_channel_unix_Socket_recvFrom(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jobject Java_io_netty_channel_unix_Socket_recvFromAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit);

jint Java_io_netty_channel_unix_Socket_bind(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_unix_Socket_bindDomainSocket(JNIEnv* env, jclass clazz, jint fd, jstring address);
jint Java_io_netty_channel_unix_Socket_listen(JNIEnv* env, jclass clazz, jint fd, jint backlog);
jint Java_io_netty_channel_unix_Socket_accept(JNIEnv* env, jclass clazz, jint fd, jbyteArray acceptedAddress);

jint Java_io_netty_channel_unix_Socket_connect(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_unix_Socket_connectDomainSocket(JNIEnv* env, jclass clazz, jint fd, jstring address);
jint Java_io_netty_channel_unix_Socket_finishConnect(JNIEnv* env, jclass clazz, jint fd);

jbyteArray Java_io_netty_channel_unix_Socket_remoteAddress(JNIEnv* env, jclass clazz, jint fd);
jbyteArray Java_io_netty_channel_unix_Socket_localAddress(JNIEnv* env, jclass clazz, jint fd);

void Java_io_netty_channel_unix_Socket_setTcpNoDelay(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_unix_Socket_setReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_unix_Socket_setSendBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_unix_Socket_setKeepAlive(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_unix_Socket_setTcpCork(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_unix_Socket_setSoLinger(JNIEnv* env, jclass clazz, jint fd, jint optval);

jint Java_io_netty_channel_unix_Socket_isTcpNoDelay(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_Socket_getReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_Socket_getSendBufferSize(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_Socket_isTcpCork(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_Socket_getSoLinger(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_unix_Socket_getSoError(JNIEnv* env, jclass clazz, jint fd);

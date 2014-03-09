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


#define EPOLL_READ 0x01
#define EPOLL_WRITE 0x02
#define EPOLL_ACCEPT 0x04
#define EPOLL_RDHUP 0x08

jint Java_io_netty_channel_epoll_Native_eventFd(JNIEnv * env, jclass clazz);
void Java_io_netty_channel_epoll_Native_eventFdWrite(JNIEnv * env, jclass clazz, jint fd, jlong value);
void Java_io_netty_channel_epoll_Native_eventFdRead(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_epollCreate(JNIEnv * env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollWait(JNIEnv * env, jclass clazz, jint efd, jlongArray events, jint timeout);
void Java_io_netty_channel_epoll_Native_epollCtlAdd(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id);
void Java_io_netty_channel_epoll_Native_epollCtlMod(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id);
void  Java_io_netty_channel_epoll_Native_epollCtlDel(JNIEnv * env, jclass clazz, jint efd, jint fd);
jint Java_io_netty_channel_epoll_Native_write(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_epoll_Native_writeAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
jlong Java_io_netty_channel_epoll_Native_writev(JNIEnv * env, jclass clazz, jint fd, jobjectArray buffers, jint offset, jint length);
jint Java_io_netty_channel_epoll_Native_read(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_epoll_Native_readAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
void JNICALL Java_io_netty_channel_epoll_Native_close(JNIEnv * env, jclass clazz, jint fd);
void Java_io_netty_channel_epoll_Native_shutdown(JNIEnv * env, jclass clazz, jint fd, jboolean read, jboolean write);
jint Java_io_netty_channel_epoll_Native_socket(JNIEnv * env, jclass clazz);
void Java_io_netty_channel_epoll_Native_bind(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
void Java_io_netty_channel_epoll_Native_listen(JNIEnv * env, jclass clazz, jint fd, jint backlog);
jboolean Java_io_netty_channel_epoll_Native_connect(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
jboolean Java_io_netty_channel_epoll_Native_finishConnect(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_accept(JNIEnv * env, jclass clazz, jint fd);
jlong Java_io_netty_channel_epoll_Native_sendfile(JNIEnv *env, jclass clazz, jint fd, jobject fileRegion, jlong off, jlong len);
jobject Java_io_netty_channel_epoll_Native_remoteAddress(JNIEnv * env, jclass clazz, jint fd);
jobject Java_io_netty_channel_epoll_Native_localAddress(JNIEnv * env, jclass clazz, jint fd);
void Java_io_netty_channel_epoll_Native_setReuseAddress(JNIEnv * env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpNoDelay(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setReceiveBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setSendBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setKeepAlive(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpCork(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setSoLinger(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTrafficClass(JNIEnv *env, jclass clazz, jint fd, jint optval);
jint Java_io_netty_channel_epoll_Native_isReuseAddresss(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isTcpNoDelay(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getReceiveBufferSize(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getSendBufferSize(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isTcpCork(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getSoLinger(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTrafficClass(JNIEnv *env, jclass clazz, jint fd);

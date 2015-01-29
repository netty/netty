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
#include <limits.h>

#define EPOLL_READ 0x01
#define EPOLL_WRITE 0x02
#define EPOLL_ACCEPT 0x04
#define EPOLL_RDHUP 0x08

// Define SO_REUSEPORT if not found to fix build issues.
// See https://github.com/netty/netty/issues/2558
#ifndef SO_REUSEPORT
#define SO_REUSEPORT 15
#endif /* SO_REUSEPORT */

// Define IOV_MAX if not found to limit the iov size on writev calls
// See https://github.com/netty/netty/issues/2647
#ifndef IOV_MAX
#define IOV_MAX 1024
#endif /* IOV_MAX */

// Define UIO_MAXIOV if not found
#ifndef UIO_MAXIOV
#define UIO_MAXIOV 1024
#endif /* UIO_MAXIOV */

jint Java_io_netty_channel_epoll_Native_eventFd(JNIEnv * env, jclass clazz);
void Java_io_netty_channel_epoll_Native_eventFdWrite(JNIEnv * env, jclass clazz, jint fd, jlong value);
void Java_io_netty_channel_epoll_Native_eventFdRead(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_epollCreate(JNIEnv * env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollWait(JNIEnv * env, jclass clazz, jint efd, jlongArray events, jint timeout);
void Java_io_netty_channel_epoll_Native_epollCtlAdd(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id);
void Java_io_netty_channel_epoll_Native_epollCtlMod(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id);
void  Java_io_netty_channel_epoll_Native_epollCtlDel(JNIEnv * env, jclass clazz, jint efd, jint fd);
jint Java_io_netty_channel_epoll_Native_write0(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_epoll_Native_writeAddress0(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
jlong Java_io_netty_channel_epoll_Native_writev0(JNIEnv * env, jclass clazz, jint fd, jobjectArray buffers, jint offset, jint length);
jlong Java_io_netty_channel_epoll_Native_writevAddresses0(JNIEnv * env, jclass clazz, jint fd, jlong memoryAddress, jint length);
jint Java_io_netty_channel_epoll_Native_sendTo(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_epoll_Native_sendToAddress(JNIEnv * env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_epoll_Native_sendToAddresses(JNIEnv * env, jclass clazz, jint fd, jlong memoryAddress, jint length, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_epoll_Native_sendmmsg(JNIEnv * env, jclass clazz, jint fd, jobjectArray packets, jint offset, jint len);

jint Java_io_netty_channel_epoll_Native_read0(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jint Java_io_netty_channel_epoll_Native_readAddress0(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
jobject Java_io_netty_channel_epoll_Native_recvFrom(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit);
jobject Java_io_netty_channel_epoll_Native_recvFromAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit);
jint Java_io_netty_channel_epoll_Native_close0(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_shutdown0(JNIEnv * env, jclass clazz, jint fd, jboolean read, jboolean write);
jint Java_io_netty_channel_epoll_Native_socketStream(JNIEnv * env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_socketDgram(JNIEnv * env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_socketDomain(JNIEnv * env, jclass clazz);

jint Java_io_netty_channel_epoll_Native_bind(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_epoll_Native_bindDomainSocket(JNIEnv * env, jclass clazz, jint fd, jstring address);
jint Java_io_netty_channel_epoll_Native_recvFd0(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_sendFd0(JNIEnv* env, jclass clazz, jint socketFd, jint fd);

jint Java_io_netty_channel_epoll_Native_listen0(JNIEnv * env, jclass clazz, jint fd, jint backlog);
jint Java_io_netty_channel_epoll_Native_connect(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port);
jint Java_io_netty_channel_epoll_Native_connectDomainSocket(JNIEnv * env, jclass clazz, jint fd, jstring address);
jint Java_io_netty_channel_epoll_Native_finishConnect0(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_accept0(JNIEnv * env, jclass clazz, jint fd);
jlong Java_io_netty_channel_epoll_Native_sendfile0(JNIEnv *env, jclass clazz, jint fd, jobject fileRegion, jlong base_off, jlong off, jlong len);
jbyteArray Java_io_netty_channel_epoll_Native_remoteAddress0(JNIEnv * env, jclass clazz, jint fd);
jbyteArray Java_io_netty_channel_epoll_Native_localAddress0(JNIEnv * env, jclass clazz, jint fd);
void Java_io_netty_channel_epoll_Native_setReuseAddress(JNIEnv * env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setReusePort(JNIEnv * env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpNoDelay(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setReceiveBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setSendBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setKeepAlive(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpCork(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setSoLinger(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTrafficClass(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setBroadcast(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepIdle(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepIntvl(JNIEnv *env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepCnt(JNIEnv *env, jclass clazz, jint fd, jint optval);

jint Java_io_netty_channel_epoll_Native_isReuseAddresss(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isReusePort(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isTcpNoDelay(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getReceiveBufferSize(JNIEnv * env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getSendBufferSize(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isTcpCork(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getSoLinger(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTrafficClass(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isBroadcast(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepIdle(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepIntvl(JNIEnv *env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepCnt(JNIEnv *env, jclass clazz, jint fd);

jstring Java_io_netty_channel_epoll_Native_kernelVersion(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_iovMax(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_uioMaxIov(JNIEnv *env, jclass clazz);
jboolean Java_io_netty_channel_epoll_Native_isSupportingSendmmsg(JNIEnv *env, jclass clazz);

jint Java_io_netty_channel_epoll_Native_errnoEBADF(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_errnoEPIPE(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_errnoEAGAIN(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_errnoEWOULDBLOCK(JNIEnv *env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_errnoEINPROGRESS(JNIEnv *env, jclass clazz);
jstring Java_io_netty_channel_epoll_Native_strError(JNIEnv *env, jclass clazz, jint err);

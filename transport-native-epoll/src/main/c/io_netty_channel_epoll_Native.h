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

jint Java_io_netty_channel_epoll_Native_eventFd(JNIEnv* env, jclass clazz);
void Java_io_netty_channel_epoll_Native_eventFdWrite(JNIEnv* env, jclass clazz, jint fd, jlong value);
void Java_io_netty_channel_epoll_Native_eventFdRead(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_epollCreate(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollWait0(JNIEnv* env, jclass clazz, jint efd, jlong address, jint length, jint timeout);
jint Java_io_netty_channel_epoll_Native_epollCtlAdd0(JNIEnv* env, jclass clazz, jint efd, jint fd, jint flags);
jint Java_io_netty_channel_epoll_Native_epollCtlMod0(JNIEnv* env, jclass clazz, jint efd, jint fd, jint flags);
jint Java_io_netty_channel_epoll_Native_epollCtlDel0(JNIEnv* env, jclass clazz, jint efd, jint fd);

jint Java_io_netty_channel_epoll_Native_sendmmsg(JNIEnv* env, jclass clazz, jint fd, jobjectArray packets, jint offset, jint len);
jint Java_io_netty_channel_epoll_Native_recvFd0(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_sendFd0(JNIEnv* env, jclass clazz, jint socketFd, jint fd);
jlong Java_io_netty_channel_epoll_Native_sendfile0(JNIEnv* env, jclass clazz, jint fd, jobject fileRegion, jlong base_off, jlong off, jlong len);

void Java_io_netty_channel_epoll_Native_setReuseAddress(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setReusePort(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpFastopen(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTrafficClass(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setBroadcast(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd, jint optval);
void Java_io_netty_channel_epoll_Native_setIpFreeBind(JNIEnv* env, jclass clazz, jint fd, jint optval);

jint Java_io_netty_channel_epoll_Native_isReuseAddresss(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isReusePort(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTrafficClass(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isBroadcast(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_getTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd);
jint Java_io_netty_channel_epoll_Native_isIpFreeBind(JNIEnv* env, jclass clazz, jint fd);
void Java_io_netty_channel_epoll_Native_tcpInfo0(JNIEnv* env, jclass clazz, jint fd, jintArray array);

jstring Java_io_netty_channel_epoll_Native_kernelVersion(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_iovMax(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_uioMaxIov(JNIEnv* env, jclass clazz);
jlong Java_io_netty_channel_epoll_Native_ssizeMax(JNIEnv* env, jclass clazz);
jboolean Java_io_netty_channel_epoll_Native_isSupportingSendmmsg(JNIEnv* env, jclass clazz);
jboolean Java_io_netty_channel_epoll_Native_isSupportingTcpFastopen(JNIEnv* env, jclass clazz);

jint Java_io_netty_channel_epoll_Native_epollin(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollout(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollrdhup(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollet(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_epollerr(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_sizeofEpollEvent(JNIEnv* env, jclass clazz);
jint Java_io_netty_channel_epoll_Native_offsetofEpollData(JNIEnv* env, jclass clazz);

jint Java_io_netty_channel_epoll_Native_splice0(JNIEnv* env, jclass clazz, jint fd, jlong offIn, jint fdOut, jlong offOut, jlong len);

jint Java_io_netty_channel_epoll_Native_tcpMd5SigMaxKeyLen(JNIEnv* env, jclass clazz);
void Java_io_netty_channel_epoll_Native_setTcpMd5Sig0(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jbyteArray key);

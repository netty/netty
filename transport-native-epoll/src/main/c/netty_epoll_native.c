/*
 * Copyright 2013 The Netty Project
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
#define _GNU_SOURCE
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/sendfile.h>
#include <sys/un.h>
#include <linux/tcp.h> // TCP_NOTSENT_LOWAT is a linux specific define
#include <netinet/in.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include <stddef.h>
#include <limits.h>
#include <inttypes.h>
#include <link.h>
#include <time.h>
#include "netty_unix_filedescriptor.h"
#include "netty_unix_socket.h"
#include "netty_unix_errors.h"
#include "netty_unix_util.h"

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

// TCP_NOTSENT_LOWAT is defined in linux 3.12. We define this here so older kernels can compile.
#ifndef TCP_NOTSENT_LOWAT
#define TCP_NOTSENT_LOWAT 25
#endif

// TCP_FASTOPEN is defined in linux 3.7. We define this here so older kernels can compile.
#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

/**
 * On older Linux kernels, epoll can't handle timeout
 * values bigger than (LONG_MAX - 999ULL)/HZ.
 *
 * See:
 *   - https://github.com/libevent/libevent/blob/master/epoll.c#L138
 *   - http://cvs.schmorp.de/libev/ev_epoll.c?revision=1.68&view=markup
 */
#define MAX_EPOLL_TIMEOUT_MSEC (35*60*1000)

// optional
extern int epoll_create1(int flags) __attribute__((weak));

#ifdef IO_NETTY_SENDMMSG_NOT_FOUND
extern int sendmmsg(int sockfd, struct mmsghdr* msgvec, unsigned int vlen, unsigned int flags) __attribute__((weak));

#ifndef __USE_GNU
struct mmsghdr {
    struct msghdr msg_hdr;  /* Message header */
    unsigned int  msg_len;  /* Number of bytes transmitted */
};
#endif
#endif

// Those are initialized in the init(...) method and cached for performance reasons
jfieldID fileChannelFieldId = NULL;
jfieldID transferedFieldId = NULL;
jfieldID fdFieldId = NULL;
jfieldID fileDescriptorFieldId = NULL;

jfieldID packetAddrFieldId = NULL;
jfieldID packetScopeIdFieldId = NULL;
jfieldID packetPortFieldId = NULL;
jfieldID packetMemoryAddressFieldId = NULL;
jfieldID packetCountFieldId = NULL;

clockid_t epollWaitClock = 0; // initialized in initializeEpollWaitClock

// util methods
static jboolean initializeEpollWaitClock() {
  struct timespec ts;
  // First try to get a monotonic clock, as we effectively measure execution time and don't want the underlying clock
  // moving unexpectedly/abruptly.
  #ifdef CLOCK_MONOTONIC_COARSE
  epollWaitClock = CLOCK_MONOTONIC_COARSE;
  if (clock_gettime(epollWaitClock, &ts) != EINVAL) {
    return JNI_TRUE;
  }
  #endif
  #ifdef CLOCK_MONOTONIC_RAW
  epollWaitClock = CLOCK_MONOTONIC_RAW;
  if (clock_gettime(epollWaitClock, &ts) != EINVAL) {
    return JNI_TRUE;
  }
  #endif
  #ifdef CLOCK_MONOTONIC
  epollWaitClock = CLOCK_MONOTONIC;
  if (clock_gettime(epollWaitClock, &ts) != EINVAL) {
    return JNI_TRUE;
  }
  #endif

  // Fallback to realtime ... in this case we are subject to clock movements on the system.
  #ifdef CLOCK_REALTIME_COARSE
  epollWaitClock = CLOCK_REALTIME_COARSE;
  if (clock_gettime(epollWaitClock, &ts) != EINVAL) {
    return JNI_TRUE;
  }
  #endif
  #ifdef CLOCK_REALTIME
  epollWaitClock = CLOCK_REALTIME;
  if (clock_gettime(epollWaitClock, &ts) != EINVAL) {
    return JNI_TRUE;
  }
  #endif
  fprintf(stderr, "FATAL: could not find a clock for clock_gettime!\n");
  return JNI_FALSE;
}

static int getSysctlValue(const char * property, int* returnValue) {
    int rc = -1;
    FILE *fd=fopen(property, "r");
    if (fd != NULL) {
      char buf[32] = {0x0};
      if (fgets(buf, 32, fd) != NULL) {
        *returnValue = atoi(buf);
        rc = 0;
      }
      fclose(fd);
    }
    return rc;
}

static inline jint epollCtl(JNIEnv* env, jint efd, int op, jint fd, jint flags) {
    uint32_t events = flags;
    struct epoll_event ev = {
        .data.fd = fd,
        .events = events
    };

    return epoll_ctl(efd, op, fd, &ev);
}
// JNI Registered Methods Begin
static jint netty_epoll_native_eventFd(JNIEnv* env, jclass clazz) {
    jint eventFD = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);

    if (eventFD < 0) {
        int err = errno;
        netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd() failed: ", err);
    }
    return eventFD;
}

static void netty_epoll_native_eventFdWrite(JNIEnv* env, jclass clazz, jint fd, jlong value) {
    jint eventFD = eventfd_write(fd, (eventfd_t) value);

    if (eventFD < 0) {
        int err = errno;
        netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd_write() failed: ", err);
    }
}

static void netty_epoll_native_eventFdRead(JNIEnv* env, jclass clazz, jint fd) {
    uint64_t eventfd_t;

    if (eventfd_read(fd, &eventfd_t) != 0) {
        // something is serious wrong
        netty_unix_errors_throwRuntimeException(env, "eventfd_read() failed");
    }
}

static jint netty_epoll_native_epollCreate(JNIEnv* env, jclass clazz) {
    jint efd;
    if (epoll_create1) {
        efd = epoll_create1(EPOLL_CLOEXEC);
    } else {
        // size will be ignored anyway but must be positive
        efd = epoll_create(126);
    }
    if (efd < 0) {
        int err = errno;
        if (epoll_create1) {
            netty_unix_errors_throwChannelExceptionErrorNo(env, "epoll_create1() failed: ", err);
        } else {
            netty_unix_errors_throwChannelExceptionErrorNo(env, "epoll_create() failed: ", err);
        }
        return efd;
    }
    if (!epoll_create1) {
        if (fcntl(efd, F_SETFD, FD_CLOEXEC) < 0) {
            int err = errno;
            close(efd);
            netty_unix_errors_throwChannelExceptionErrorNo(env, "fcntl() failed: ", err);
            return err;
        }
    }
    return efd;
}

static jint netty_epoll_native_epollWait0(JNIEnv* env, jclass clazz, jint efd, jlong address, jint len, jint timeout) {
    struct epoll_event *ev = (struct epoll_event*) (intptr_t) address;
    struct timespec ts;
    jlong timeBeforeWait, timeNow;
    int timeDiff, result, err;

    if (timeout > MAX_EPOLL_TIMEOUT_MSEC) {
        // Workaround for bug in older linux kernels that can not handle bigger timeout then MAX_EPOLL_TIMEOUT_MSEC.
        timeout = MAX_EPOLL_TIMEOUT_MSEC;
    }

    clock_gettime(epollWaitClock, &ts);
    timeBeforeWait = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;

    for (;;) {
      result = epoll_wait(efd, ev, len, timeout);
      if (result >= 0) {
        return result;
      }
      if ((err = errno) == EINTR) {
        if (timeout > 0) {
          clock_gettime(epollWaitClock, &ts);
          timeNow = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
          timeDiff = timeNow - timeBeforeWait;
          timeout -= timeDiff;
          if (timeDiff < 0 || timeout <= 0) {
            return 0;
          }
          timeBeforeWait = timeNow;
        } else if (timeout == 0) {
          return 0;
        }
      } else {
        return -err;
      }
    }
}

static jint netty_epoll_native_epollCtlAdd0(JNIEnv* env, jclass clazz, jint efd, jint fd, jint flags) {
    int res = epollCtl(env, efd, EPOLL_CTL_ADD, fd, flags);
    if (res < 0) {
        return -errno;
    }
    return res;
}
static jint netty_epoll_native_epollCtlMod0(JNIEnv* env, jclass clazz, jint efd, jint fd, jint flags) {
    int res = epollCtl(env, efd, EPOLL_CTL_MOD, fd, flags);
    if (res < 0) {
        return -errno;
    }
    return res;
}

static jint netty_epoll_native_epollCtlDel0(JNIEnv* env, jclass clazz, jint efd, jint fd) {
    // Create an empty event to workaround a bug in older kernels which can not handle NULL.
    struct epoll_event event = { 0 };
    int res = epoll_ctl(efd, EPOLL_CTL_DEL, fd, &event);
    if (res < 0) {
        return -errno;
    }
    return res;
}

static jint netty_epoll_native_sendmmsg0(JNIEnv* env, jclass clazz, jint fd, jobjectArray packets, jint offset, jint len) {
    struct mmsghdr msg[len];
    struct sockaddr_storage addr[len];
    int i;

    memset(msg, 0, sizeof(msg));

    for (i = 0; i < len; i++) {

        jobject packet = (*env)->GetObjectArrayElement(env, packets, i + offset);
        jbyteArray address = (jbyteArray) (*env)->GetObjectField(env, packet, packetAddrFieldId);
        jint scopeId = (*env)->GetIntField(env, packet, packetScopeIdFieldId);
        jint port = (*env)->GetIntField(env, packet, packetPortFieldId);

        if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr[i]) == -1) {
            return -1;
        }

        msg[i].msg_hdr.msg_name = &addr[i];
        msg[i].msg_hdr.msg_namelen = sizeof(addr[i]);

        msg[i].msg_hdr.msg_iov = (struct iovec*) (intptr_t) (*env)->GetLongField(env, packet, packetMemoryAddressFieldId);
        msg[i].msg_hdr.msg_iovlen = (*env)->GetIntField(env, packet, packetCountFieldId);;
    }

    ssize_t res;
    int err;
    do {
       res = sendmmsg(fd, msg, len, 0);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}
static jint netty_epoll_native_recvFd0(JNIEnv* env, jclass clazz, jint fd) {
    int socketFd;
    struct msghdr descriptorMessage = { 0 };
    struct iovec iov[1] = { 0 };
    char control[CMSG_SPACE(sizeof(int))] = { 0 };
    char iovecData[1];

    descriptorMessage.msg_control = control;
    descriptorMessage.msg_controllen = sizeof(control);
    descriptorMessage.msg_iov = iov;
    descriptorMessage.msg_iovlen = 1;
    iov[0].iov_base = iovecData;
    iov[0].iov_len = sizeof(iovecData);

    ssize_t res;
    int err;

    for (;;) {
        do {
            res = recvmsg(fd, &descriptorMessage, 0);
            // Keep on reading if we was interrupted
        } while (res == -1 && ((err = errno) == EINTR));

        if (res == 0) {
            return 0;
        }

        if (res < 0) {
            return -err;
        }

        struct cmsghdr* cmsg = CMSG_FIRSTHDR(&descriptorMessage);
        if (!cmsg) {
            return -errno;
        }

        if ((cmsg->cmsg_len == CMSG_LEN(sizeof(int))) && (cmsg->cmsg_level == SOL_SOCKET) && (cmsg->cmsg_type == SCM_RIGHTS)) {
            socketFd = *((int *) CMSG_DATA(cmsg));
            // set as non blocking as we want to use it with epoll
            if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
                err = errno;
                close(socketFd);
                return -err;
            }
            return socketFd;
        }
    }
}

static jint netty_epoll_native_sendFd0(JNIEnv* env, jclass clazz, jint socketFd, jint fd) {
    struct msghdr descriptorMessage = { 0 };
    struct iovec iov[1] = { 0 };
    char control[CMSG_SPACE(sizeof(int))] = { 0 };
    char iovecData[1];

    descriptorMessage.msg_control = control;
    descriptorMessage.msg_controllen = sizeof(control);
    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&descriptorMessage);

    if (cmsg) {
        cmsg->cmsg_len = CMSG_LEN(sizeof(int));
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        *((int *)CMSG_DATA(cmsg)) = fd;
        descriptorMessage.msg_iov = iov;
        descriptorMessage.msg_iovlen = 1;
        iov[0].iov_base = iovecData;
        iov[0].iov_len = sizeof(iovecData);

        ssize_t res;
        int err;
        do {
            res = sendmsg(socketFd, &descriptorMessage, 0);
        // keep on writing if it was interrupted
        } while (res == -1 && ((err = errno) == EINTR));

        if (res < 0) {
            return -err;
        }
        return (jint) res;
    }
    return -1;
}

static jlong netty_epoll_native_sendfile0(JNIEnv* env, jclass clazz, jint fd, jobject fileRegion, jlong base_off, jlong off, jlong len) {
    jobject fileChannel = (*env)->GetObjectField(env, fileRegion, fileChannelFieldId);
    if (fileChannel == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get DefaultFileRegion.file");
        return -1;
    }
    jobject fileDescriptor = (*env)->GetObjectField(env, fileChannel, fileDescriptorFieldId);
    if (fileDescriptor == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get FileChannelImpl.fd");
        return -1;
    }
    jint srcFd = (*env)->GetIntField(env, fileDescriptor, fdFieldId);
    if (srcFd == -1) {
        netty_unix_errors_throwRuntimeException(env, "failed to get FileDescriptor.fd");
        return -1;
    }
    ssize_t res;
    off_t offset = base_off + off;
    int err;
    do {
      res = sendfile(fd, srcFd, &offset, (size_t) len);
    } while (res == -1 && ((err = errno) == EINTR));
    if (res < 0) {
        return -err;
    }
    if (res > 0) {
        // update the transfered field in DefaultFileRegion
        (*env)->SetLongField(env, fileRegion, transferedFieldId, off + res);
    }

    return res;
}
static void netty_epoll_native_setReuseAddress(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
}

static void netty_epoll_native_setReusePort(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpFastopen(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_FASTOPEN, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_NOTSENT_LOWAT, &optval, sizeof(optval));
}

static void netty_epoll_native_setTrafficClass(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval));

    /* Try to set the ipv6 equivalent, but don't throw if this is an ipv4 only socket. */
    int rc = setsockopt(fd, IPPROTO_IPV6, IPV6_TCLASS, &optval, sizeof(optval));
    if (rc < 0 && errno != ENOPROTOOPT) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "setting ipv6 dscp failed: ", errno);
    }
}

static void netty_epoll_native_setBroadcast(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof(optval));
}

static void netty_epoll_native_setTcpUserTimeout(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_USER_TIMEOUT, &optval, sizeof(optval));
}

static void netty_epoll_native_setIpFreeBind(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_FREEBIND, &optval, sizeof(optval));
}

static jint netty_epoll_native_isReuseAddress(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_isReusePort(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_getTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_NOTSENT_LOWAT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_getTrafficClass(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_isBroadcast(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_getTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_getTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_native_getTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_native_getTcpUserTimeout(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_USER_TIMEOUT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_Native_isIpFreeBind(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_FREEBIND, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static void netty_epoll_native_tcpInfo0(JNIEnv* env, jclass clazz, jint fd, jintArray array) {
     struct tcp_info tcp_info;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_INFO, &tcp_info, sizeof(tcp_info)) == -1) {
         return;
     }
     unsigned int cArray[32];
     cArray[0] = tcp_info.tcpi_state;
     cArray[1] = tcp_info.tcpi_ca_state;
     cArray[2] = tcp_info.tcpi_retransmits;
     cArray[3] = tcp_info.tcpi_probes;
     cArray[4] = tcp_info.tcpi_backoff;
     cArray[5] = tcp_info.tcpi_options;
     cArray[6] = tcp_info.tcpi_snd_wscale;
     cArray[7] = tcp_info.tcpi_rcv_wscale;
     cArray[8] = tcp_info.tcpi_rto;
     cArray[9] = tcp_info.tcpi_ato;
     cArray[10] = tcp_info.tcpi_snd_mss;
     cArray[11] = tcp_info.tcpi_rcv_mss;
     cArray[12] = tcp_info.tcpi_unacked;
     cArray[13] = tcp_info.tcpi_sacked;
     cArray[14] = tcp_info.tcpi_lost;
     cArray[15] = tcp_info.tcpi_retrans;
     cArray[16] = tcp_info.tcpi_fackets;
     cArray[17] = tcp_info.tcpi_last_data_sent;
     cArray[18] = tcp_info.tcpi_last_ack_sent;
     cArray[19] = tcp_info.tcpi_last_data_recv;
     cArray[20] = tcp_info.tcpi_last_ack_recv;
     cArray[21] = tcp_info.tcpi_pmtu;
     cArray[22] = tcp_info.tcpi_rcv_ssthresh;
     cArray[23] = tcp_info.tcpi_rtt;
     cArray[24] = tcp_info.tcpi_rttvar;
     cArray[25] = tcp_info.tcpi_snd_ssthresh;
     cArray[26] = tcp_info.tcpi_snd_cwnd;
     cArray[27] = tcp_info.tcpi_advmss;
     cArray[28] = tcp_info.tcpi_reordering;
     cArray[29] = tcp_info.tcpi_rcv_rtt;
     cArray[30] = tcp_info.tcpi_rcv_space;
     cArray[31] = tcp_info.tcpi_total_retrans;

     (*env)->SetIntArrayRegion(env, array, 0, 32, cArray);
}

static jstring netty_epoll_native_kernelVersion(JNIEnv* env, jclass clazz) {
    struct utsname name;

    int res = uname(&name);
    if (res == 0) {
        return (*env)->NewStringUTF(env, name.release);
    }
    int err = errno;
    netty_unix_errors_throwRuntimeExceptionErrorNo(env, "uname() failed: ", err);
    return NULL;
}

static jint netty_epoll_native_iovMax(JNIEnv* env, jclass clazz) {
    return IOV_MAX;
}

static jint netty_epoll_native_uioMaxIov(JNIEnv* env, jclass clazz) {
    return UIO_MAXIOV;
}

static jboolean netty_epoll_native_isSupportingSendmmsg(JNIEnv* env, jclass clazz) {
    if (sendmmsg) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static jboolean netty_epoll_native_isSupportingTcpFastopen(JNIEnv* env, jclass clazz) {
    int fastopen = 0;
    getSysctlValue("/proc/sys/net/ipv4/tcp_fastopen", &fastopen);
    if (fastopen > 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static jint netty_epoll_native_epollet(JNIEnv* env, jclass clazz) {
    return EPOLLET;
}

static jint netty_epoll_native_epollin(JNIEnv* env, jclass clazz) {
    return EPOLLIN;
}

static jint netty_epoll_native_epollout(JNIEnv* env, jclass clazz) {
    return EPOLLOUT;
}

static jint netty_epoll_native_epollrdhup(JNIEnv* env, jclass clazz) {
    return EPOLLRDHUP;
}

static jint netty_epoll_native_epollerr(JNIEnv* env, jclass clazz) {
    return EPOLLERR;
}

static jint netty_epoll_native_sizeofEpollEvent(JNIEnv* env, jclass clazz) {
    return sizeof(struct epoll_event);
}

static jint netty_epoll_native_offsetofEpollData(JNIEnv* env, jclass clazz) {
    return offsetof(struct epoll_event, data);
}

static jint netty_epoll_native_splice0(JNIEnv* env, jclass clazz, jint fd, jlong offIn, jint fdOut, jlong offOut, jlong len) {
    ssize_t res;
    int err;
    loff_t off_in = (loff_t) offIn;
    loff_t off_out = (loff_t) offOut;

    loff_t* p_off_in = off_in >= 0 ? &off_in : NULL;
    loff_t* p_off_out = off_in >= 0 ? &off_out : NULL;

    do {
       res = splice(fd, p_off_in, fdOut, p_off_out, (size_t) len, SPLICE_F_NONBLOCK | SPLICE_F_MOVE);
       // keep on splicing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jlong netty_epoll_native_ssizeMax(JNIEnv* env, jclass clazz) {
    return SSIZE_MAX;
}

static jint netty_epoll_native_tcpMd5SigMaxKeyLen(JNIEnv* env, jclass clazz) {
    struct tcp_md5sig md5sig;

    // Defensive size check
    if (sizeof(md5sig.tcpm_key) < TCP_MD5SIG_MAXKEYLEN) {
        return sizeof(md5sig.tcpm_key);
    }

    return TCP_MD5SIG_MAXKEYLEN;
}

static void netty_epoll_native_setTcpMd5Sig0(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jbyteArray key) {
    struct sockaddr_storage addr;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, 0, &addr) == -1) {
        return;
    }

    struct tcp_md5sig md5sig;
    memset(&md5sig, 0, sizeof(md5sig));
    md5sig.tcpm_addr.ss_family = addr.ss_family;

    struct sockaddr_in* ipaddr;
    struct sockaddr_in6* ip6addr;

    switch (addr.ss_family) {
    case AF_INET:
        ipaddr = (struct sockaddr_in*) &addr;
        memcpy(&((struct sockaddr_in *) &md5sig.tcpm_addr)->sin_addr, &ipaddr->sin_addr, sizeof(ipaddr->sin_addr));
        break;
    case AF_INET6:
        ip6addr = (struct sockaddr_in6*) &addr;
        memcpy(&((struct sockaddr_in6 *) &md5sig.tcpm_addr)->sin6_addr, &ip6addr->sin6_addr, sizeof(ip6addr->sin6_addr));
        break;
    }

    if (key != NULL) {
        md5sig.tcpm_keylen = (*env)->GetArrayLength(env, key);
        (*env)->GetByteArrayRegion(env, key, 0, md5sig.tcpm_keylen, (void *) &md5sig.tcpm_key);
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
            return;
        }
    }

    if (setsockopt(fd, IPPROTO_TCP, TCP_MD5SIG, &md5sig, sizeof(md5sig)) < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "setsockopt() failed: ", errno);
    }
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "epollet", "()I", (void *) netty_epoll_native_epollet },
  { "epollin", "()I", (void *) netty_epoll_native_epollin },
  { "epollout", "()I", (void *) netty_epoll_native_epollout },
  { "epollrdhup", "()I", (void *) netty_epoll_native_epollrdhup },
  { "epollerr", "()I", (void *) netty_epoll_native_epollerr },
  { "ssizeMax", "()J", (void *) netty_epoll_native_ssizeMax },
  { "tcpMd5SigMaxKeyLen", "()I", (void *) netty_epoll_native_tcpMd5SigMaxKeyLen },
  { "iovMax", "()I", (void *) netty_epoll_native_iovMax },
  { "uioMaxIov", "()I", (void *) netty_epoll_native_uioMaxIov },
  { "isSupportingSendmmsg", "()Z", (void *) netty_epoll_native_isSupportingSendmmsg },
  { "isSupportingTcpFastopen", "()Z", (void *) netty_epoll_native_isSupportingTcpFastopen },
  { "kernelVersion", "()Ljava/lang/String;", (void *) netty_epoll_native_kernelVersion }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "eventFd", "()I", (void *) netty_epoll_native_eventFd },
  { "eventFdWrite", "(IJ)V", (void *) netty_epoll_native_eventFdWrite },
  { "eventFdRead", "(I)V", (void *) netty_epoll_native_eventFdRead },
  { "epollCreate", "()I", (void *) netty_epoll_native_epollCreate },
  { "epollWait0", "(IJII)I", (void *) netty_epoll_native_epollWait0 },
  { "epollCtlAdd0", "(III)I", (void *) netty_epoll_native_epollCtlAdd0 },
  { "epollCtlMod0", "(III)I", (void *) netty_epoll_native_epollCtlMod0 },
  { "epollCtlDel0", "(II)I", (void *) netty_epoll_native_epollCtlDel0 },
  // "sendmmsg0" has a dynamic signature
  { "recvFd0", "(I)I", (void *) netty_epoll_native_recvFd0 },
  { "sendFd0", "(II)I", (void *) netty_epoll_native_sendFd0 },
  // "sendFile0" has a dynamic signature
  { "setReuseAddress", "(II)V", (void *) netty_epoll_native_setReuseAddress },
  { "setReusePort", "(II)V", (void *) netty_epoll_native_setReusePort },
  { "setTcpFastopen", "(II)V", (void *) netty_epoll_native_setTcpFastopen },
  { "setTcpNotSentLowAt", "(II)V", (void *) netty_epoll_native_setTcpNotSentLowAt },
  { "setTrafficClass", "(II)V", (void *) netty_epoll_native_setTrafficClass },
  { "setBroadcast", "(II)V", (void *) netty_epoll_native_setBroadcast },
  { "setTcpKeepIdle", "(II)V", (void *) netty_epoll_native_setTcpKeepIdle },
  { "setTcpKeepIntvl", "(II)V", (void *) netty_epoll_native_setTcpKeepIntvl },
  { "setTcpKeepCnt", "(II)V", (void *) netty_epoll_native_setTcpKeepCnt },
  { "setTcpUserTimeout", "(II)V", (void *) netty_epoll_native_setTcpUserTimeout },
  { "setIpFreeBind", "(II)V", (void *) netty_epoll_native_setIpFreeBind },
  { "isReuseAddress", "(I)I", (void *) netty_epoll_native_isReuseAddress },
  { "isReusePort", "(I)I", (void *) netty_epoll_native_isReusePort },
  { "getTcpNotSentLowAt", "(I)I", (void *) netty_epoll_native_getTcpNotSentLowAt },
  { "getTrafficClass", "(I)I", (void *) netty_epoll_native_getTrafficClass },
  { "isBroadcast", "(I)I", (void *) netty_epoll_native_isBroadcast },
  { "getTcpKeepIdle", "(I)I", (void *) netty_epoll_native_getTcpKeepIdle },
  { "getTcpKeepIntvl", "(I)I", (void *) netty_epoll_native_getTcpKeepIntvl },
  { "getTcpKeepCnt", "(I)I", (void *) netty_epoll_native_getTcpKeepCnt },
  { "getTcpUserTimeout", "(I)I", (void *) netty_epoll_native_getTcpUserTimeout },
  { "isIpFreeBind", "(I)I", (void *) netty_epoll_Native_isIpFreeBind },
  { "tcpInfo0", "(I[I)V", (void *) netty_epoll_native_tcpInfo0 },
  { "sizeofEpollEvent", "()I", (void *) netty_epoll_native_sizeofEpollEvent },
  { "offsetofEpollData", "()I", (void *) netty_epoll_native_offsetofEpollData },
  { "splice0", "(IJIJJ)I", (void *) netty_epoll_native_splice0 },
  { "setTcpMd5Sig0", "(I[BI[B)V", (void *) netty_epoll_native_setTcpMd5Sig0 }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * dynamicMethodsTableSize());
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket;II)I");
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    dynamicMethod->name = "sendmmsg0";
    dynamicMethod->signature = netty_unix_util_prepend("(I[L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_epoll_native_sendmmsg0;
    free(dynamicTypeName);
    ++dynamicMethod;
    dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/DefaultFileRegion;JJJ)J");
    dynamicMethod->name = "sendfile0";
    dynamicMethod->signature = netty_unix_util_prepend("(IL", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_epoll_native_sendfile0;
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

static jint netty_epoll_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // We must register the statically referenced methods first!
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/epoll/NativeStaticallyReferencedJniMethods",
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }
    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/epoll/Native",
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;
    // Load all c modules that we depend upon
    if (netty_unix_errors_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_unix_filedescriptor_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    if (netty_unix_socket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        return JNI_ERR;
    }
    // Initialize this module
    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/DefaultFileRegion");
    jclass fileRegionCls = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (fileRegionCls == NULL) {
        return JNI_ERR;
    }
    fileChannelFieldId = (*env)->GetFieldID(env, fileRegionCls, "file", "Ljava/nio/channels/FileChannel;");
    if (fileChannelFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: DefaultFileRegion.file");
        return JNI_ERR;
    }
    transferedFieldId = (*env)->GetFieldID(env, fileRegionCls, "transfered", "J");
    if (transferedFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: DefaultFileRegion.transfered");
        return JNI_ERR;
    }

    jclass fileChannelCls = (*env)->FindClass(env, "sun/nio/ch/FileChannelImpl");
    if (fileChannelCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    fileDescriptorFieldId = (*env)->GetFieldID(env, fileChannelCls, "fd", "Ljava/io/FileDescriptor;");
    if (fileDescriptorFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: FileChannelImpl.fd");
        return JNI_ERR;
    }

    jclass fileDescriptorCls = (*env)->FindClass(env, "java/io/FileDescriptor");
    if (fileDescriptorCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    fdFieldId = (*env)->GetFieldID(env, fileDescriptorCls, "fd", "I");
    if (fdFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: FileDescriptor.fd");
        return JNI_ERR;
    }

    nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket");
    jclass nativeDatagramPacketCls = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (nativeDatagramPacketCls == NULL) {
        // pending exception...
        return JNI_ERR;
    }

    packetAddrFieldId = (*env)->GetFieldID(env, nativeDatagramPacketCls, "addr", "[B");
    if (packetAddrFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: NativeDatagramPacket.addr");
        return JNI_ERR;
    }
    packetScopeIdFieldId = (*env)->GetFieldID(env, nativeDatagramPacketCls, "scopeId", "I");
    if (packetScopeIdFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: NativeDatagramPacket.scopeId");
        return JNI_ERR;
    }
    packetPortFieldId = (*env)->GetFieldID(env, nativeDatagramPacketCls, "port", "I");
    if (packetPortFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: NativeDatagramPacket.port");
        return JNI_ERR;
    }
    packetMemoryAddressFieldId = (*env)->GetFieldID(env, nativeDatagramPacketCls, "memoryAddress", "J");
    if (packetMemoryAddressFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: NativeDatagramPacket.memoryAddress");
        return JNI_ERR;
    }

    packetCountFieldId = (*env)->GetFieldID(env, nativeDatagramPacketCls, "count", "I");
    if (packetCountFieldId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get field ID: NativeDatagramPacket.count");
        return JNI_ERR;
    }

    if (!initializeEpollWaitClock()) {
      return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

static void netty_epoll_native_JNI_OnUnLoad(JNIEnv* env) {
    netty_unix_errors_JNI_OnUnLoad(env);
    netty_unix_filedescriptor_JNI_OnUnLoad(env);
    netty_unix_socket_JNI_OnUnLoad(env);
}

/**
 * The expected format of the library name is "lib<>netty-transport-native-epoll" where the <> portion is what we will return.
 */
static char* parsePackagePrefix(const char* libraryPathName, jint* status) {
    char* packageNameEnd = strstr(libraryPathName, "netty-transport-native-epoll");
    if (packageNameEnd == NULL) {
        *status = JNI_ERR;
        return NULL;
    }
    char* packagePrefix = netty_unix_util_rstrstr(packageNameEnd, libraryPathName, "lib");
    if (packagePrefix == NULL) {
        *status = JNI_ERR;
        return NULL;
    }
    packagePrefix += 3;
    if (packagePrefix == packageNameEnd) {
        return NULL;
    }
    // packagePrefix length is > 0
    // Make a copy so we can modify the value without impacting libraryPathName.
    size_t packagePrefixLen = packageNameEnd - packagePrefix;
    packagePrefix = strndup(packagePrefix, packagePrefixLen);
    // Make sure the packagePrefix is in the correct format for the JNI functions it will be used with.
    char* temp = packagePrefix;
    packageNameEnd = packagePrefix + packagePrefixLen;
    // Package names must be sanitized, in JNI packages names are separated by '/' characters.
    for (; temp != packageNameEnd; ++temp) {
        if (*temp == '-') {
            *temp = '/';
        }
    }
    // Make sure packagePrefix is terminated with the '/' JNI package separator.
    if(*(--temp) != '/') {
        temp = packagePrefix;
        packagePrefix = netty_unix_util_prepend(packagePrefix, "/");
        free(temp);
    }
    return packagePrefix;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    Dl_info dlinfo;
    jint status = 0;
    // We need to use an address of a function that is uniquely part of this library, so choose a static
    // function. See https://github.com/netty/netty/issues/4840.
    if (!dladdr((void*) parsePackagePrefix, &dlinfo)) {
        fprintf(stderr, "FATAL: transport-native-epoll JNI call to dladdr failed!\n");
        return JNI_ERR;
    }
    char* packagePrefix = parsePackagePrefix(dlinfo.dli_fname, &status);
    if (status == JNI_ERR) {
        fprintf(stderr, "FATAL: transport-native-epoll JNI encountered unexpected dlinfo.dli_fname: %s\n", dlinfo.dli_fname);
        return JNI_ERR;
    }

    jint ret = netty_epoll_native_JNI_OnLoad(env, packagePrefix);

    if (packagePrefix != NULL) {
      free(packagePrefix);
      packagePrefix = NULL;
    }

    return ret;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    }
    netty_epoll_native_JNI_OnUnLoad(env);
}

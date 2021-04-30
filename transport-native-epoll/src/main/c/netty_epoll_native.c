/*
 * Copyright 2013 The Netty Project
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
#define _GNU_SOURCE
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/timerfd.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include <stddef.h>
#include <limits.h>
#include <inttypes.h>
#include <link.h>
#include <time.h>
// Needed to be able to use syscalls directly and so not depend on newer GLIBC versions
#include <linux/net.h>
#include <sys/syscall.h>

// Needed for UDP_SEGMENT
#include <netinet/udp.h>

#include "netty_epoll_linuxsocket.h"
#include "netty_unix_buffer.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_limits.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"
#include "netty_unix.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/channel/epoll/NativeStaticallyReferencedJniMethods"
#define NATIVE_CLASSNAME "io/netty/channel/epoll/Native"

// TCP_FASTOPEN is defined in linux 3.7. We define this here so older kernels can compile.
#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

// Allow to compile on systems with older kernels.
#ifndef UDP_SEGMENT
#define UDP_SEGMENT	103
#endif

// UDP_GRO is defined in linux 5. We define this here so older kernels can compile.
#ifndef UDP_GRO
#define UDP_GRO 104
#endif

#ifdef IP_RECVORIGDSTADDR
#if !defined(SOL_IP) && defined(IPPROTO_IP)
#define SOL_IP IPPROTO_IP
#endif /* !SOL_IP && IPPROTO_IP */
#endif // IP_RECVORIGDSTADDR

// optional
extern int epoll_create1(int flags) __attribute__((weak));

#ifndef __USE_GNU
struct mmsghdr {
    struct msghdr msg_hdr;  /* Message header */
    unsigned int  msg_len;  /* Number of bytes transmitted */
};
#endif

// All linux syscall numbers are stable so this is safe.
#ifndef SYS_recvmmsg
// Only support SYS_recvmmsg for __x86_64__ / __i386__ for now
#if defined(__x86_64__)
// See https://github.com/torvalds/linux/blob/v5.4/arch/x86/entry/syscalls/syscall_64.tbl
#define SYS_recvmmsg 299
#elif defined(__i386__)
// See https://github.com/torvalds/linux/blob/v5.4/arch/x86/entry/syscalls/syscall_32.tbl
#define SYS_recvmmsg 337
#else
#define SYS_recvmmsg -1
#endif
#endif // SYS_recvmmsg

#ifndef SYS_sendmmsg
// Only support SYS_sendmmsg for __x86_64__ / __i386__ for now
#if defined(__x86_64__)
// See https://github.com/torvalds/linux/blob/v5.4/arch/x86/entry/syscalls/syscall_64.tbl
#define SYS_sendmmsg 307
#elif defined(__i386__)
// See https://github.com/torvalds/linux/blob/v5.4/arch/x86/entry/syscalls/syscall_32.tbl
#define SYS_sendmmsg 345
#else
#define SYS_sendmmsg -1
#endif
#endif // SYS_sendmmsg

// Those are initialized in the init(...) method and cached for performance reasons
static jfieldID packetSenderAddrFieldId = NULL;
static jfieldID packetSenderAddrLenFieldId = NULL;
static jfieldID packetSenderScopeIdFieldId = NULL;
static jfieldID packetSenderPortFieldId = NULL;
static jfieldID packetRecipientAddrFieldId = NULL;
static jfieldID packetRecipientAddrLenFieldId = NULL;
static jfieldID packetRecipientScopeIdFieldId = NULL;
static jfieldID packetRecipientPortFieldId = NULL;

static jfieldID packetSegmentSizeFieldId = NULL;
static jfieldID packetMemoryAddressFieldId = NULL;
static jfieldID packetCountFieldId = NULL;

static const char* staticPackagePrefix = NULL;
static int register_unix_called = 0;

// util methods
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
        netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd() failed: ", errno);
    }
    return eventFD;
}

static jint netty_epoll_native_timerFd(JNIEnv* env, jclass clazz) {
    jint timerFD = timerfd_create(CLOCK_MONOTONIC, TFD_CLOEXEC | TFD_NONBLOCK);

    if (timerFD < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "timerfd_create() failed: ", errno);
    }
    return timerFD;
}

static void netty_epoll_native_eventFdWrite(JNIEnv* env, jclass clazz, jint fd, jlong value) {
    uint64_t val;

    for (;;) {
        jint ret = eventfd_write(fd, (eventfd_t) value);

        if (ret < 0) {
            // We need to read before we can write again, let's try to read and then write again and if this
            // fails we will bail out.
            //
            // See https://man7.org/linux/man-pages/man2/eventfd.2.html.
            if (errno == EAGAIN) {
                if (eventfd_read(fd, &val) == 0 || errno == EAGAIN) {
                    // Try again
                    continue;
                }
                netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd_read(...) failed: ", errno);
            } else {
                netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd_write(...) failed: ", errno);
            }
        }
        break;
    }
}

static void netty_epoll_native_eventFdRead(JNIEnv* env, jclass clazz, jint fd) {
    uint64_t eventfd_t;

    if (eventfd_read(fd, &eventfd_t) != 0) {
        // something is serious wrong
        netty_unix_errors_throwRuntimeException(env, "eventfd_read() failed");
    }
}

static void netty_epoll_native_timerFdRead(JNIEnv* env, jclass clazz, jint fd) {
    uint64_t timerFireCount;

    if (read(fd, &timerFireCount, sizeof(uint64_t)) < 0) {
        // it is expected that this is only called where there is known to be activity, so this is an error.
        netty_unix_errors_throwChannelExceptionErrorNo(env, "read() failed: ", errno);
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

static void netty_epoll_native_timerFdSetTime(JNIEnv* env, jclass clazz, jint timerFd, jint tvSec, jint tvNsec) {
    struct itimerspec ts;
    memset(&ts.it_interval, 0, sizeof(struct timespec));
    ts.it_value.tv_sec = tvSec;
    ts.it_value.tv_nsec = tvNsec;
    if (timerfd_settime(timerFd, 0, &ts, NULL) < 0) {
        netty_unix_errors_throwIOExceptionErrorNo(env, "timerfd_settime() failed: ", errno);
    }
}

static jint netty_epoll_native_epollWait(JNIEnv* env, jclass clazz, jint efd, jlong address, jint len, jint timeout) {
    struct epoll_event *ev = (struct epoll_event*) (intptr_t) address;
    int result, err;

    do {
        result = epoll_wait(efd, ev, len, timeout);
        if (result >= 0) {
            return result;
        }
    } while((err = errno) == EINTR);
    return -err;
}

// This method is deprecated!
static jint netty_epoll_native_epollWait0(JNIEnv* env, jclass clazz, jint efd, jlong address, jint len, jint timerFd, jint tvSec, jint tvNsec) {
    // only reschedule the timer if there is a newer event.
    // -1 is a special value used by EpollEventLoop.
    if (tvSec != ((jint) -1) && tvNsec != ((jint) -1)) {
    	struct itimerspec ts;
    	memset(&ts.it_interval, 0, sizeof(struct timespec));
    	ts.it_value.tv_sec = tvSec;
    	ts.it_value.tv_nsec = tvNsec;
    	if (timerfd_settime(timerFd, 0, &ts, NULL) < 0) {
    		netty_unix_errors_throwChannelExceptionErrorNo(env, "timerfd_settime() failed: ", errno);
    		return -1;
    	}
    }
    return netty_epoll_native_epollWait(env, clazz, efd, address, len, -1);
}

static inline void cpu_relax() {
#if defined(__x86_64__)
    asm volatile("pause\n": : :"memory");
#endif
}

static jint netty_epoll_native_epollBusyWait0(JNIEnv* env, jclass clazz, jint efd, jlong address, jint len) {
    struct epoll_event *ev = (struct epoll_event*) (intptr_t) address;
    int result, err;

    // Zeros = poll (aka return immediately).
    do {
        result = epoll_wait(efd, ev, len, 0);
        if (result == 0) {
            // Since we're always polling epoll_wait with no timeout,
            // signal CPU that we're in a busy loop
            cpu_relax();
        }

        if (result >= 0) {
            return result;
        }
    } while((err = errno) == EINTR);

    return -err;
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

static jint netty_epoll_native_sendmmsg0(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jobjectArray packets, jint offset, jint len) {
    struct mmsghdr msg[len];
    struct sockaddr_storage addr[len];
    char controls[len][CMSG_SPACE(sizeof(uint16_t))];

    socklen_t addrSize;
    int i;

    memset(msg, 0, sizeof(msg));

    for (i = 0; i < len; i++) {
        jobject packet = (*env)->GetObjectArrayElement(env, packets, i + offset);
        jbyteArray address = (jbyteArray) (*env)->GetObjectField(env, packet, packetRecipientAddrFieldId);
        jint addrLen = (*env)->GetIntField(env, packet, packetRecipientAddrLenFieldId);
        jint packetSegmentSize = (*env)->GetIntField(env, packet, packetSegmentSizeFieldId);
        if (packetSegmentSize > 0) {
            msg[i].msg_hdr.msg_control = controls[i];
            msg[i].msg_hdr.msg_controllen = sizeof(controls[i]);
            struct cmsghdr *cm = CMSG_FIRSTHDR(&msg[i].msg_hdr);
            cm->cmsg_level = SOL_UDP;
            cm->cmsg_type = UDP_SEGMENT;
            cm->cmsg_len = CMSG_LEN(sizeof(uint16_t));
            *((uint16_t *) CMSG_DATA(cm)) = packetSegmentSize;
        }
        if (addrLen != 0) {
            jint scopeId = (*env)->GetIntField(env, packet, packetRecipientScopeIdFieldId);
            jint port = (*env)->GetIntField(env, packet, packetRecipientPortFieldId);

           if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, port, &addr[i], &addrSize) == -1) {
              return -1;
           }
           msg[i].msg_hdr.msg_name = &addr[i];
           msg[i].msg_hdr.msg_namelen = addrSize;
        }

        msg[i].msg_hdr.msg_iov = (struct iovec*) (intptr_t) (*env)->GetLongField(env, packet, packetMemoryAddressFieldId);
        msg[i].msg_hdr.msg_iovlen = (*env)->GetIntField(env, packet, packetCountFieldId);
    }

    ssize_t res;
    int err;
    do {
       // We directly use the syscall to prevent depending on GLIBC 2.14.
       res = syscall(SYS_sendmmsg, fd, msg, len, 0);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static void init_packet_address(JNIEnv* env, jobject packet, struct sockaddr_storage* addr, jfieldID addrFieldId,
            jfieldID addrLenFieldId, jfieldID scopeIdFieldId, jfieldID portFieldId) {
    jbyteArray address = (jbyteArray) (*env)->GetObjectField(env, packet, addrFieldId);

    if (addr->ss_family == AF_INET) {
        struct sockaddr_in* ipaddr = (struct sockaddr_in*) addr;

        (*env)->SetByteArrayRegion(env, address, 0, 4, (jbyte*) &ipaddr->sin_addr.s_addr);
        (*env)->SetIntField(env, packet, addrLenFieldId, 4);
        (*env)->SetIntField(env, packet, scopeIdFieldId, 0);
        (*env)->SetIntField(env, packet, portFieldId, ntohs(ipaddr->sin_port));
    } else {
        int addrLen = netty_unix_socket_ipAddressLength(addr);
        struct sockaddr_in6* ip6addr = (struct sockaddr_in6*) addr;

        if (addrLen == 4) {
            // IPV4 mapped IPV6 address
            jbyte* addr = (jbyte*) &ip6addr->sin6_addr.s6_addr;
            (*env)->SetByteArrayRegion(env, address, 0, 4, addr + 12);
        } else {
            (*env)->SetByteArrayRegion(env, address, 0, 16, (jbyte*) &ip6addr->sin6_addr.s6_addr);
        }
        (*env)->SetIntField(env, packet, addrLenFieldId, addrLen);
        (*env)->SetIntField(env, packet, scopeIdFieldId, ip6addr->sin6_scope_id);
        (*env)->SetIntField(env, packet, portFieldId, ntohs(ip6addr->sin6_port));
    }
}

static void init_packet(JNIEnv* env, jobject packet, struct msghdr* msg, int len) {
    (*env)->SetIntField(env, packet, packetCountFieldId, len);

    init_packet_address(env, packet, (struct sockaddr_storage*) msg->msg_name, packetSenderAddrFieldId, packetSenderAddrLenFieldId, packetSenderScopeIdFieldId, packetSenderPortFieldId);

    struct cmsghdr *cmsg = NULL;
    uint16_t gso_size = 0;
    uint16_t *gsosizeptr = NULL;
    for (cmsg = CMSG_FIRSTHDR(msg); cmsg != NULL; cmsg = CMSG_NXTHDR(msg, cmsg)) {
       if (cmsg->cmsg_level == SOL_UDP && cmsg->cmsg_type == UDP_GRO) {
           gsosizeptr = (uint16_t *) CMSG_DATA(cmsg);
           gso_size = *gsosizeptr;
       }
#ifdef IP_RECVORIGDSTADDR
       else if (cmsg->cmsg_level == SOL_IP && cmsg->cmsg_type == IP_RECVORIGDSTADDR) {
           init_packet_address(env, packet, (struct sockaddr_storage*) CMSG_DATA(cmsg), packetRecipientAddrFieldId, packetRecipientAddrLenFieldId, packetRecipientScopeIdFieldId, packetRecipientPortFieldId);
       }
#endif // IP_RECVORIGDSTADDR
    }
    (*env)->SetIntField(env, packet, packetSegmentSizeFieldId, gso_size);
}

static jint netty_epoll_native_recvmsg0(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jobject packet) {
    struct msghdr msg = { 0 };
    struct sockaddr_storage sock_address;
    int addrSize = sizeof(sock_address);
    // Enough space for GRO and IP_RECVORIGDSTADDR
    char control[CMSG_SPACE(sizeof(uint16_t)) + sizeof(struct sockaddr_storage)] = { 0 };
    msg.msg_name = &sock_address;
    msg.msg_namelen = (socklen_t) addrSize;
    msg.msg_iov = (struct iovec*) (intptr_t) (*env)->GetLongField(env, packet, packetMemoryAddressFieldId);
    msg.msg_iovlen = (*env)->GetIntField(env, packet, packetCountFieldId);
    msg.msg_control = control;
    msg.msg_controllen = sizeof(control);
    ssize_t res;
    int err;
    do {
        res = recvmsg(fd, &msg, 0);
        // keep on reading if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));
    if (res < 0) {
        return -err;
    }
    init_packet(env, packet, &msg, res);
    return (jint) res;
}

static jint netty_epoll_native_recvmmsg0(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jobjectArray packets, jint offset, jint len) {
    struct mmsghdr msg[len];
    memset(msg, 0, sizeof(msg));
    struct sockaddr_storage addr[len];
    int addrSize = sizeof(addr);
    memset(addr, 0, addrSize);
    int storageSize = sizeof(struct sockaddr_storage);
    char* cntrlbuf = NULL;

#ifdef IP_RECVORIGDSTADDR
    int readLocalAddr = 0;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_RECVORIGDSTADDR,
            &readLocalAddr, sizeof(readLocalAddr)) < 0) {
        cntrlbuf = malloc(sizeof(char) * storageSize * len);
    }
#endif // IP_RECVORIGDSTADDR

    int i;

    for (i = 0; i < len; i++) {
        jobject packet = (*env)->GetObjectArrayElement(env, packets, i + offset);
        msg[i].msg_hdr.msg_iov = (struct iovec*) (intptr_t) (*env)->GetLongField(env, packet, packetMemoryAddressFieldId);
        msg[i].msg_hdr.msg_iovlen = (*env)->GetIntField(env, packet, packetCountFieldId);

        msg[i].msg_hdr.msg_name = addr + i;
        msg[i].msg_hdr.msg_namelen = (socklen_t) addrSize;

        if (cntrlbuf != NULL) {
            msg[i].msg_hdr.msg_control =  cntrlbuf + i * storageSize;
            msg[i].msg_hdr.msg_controllen = storageSize;
        }
    }

    ssize_t res;
    int err;
    do {
        // We directly use the syscall to prevent depending on GLIBC 2.12.
        res = syscall(SYS_recvmmsg, fd, &msg, len, 0, NULL);

        // keep on reading if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res >= 0) {
        for (i = 0; i < res; i++) {
            jobject packet = (*env)->GetObjectArrayElement(env, packets, i + offset);
            init_packet(env, packet, &msg[i].msg_hdr, msg[i].msg_len);
        }
    }
    // Free the control message buffer if needed.
    free(cntrlbuf);

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jstring netty_epoll_native_kernelVersion(JNIEnv* env, jclass clazz) {
    struct utsname name;

    int res = uname(&name);
    if (res == 0) {
        return (*env)->NewStringUTF(env, name.release);
    }
    netty_unix_errors_throwRuntimeExceptionErrorNo(env, "uname() failed: ", errno);
    return NULL;
}

static jboolean netty_epoll_native_isSupportingSendmmsg(JNIEnv* env, jclass clazz) {
    if (SYS_sendmmsg == -1) {
        return JNI_FALSE;
    }
    if (syscall(SYS_sendmmsg, -1, NULL, 0, 0) == -1) {
        if (errno == ENOSYS) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

static jboolean netty_epoll_native_isSupportingUdpSegment(JNIEnv* env, jclass clazz) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd == -1) {
        return JNI_FALSE;
    }
    int gso_size = 512;
    int ret = setsockopt(fd, SOL_UDP, UDP_SEGMENT, &gso_size, sizeof(gso_size));
    close(fd);
    return ret == -1 ? JNI_FALSE : JNI_TRUE;
}

static jboolean netty_epoll_native_isSupportingRecvmmsg(JNIEnv* env, jclass clazz) {
    if (SYS_recvmmsg == -1) {
        return JNI_FALSE;
    }
    if (syscall(SYS_recvmmsg, -1, NULL, 0, 0, NULL) == -1) {
        if (errno == ENOSYS) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

static jint netty_epoll_native_tcpFastopenMode(JNIEnv* env, jclass clazz) {
    int fastopen = 0;
    getSysctlValue("/proc/sys/net/ipv4/tcp_fastopen", &fastopen);
    return fastopen;
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
    loff_t* p_off_out = off_out >= 0 ? &off_out : NULL;

    do {
       res = splice(fd, p_off_in, fdOut, p_off_out, (size_t) len, SPLICE_F_NONBLOCK | SPLICE_F_MOVE);
       // keep on splicing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jint netty_epoll_native_tcpMd5SigMaxKeyLen(JNIEnv* env, jclass clazz) {
    struct tcp_md5sig md5sig;

    // Defensive size check
    if (sizeof(md5sig.tcpm_key) < TCP_MD5SIG_MAXKEYLEN) {
        return sizeof(md5sig.tcpm_key);
    }

    return TCP_MD5SIG_MAXKEYLEN;
}

static jint netty_epoll_native_registerUnix(JNIEnv* env, jclass clazz) {
    register_unix_called = 1;
    return netty_unix_register(env, staticPackagePrefix);
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "epollet", "()I", (void *) netty_epoll_native_epollet },
  { "epollin", "()I", (void *) netty_epoll_native_epollin },
  { "epollout", "()I", (void *) netty_epoll_native_epollout },
  { "epollrdhup", "()I", (void *) netty_epoll_native_epollrdhup },
  { "epollerr", "()I", (void *) netty_epoll_native_epollerr },
  { "tcpMd5SigMaxKeyLen", "()I", (void *) netty_epoll_native_tcpMd5SigMaxKeyLen },
  { "isSupportingSendmmsg", "()Z", (void *) netty_epoll_native_isSupportingSendmmsg },
  { "isSupportingRecvmmsg", "()Z", (void *) netty_epoll_native_isSupportingRecvmmsg },
  { "tcpFastopenMode", "()I", (void *) netty_epoll_native_tcpFastopenMode },
  { "kernelVersion", "()Ljava/lang/String;", (void *) netty_epoll_native_kernelVersion }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "eventFd", "()I", (void *) netty_epoll_native_eventFd },
  { "timerFd", "()I", (void *) netty_epoll_native_timerFd },
  { "eventFdWrite", "(IJ)V", (void *) netty_epoll_native_eventFdWrite },
  { "eventFdRead", "(I)V", (void *) netty_epoll_native_eventFdRead },
  { "timerFdRead", "(I)V", (void *) netty_epoll_native_timerFdRead },
  { "timerFdSetTime", "(III)V", (void *) netty_epoll_native_timerFdSetTime },
  { "epollCreate", "()I", (void *) netty_epoll_native_epollCreate },
  { "epollWait0", "(IJIIII)I", (void *) netty_epoll_native_epollWait0 }, // This method is deprecated!
  { "epollWait", "(IJII)I", (void *) netty_epoll_native_epollWait },
  { "epollBusyWait0", "(IJI)I", (void *) netty_epoll_native_epollBusyWait0 },
  { "epollCtlAdd0", "(III)I", (void *) netty_epoll_native_epollCtlAdd0 },
  { "epollCtlMod0", "(III)I", (void *) netty_epoll_native_epollCtlMod0 },
  { "epollCtlDel0", "(II)I", (void *) netty_epoll_native_epollCtlDel0 },
  // "sendmmsg0" has a dynamic signature
  { "sizeofEpollEvent", "()I", (void *) netty_epoll_native_sizeofEpollEvent },
  { "offsetofEpollData", "()I", (void *) netty_epoll_native_offsetofEpollData },
  { "splice0", "(IJIJJ)I", (void *) netty_epoll_native_splice0 },
  { "isSupportingUdpSegment", "()Z", (void *) netty_epoll_native_isSupportingUdpSegment },
  { "registerUnix", "()I", (void *) netty_epoll_native_registerUnix },

};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 3; // 3 is for the dynamic method signatures.
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    char* dynamicTypeName = NULL;
    size_t size = sizeof(JNINativeMethod) * dynamicMethodsTableSize();
    JNINativeMethod* dynamicMethods = malloc(size);
    if (dynamicMethods == NULL) {
        return NULL;
    }
    memset(dynamicMethods, 0, size);
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket;II)I", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IZ[L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "sendmmsg0";
    dynamicMethod->fnPtr = (void *) netty_epoll_native_sendmmsg0;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket;II)I", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IZ[L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvmmsg0";
    dynamicMethod->fnPtr = (void *) netty_epoll_native_recvmmsg0;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket;)I", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IZL", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvmsg0";
    dynamicMethod->fnPtr = (void *) netty_epoll_native_recvmsg0;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    return dynamicMethods;
error:
    free(dynamicTypeName);
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    return NULL;
}

// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Native to reflect that.
static jint netty_epoll_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    int linuxsocketOnLoadCalled = 0;
    char* nettyClassName = NULL;
    jclass nativeDatagramPacketCls = NULL;
    JNINativeMethod* dynamicMethods = NULL;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
    }
    staticallyRegistered = 1;

    // Register the methods which are not referenced by static member variables
    dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }

    if (netty_jni_util_register_natives(env,
            packagePrefix,
            NATIVE_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }
    nativeRegistered = 1;

    if (netty_epoll_linuxsocket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    linuxsocketOnLoadCalled = 1;

    // Initialize this module
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/epoll/NativeDatagramPacketArray$NativeDatagramPacket", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, nativeDatagramPacketCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetSenderAddrFieldId, "senderAddr", "[B", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetSenderAddrLenFieldId, "senderAddrLen", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetSenderScopeIdFieldId, "senderScopeId", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetSenderPortFieldId, "senderPort", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetRecipientAddrFieldId, "recipientAddr", "[B", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetRecipientAddrLenFieldId, "recipientAddrLen", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetRecipientScopeIdFieldId, "recipientScopeId", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetRecipientPortFieldId, "recipientPort", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetSegmentSizeFieldId, "segmentSize", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetMemoryAddressFieldId, "memoryAddress", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, nativeDatagramPacketCls, packetCountFieldId, "count", "I", done);

    ret = NETTY_JNI_UTIL_JNI_VERSION;

    if (packagePrefix != NULL) {
        staticPackagePrefix = strdup(packagePrefix);
    }
done:

    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(nettyClassName);

    if (ret == JNI_ERR) {
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, NATIVE_CLASSNAME);
        }
        if (linuxsocketOnLoadCalled == 1) {
            netty_epoll_linuxsocket_JNI_OnUnLoad(env, packagePrefix);
        }
        packetSenderAddrFieldId = NULL;
        packetSenderAddrLenFieldId = NULL;
        packetSenderScopeIdFieldId = NULL;
        packetSenderPortFieldId = NULL;
        packetRecipientAddrFieldId = NULL;
        packetRecipientAddrLenFieldId = NULL;
        packetRecipientScopeIdFieldId = NULL;
        packetRecipientPortFieldId = NULL;
        packetSegmentSizeFieldId = NULL;
        packetMemoryAddressFieldId = NULL;
        packetCountFieldId = NULL;
    }
    return ret;
}

static void netty_epoll_native_JNI_OnUnload(JNIEnv* env) {
    netty_epoll_linuxsocket_JNI_OnUnLoad(env, staticPackagePrefix);

    if (register_unix_called == 1) {
        register_unix_called = 0;
        netty_unix_unregister(env, staticPackagePrefix);
    }

    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, NATIVE_CLASSNAME);

    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }

    packetSenderAddrFieldId = NULL;
    packetSenderAddrLenFieldId = NULL;
    packetSenderScopeIdFieldId = NULL;
    packetSenderPortFieldId = NULL;
    packetRecipientAddrFieldId = NULL;
    packetRecipientAddrLenFieldId = NULL;
    packetRecipientScopeIdFieldId = NULL;
    packetRecipientPortFieldId = NULL;
    packetSegmentSizeFieldId = NULL;
    packetMemoryAddressFieldId = NULL;
    packetCountFieldId = NULL;
}

// Invoked by the JVM when statically linked

// We build with -fvisibility=hidden so ensure we mark everything that needs to be visible with JNIEXPORT
// https://mail.openjdk.java.net/pipermail/core-libs-dev/2013-February/014549.html

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_epoll(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_epoll", netty_epoll_native_JNI_OnLoad);
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_epoll(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_epoll_native_JNI_OnUnload);
}

#ifndef NETTY_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, "netty_transport_native_epoll", netty_epoll_native_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_epoll_native_JNI_OnUnload);
}
#endif /* NETTY_BUILD_STATIC */

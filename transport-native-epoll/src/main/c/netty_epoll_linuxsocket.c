/*
 * Copyright 2016 The Netty Project
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

/*
 * Since glibc 2.8, the _GNU_SOURCE feature test macro must be defined
 * (before including any header files) in order to obtain the
 * definition of the ucred structure. See <a href=https://linux.die.net/man/7/unix>
 */
#define _GNU_SOURCE

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <netinet/in.h>
#include <sys/sendfile.h>
#include <linux/tcp.h> // TCP_NOTSENT_LOWAT is a linux specific define

#include "netty_epoll_linuxsocket.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"

#define LINUXSOCKET_CLASSNAME "io/netty/channel/epoll/LinuxSocket"

// TCP_FASTOPEN is defined in linux 3.7. We define this here so older kernels can compile.
#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

// TCP_FASTOPEN_CONNECT is defined in linux 4.11. We define this here so older kernels can compile.
#ifndef TCP_FASTOPEN_CONNECT
#define TCP_FASTOPEN_CONNECT 30
#endif

// TCP_NOTSENT_LOWAT is defined in linux 3.12. We define this here so older kernels can compile.
#ifndef TCP_NOTSENT_LOWAT
#define TCP_NOTSENT_LOWAT 25
#endif

// SO_BUSY_POLL is defined in linux 3.11. We define this here so older kernels can compile.
#ifndef SO_BUSY_POLL
#define SO_BUSY_POLL 46
#endif

static jclass peerCredentialsClass = NULL;
static jmethodID peerCredentialsMethodId = NULL;

static jfieldID fileChannelFieldId = NULL;
static jfieldID transferredFieldId = NULL;
static jfieldID fdFieldId = NULL;
static jfieldID fileDescriptorFieldId = NULL;

// JNI Registered Methods Begin
static void netty_epoll_linuxsocket_setTimeToLive(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_TTL, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setIpMulticastLoop(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jint optval) {
    if (ipv6 == JNI_TRUE) {
        u_int val = (u_int) optval;
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_MULTICAST_LOOP, &val, sizeof(val));
    } else {
        u_char val = (u_char) optval;
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_MULTICAST_LOOP, &val, sizeof(val));
    }
}

static void netty_epoll_linuxsocket_setInterface(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray interfaceAddress, jint scopeId, jint interfaceIndex) {
    struct sockaddr_storage interfaceAddr;
    socklen_t interfaceAddrSize;
    struct sockaddr_in* interfaceIpAddr;

    memset(&interfaceAddr, 0, sizeof(interfaceAddr));

    if (ipv6 == JNI_TRUE) {
        if (interfaceIndex == -1) {
           netty_unix_errors_throwIOException(env, "Unable to find network index");
           return;
        }
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_MULTICAST_IF, &interfaceIndex, sizeof(interfaceIndex));
    } else {
        if (netty_unix_socket_initSockaddr(env, ipv6, interfaceAddress, scopeId, 0, &interfaceAddr, &interfaceAddrSize) == -1) {
            netty_unix_errors_throwIOException(env, "Could not init sockaddr");
            return;
        }

        interfaceIpAddr = (struct sockaddr_in*) &interfaceAddr;
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_MULTICAST_IF, &interfaceIpAddr->sin_addr, sizeof(interfaceIpAddr->sin_addr));
    }
}

static void netty_epoll_linuxsocket_setTcpCork(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_CORK, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpQuickAck(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_QUICKACK, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpDeferAccept(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_DEFER_ACCEPT, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_NOTSENT_LOWAT, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpFastOpen(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_FASTOPEN, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setTcpUserTimeout(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_USER_TIMEOUT, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setIpFreeBind(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_FREEBIND, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setIpTransparent(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_IP, IP_TRANSPARENT, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setIpRecvOrigDestAddr(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_RECVORIGDSTADDR, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_setSoBusyPoll(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_BUSY_POLL, &optval, sizeof(optval));
}

static void netty_epoll_linuxsocket_joinGroup(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray groupAddress, jbyteArray interfaceAddress, jint scopeId, jint interfaceIndex) {
    struct sockaddr_storage groupAddr;
    socklen_t groupAddrSize;
    struct sockaddr_storage interfaceAddr;
    socklen_t interfaceAddrSize;
    struct sockaddr_in* groupIpAddr;
    struct sockaddr_in* interfaceIpAddr;
    struct ip_mreq mreq;

    struct sockaddr_in6* groupIp6Addr;
    struct ipv6_mreq mreq6;

    memset(&groupAddr, 0, sizeof(groupAddr));
    memset(&interfaceAddr, 0, sizeof(interfaceAddr));

    if (netty_unix_socket_initSockaddr(env, ipv6, groupAddress, scopeId, 0, &groupAddr, &groupAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for groupAddress");
        return;
    }

    switch (groupAddr.ss_family) {
    case AF_INET:
        if (netty_unix_socket_initSockaddr(env, ipv6, interfaceAddress, scopeId, 0, &interfaceAddr, &interfaceAddrSize) == -1) {
            netty_unix_errors_throwIOException(env, "Could not init sockaddr for interfaceAddr");
            return;
        }

        interfaceIpAddr = (struct sockaddr_in*) &interfaceAddr;
        groupIpAddr = (struct sockaddr_in*) &groupAddr;

        memcpy(&mreq.imr_multiaddr, &groupIpAddr->sin_addr, sizeof(groupIpAddr->sin_addr));
        memcpy(&mreq.imr_interface, &interfaceIpAddr->sin_addr, sizeof(interfaceIpAddr->sin_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq));
        break;
    case AF_INET6:
        if (interfaceIndex == -1) {
            netty_unix_errors_throwIOException(env, "Unable to find network index");
            return;
        }
        mreq6.ipv6mr_interface = interfaceIndex;

        groupIp6Addr = (struct sockaddr_in6*) &groupAddr;
        memcpy(&mreq6.ipv6mr_multiaddr, &groupIp6Addr->sin6_addr, sizeof(groupIp6Addr->sin6_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_JOIN_GROUP, &mreq6, sizeof(mreq6));
        break;
    default:
        netty_unix_errors_throwIOException(env, "Address family not supported");
        break;
    }
}

static void netty_epoll_linuxsocket_joinSsmGroup(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray groupAddress, jbyteArray interfaceAddress, jint scopeId, jint interfaceIndex, jbyteArray sourceAddress) {
    struct sockaddr_storage groupAddr;
    socklen_t groupAddrSize;
    struct sockaddr_storage interfaceAddr;
    socklen_t interfaceAddrSize;
    struct sockaddr_storage sourceAddr;
    socklen_t sourceAddrSize;
    struct sockaddr_in* groupIpAddr;
    struct sockaddr_in* interfaceIpAddr;
    struct sockaddr_in* sourceIpAddr;
    struct ip_mreq_source mreq;

    struct group_source_req mreq6;

    memset(&groupAddr, 0, sizeof(groupAddr));
    memset(&sourceAddr, 0, sizeof(sourceAddr));
    memset(&interfaceAddr, 0, sizeof(interfaceAddr));

    if (netty_unix_socket_initSockaddr(env, ipv6, groupAddress, scopeId, 0, &groupAddr, &groupAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for groupAddress");
        return;
    }

    if (netty_unix_socket_initSockaddr(env, ipv6, sourceAddress, scopeId, 0, &sourceAddr, &sourceAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for sourceAddress");
        return;
    }

    switch (groupAddr.ss_family) {
    case AF_INET:
        if (netty_unix_socket_initSockaddr(env, ipv6, interfaceAddress, scopeId, 0, &interfaceAddr, &interfaceAddrSize) == -1) {
            netty_unix_errors_throwIOException(env, "Could not init sockaddr for interfaceAddress");
            return;
        }
        interfaceIpAddr = (struct sockaddr_in*) &interfaceAddr;
        groupIpAddr = (struct sockaddr_in*) &groupAddr;
        sourceIpAddr = (struct sockaddr_in*) &sourceAddr;
        memcpy(&mreq.imr_multiaddr, &groupIpAddr->sin_addr, sizeof(groupIpAddr->sin_addr));
        memcpy(&mreq.imr_interface, &interfaceIpAddr->sin_addr, sizeof(interfaceIpAddr->sin_addr));
        memcpy(&mreq.imr_sourceaddr, &sourceIpAddr->sin_addr, sizeof(sourceIpAddr->sin_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_ADD_SOURCE_MEMBERSHIP, &mreq, sizeof(mreq));
        break;
    case AF_INET6:
        if (interfaceIndex == -1) {
            netty_unix_errors_throwIOException(env, "Unable to find network index");
            return;
        }
        mreq6.gsr_group = groupAddr;
        mreq6.gsr_interface = interfaceIndex;
        mreq6.gsr_source = sourceAddr;
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, MCAST_JOIN_SOURCE_GROUP, &mreq6, sizeof(mreq6));
        break;
    default:
        netty_unix_errors_throwIOException(env, "Address family not supported");
        break;
    }
}

static void netty_epoll_linuxsocket_leaveGroup(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray groupAddress, jbyteArray interfaceAddress, jint scopeId, jint interfaceIndex) {
    struct sockaddr_storage groupAddr;
    socklen_t groupAddrSize;

    struct sockaddr_storage interfaceAddr;
    socklen_t interfaceAddrSize;
    struct sockaddr_in* groupIpAddr;
    struct sockaddr_in* interfaceIpAddr;
    struct ip_mreq mreq;

    struct sockaddr_in6* groupIp6Addr;
    struct ipv6_mreq mreq6;

    memset(&groupAddr, 0, sizeof(groupAddr));
    memset(&interfaceAddr, 0, sizeof(interfaceAddr));

    if (netty_unix_socket_initSockaddr(env, ipv6, groupAddress, scopeId, 0, &groupAddr, &groupAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for groupAddress");
        return;
    }

    switch (groupAddr.ss_family) {
    case AF_INET:
        if (netty_unix_socket_initSockaddr(env, ipv6, interfaceAddress, scopeId, 0, &interfaceAddr, &interfaceAddrSize) == -1) {
            netty_unix_errors_throwIOException(env, "Could not init sockaddr for interfaceAddress");
            return;
        }
        interfaceIpAddr = (struct sockaddr_in*) &interfaceAddr;
        groupIpAddr = (struct sockaddr_in*) &groupAddr;

        memcpy(&mreq.imr_multiaddr, &groupIpAddr->sin_addr, sizeof(groupIpAddr->sin_addr));
        memcpy(&mreq.imr_interface, &interfaceIpAddr->sin_addr, sizeof(interfaceIpAddr->sin_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_DROP_MEMBERSHIP, &mreq, sizeof(mreq));
        break;
    case AF_INET6:
        if (interfaceIndex == -1) {
            netty_unix_errors_throwIOException(env, "Unable to find network index");
            return;
        }
        mreq6.ipv6mr_interface = interfaceIndex;

        groupIp6Addr = (struct sockaddr_in6*) &groupAddr;
        memcpy(&mreq6.ipv6mr_multiaddr, &groupIp6Addr->sin6_addr, sizeof(groupIp6Addr->sin6_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_LEAVE_GROUP, &mreq6, sizeof(mreq6));
        break;
    default:
        netty_unix_errors_throwIOException(env, "Address family not supported");
        break;
    }
}

static void netty_epoll_linuxsocket_leaveSsmGroup(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray groupAddress, jbyteArray interfaceAddress, jint scopeId, jint interfaceIndex, jbyteArray sourceAddress) {
    struct sockaddr_storage groupAddr;
    socklen_t groupAddrSize;
    struct sockaddr_storage interfaceAddr;
    socklen_t interfaceAddrSize;
    struct sockaddr_storage sourceAddr;
    socklen_t sourceAddrSize;
    struct sockaddr_in* groupIpAddr;
    struct sockaddr_in* interfaceIpAddr;
    struct sockaddr_in* sourceIpAddr;

    struct ip_mreq_source mreq;
    struct group_source_req mreq6;

    memset(&groupAddr, 0, sizeof(groupAddr));
    memset(&sourceAddr, 0, sizeof(sourceAddr));
    memset(&interfaceAddr, 0, sizeof(interfaceAddr));


    if (netty_unix_socket_initSockaddr(env, ipv6, groupAddress, scopeId, 0, &groupAddr, &groupAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for groupAddress");
        return;
    }

    if (netty_unix_socket_initSockaddr(env, ipv6, sourceAddress, scopeId, 0, &sourceAddr, &sourceAddrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr for sourceAddress");
        return;
    }

    switch (groupAddr.ss_family) {
    case AF_INET:
        if (netty_unix_socket_initSockaddr(env, ipv6, interfaceAddress, scopeId, 0, &interfaceAddr, &interfaceAddrSize) == -1) {
            netty_unix_errors_throwIOException(env, "Could not init sockaddr for interfaceAddress");
            return;
        }
        interfaceIpAddr = (struct sockaddr_in*) &interfaceAddr;

        groupIpAddr = (struct sockaddr_in*) &groupAddr;
        sourceIpAddr = (struct sockaddr_in*) &sourceAddr;
        memcpy(&mreq.imr_multiaddr, &groupIpAddr->sin_addr, sizeof(groupIpAddr->sin_addr));
        memcpy(&mreq.imr_interface, &interfaceIpAddr->sin_addr, sizeof(interfaceIpAddr->sin_addr));
        memcpy(&mreq.imr_sourceaddr, &sourceIpAddr->sin_addr, sizeof(sourceIpAddr->sin_addr));
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_DROP_SOURCE_MEMBERSHIP, &mreq, sizeof(mreq));
        break;
    case AF_INET6:
        if (interfaceIndex == -1) {
            netty_unix_errors_throwIOException(env, "Unable to find network index");
            return;
        }

        mreq6.gsr_group = groupAddr;
        mreq6.gsr_interface = interfaceIndex;
        mreq6.gsr_source = sourceAddr;
        netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, MCAST_LEAVE_SOURCE_GROUP, &mreq6, sizeof(mreq6));
        break;
    default:
        netty_unix_errors_throwIOException(env, "Address family not supported");
        break;
    }
}

static void netty_epoll_linuxsocket_setTcpMd5Sig(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray address, jint scopeId, jbyteArray key) {
    struct sockaddr_storage addr;
    socklen_t addrSize;

    memset(&addr, 0, sizeof(addr));

    if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, 0, &addr, &addrSize) == -1) {
        netty_unix_errors_throwIOException(env, "Could not init sockaddr");
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
        netty_unix_errors_throwIOExceptionErrorNo(env, "setsockopt() failed: ", errno);
    }
}

static int netty_epoll_linuxsocket_getInterface(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6) {
    if (ipv6 == JNI_TRUE) {
        int optval;
        if (netty_unix_socket_getOption(env, fd, IPPROTO_IPV6, IPV6_MULTICAST_IF, &optval, sizeof(optval)) == -1) {
            return -1;
        }
        return optval;
    } else {
        struct in_addr optval;
        if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_MULTICAST_IF, &optval, sizeof(optval)) == -1) {
            return -1;
        }

        return ntohl(optval.s_addr);
    }
}

static jint netty_epoll_linuxsocket_getTimeToLive(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_TTL, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}


static jint netty_epoll_linuxsocket_getIpMulticastLoop(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6) {
    if (ipv6 == JNI_TRUE) {
        u_int optval;
        if (netty_unix_socket_getOption(env, fd, IPPROTO_IPV6, IPV6_MULTICAST_LOOP, &optval, sizeof(optval)) == -1) {
            return -1;
        }
        return (jint) optval;
    } else {
        u_char optval;
        if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_MULTICAST_LOOP, &optval, sizeof(optval)) == -1) {
            return -1;
        }
        return (jint) optval;
    }
}

static jint netty_epoll_linuxsocket_getTcpKeepIdle(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_getTcpKeepIntvl(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_getTcpKeepCnt(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_linuxsocket_getTcpUserTimeout(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_USER_TIMEOUT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_linuxsocket_isIpFreeBind(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_FREEBIND, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_linuxsocket_isIpTransparent(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, SOL_IP, IP_TRANSPARENT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static jint netty_epoll_linuxsocket_isIpRecvOrigDestAddr(JNIEnv* env, jclass clazz, jint fd) {
     int optval;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_RECVORIGDSTADDR, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

static void netty_epoll_linuxsocket_getTcpInfo(JNIEnv* env, jclass clazz, jint fd, jlongArray array) {
     struct tcp_info tcp_info;
     if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_INFO, &tcp_info, sizeof(tcp_info)) == -1) {
         return;
     }
     jlong cArray[32];
     // Expand to 64 bits, then cast away unsigned-ness.
     cArray[0] = (jlong) (uint64_t) tcp_info.tcpi_state;
     cArray[1] = (jlong) (uint64_t) tcp_info.tcpi_ca_state;
     cArray[2] = (jlong) (uint64_t) tcp_info.tcpi_retransmits;
     cArray[3] = (jlong) (uint64_t) tcp_info.tcpi_probes;
     cArray[4] = (jlong) (uint64_t) tcp_info.tcpi_backoff;
     cArray[5] = (jlong) (uint64_t) tcp_info.tcpi_options;
     cArray[6] = (jlong) (uint64_t) tcp_info.tcpi_snd_wscale;
     cArray[7] = (jlong) (uint64_t) tcp_info.tcpi_rcv_wscale;
     cArray[8] = (jlong) (uint64_t) tcp_info.tcpi_rto;
     cArray[9] = (jlong) (uint64_t) tcp_info.tcpi_ato;
     cArray[10] = (jlong) (uint64_t) tcp_info.tcpi_snd_mss;
     cArray[11] = (jlong) (uint64_t) tcp_info.tcpi_rcv_mss;
     cArray[12] = (jlong) (uint64_t) tcp_info.tcpi_unacked;
     cArray[13] = (jlong) (uint64_t) tcp_info.tcpi_sacked;
     cArray[14] = (jlong) (uint64_t) tcp_info.tcpi_lost;
     cArray[15] = (jlong) (uint64_t) tcp_info.tcpi_retrans;
     cArray[16] = (jlong) (uint64_t) tcp_info.tcpi_fackets;
     cArray[17] = (jlong) (uint64_t) tcp_info.tcpi_last_data_sent;
     cArray[18] = (jlong) (uint64_t) tcp_info.tcpi_last_ack_sent;
     cArray[19] = (jlong) (uint64_t) tcp_info.tcpi_last_data_recv;
     cArray[20] = (jlong) (uint64_t) tcp_info.tcpi_last_ack_recv;
     cArray[21] = (jlong) (uint64_t) tcp_info.tcpi_pmtu;
     cArray[22] = (jlong) (uint64_t) tcp_info.tcpi_rcv_ssthresh;
     cArray[23] = (jlong) (uint64_t) tcp_info.tcpi_rtt;
     cArray[24] = (jlong) (uint64_t) tcp_info.tcpi_rttvar;
     cArray[25] = (jlong) (uint64_t) tcp_info.tcpi_snd_ssthresh;
     cArray[26] = (jlong) (uint64_t) tcp_info.tcpi_snd_cwnd;
     cArray[27] = (jlong) (uint64_t) tcp_info.tcpi_advmss;
     cArray[28] = (jlong) (uint64_t) tcp_info.tcpi_reordering;
     cArray[29] = (jlong) (uint64_t) tcp_info.tcpi_rcv_rtt;
     cArray[30] = (jlong) (uint64_t) tcp_info.tcpi_rcv_space;
     cArray[31] = (jlong) (uint64_t) tcp_info.tcpi_total_retrans;

     (*env)->SetLongArrayRegion(env, array, 0, 32, cArray);
}

static jint netty_epoll_linuxsocket_isTcpCork(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_CORK, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_getSoBusyPoll(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_BUSY_POLL, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_getTcpDeferAccept(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_DEFER_ACCEPT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_isTcpQuickAck(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_QUICKACK, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_epoll_linuxsocket_getTcpNotSentLowAt(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_NOTSENT_LOWAT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jobject netty_epoll_linuxsocket_getPeerCredentials(JNIEnv *env, jclass clazz, jint fd) {
     struct ucred credentials;
     if(netty_unix_socket_getOption(env,fd, SOL_SOCKET, SO_PEERCRED, &credentials, sizeof (credentials)) == -1) {
         return NULL;
     }
     jintArray gids = (*env)->NewIntArray(env, 1);
     (*env)->SetIntArrayRegion(env, gids, 0, 1, (jint*) &credentials.gid);
     return (*env)->NewObject(env, peerCredentialsClass, peerCredentialsMethodId, credentials.pid, credentials.uid, gids);
}

static jlong netty_epoll_linuxsocket_sendFile(JNIEnv* env, jclass clazz, jint fd, jobject fileRegion, jlong base_off, jlong off, jlong len) {
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
        // update the transferred field in DefaultFileRegion
        (*env)->SetLongField(env, fileRegion, transferredFieldId, off + res);
    }

    return res;
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "setTimeToLive", "(II)V", (void *) netty_epoll_linuxsocket_setTimeToLive },
  { "getTimeToLive", "(I)I", (void *) netty_epoll_linuxsocket_getTimeToLive },
  { "setInterface", "(IZ[BII)V", (void *) netty_epoll_linuxsocket_setInterface },
  { "getInterface", "(IZ)I", (void *) netty_epoll_linuxsocket_getInterface },
  { "setIpMulticastLoop", "(IZI)V", (void * ) netty_epoll_linuxsocket_setIpMulticastLoop },
  { "getIpMulticastLoop", "(IZ)I", (void * ) netty_epoll_linuxsocket_getIpMulticastLoop },
  { "setTcpCork", "(II)V", (void *) netty_epoll_linuxsocket_setTcpCork },
  { "setSoBusyPoll", "(II)V", (void *) netty_epoll_linuxsocket_setSoBusyPoll },
  { "setTcpQuickAck", "(II)V", (void *) netty_epoll_linuxsocket_setTcpQuickAck },
  { "setTcpDeferAccept", "(II)V", (void *) netty_epoll_linuxsocket_setTcpDeferAccept },
  { "setTcpNotSentLowAt", "(II)V", (void *) netty_epoll_linuxsocket_setTcpNotSentLowAt },
  { "isTcpCork", "(I)I", (void *) netty_epoll_linuxsocket_isTcpCork },
  { "getSoBusyPoll", "(I)I", (void *) netty_epoll_linuxsocket_getSoBusyPoll },
  { "getTcpDeferAccept", "(I)I", (void *) netty_epoll_linuxsocket_getTcpDeferAccept },
  { "getTcpNotSentLowAt", "(I)I", (void *) netty_epoll_linuxsocket_getTcpNotSentLowAt },
  { "isTcpQuickAck", "(I)I", (void *) netty_epoll_linuxsocket_isTcpQuickAck },
  { "setTcpFastOpen", "(II)V", (void *) netty_epoll_linuxsocket_setTcpFastOpen },
  { "setTcpKeepIdle", "(II)V", (void *) netty_epoll_linuxsocket_setTcpKeepIdle },
  { "setTcpKeepIntvl", "(II)V", (void *) netty_epoll_linuxsocket_setTcpKeepIntvl },
  { "setTcpKeepCnt", "(II)V", (void *) netty_epoll_linuxsocket_setTcpKeepCnt },
  { "setTcpUserTimeout", "(II)V", (void *) netty_epoll_linuxsocket_setTcpUserTimeout },
  { "setIpFreeBind", "(II)V", (void *) netty_epoll_linuxsocket_setIpFreeBind },
  { "setIpTransparent", "(II)V", (void *) netty_epoll_linuxsocket_setIpTransparent },
  { "setIpRecvOrigDestAddr", "(II)V", (void *) netty_epoll_linuxsocket_setIpRecvOrigDestAddr },
  { "getTcpKeepIdle", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepIdle },
  { "getTcpKeepIntvl", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepIntvl },
  { "getTcpKeepCnt", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepCnt },
  { "getTcpUserTimeout", "(I)I", (void *) netty_epoll_linuxsocket_getTcpUserTimeout },
  { "isIpFreeBind", "(I)I", (void *) netty_epoll_linuxsocket_isIpFreeBind },
  { "isIpTransparent", "(I)I", (void *) netty_epoll_linuxsocket_isIpTransparent },
  { "isIpRecvOrigDestAddr", "(I)I", (void *) netty_epoll_linuxsocket_isIpRecvOrigDestAddr },
  { "getTcpInfo", "(I[J)V", (void *) netty_epoll_linuxsocket_getTcpInfo },
  { "setTcpMd5Sig", "(IZ[BI[B)V", (void *) netty_epoll_linuxsocket_setTcpMd5Sig },
  { "joinGroup", "(IZ[B[BII)V", (void *) netty_epoll_linuxsocket_joinGroup },
  { "joinSsmGroup", "(IZ[B[BII[B)V", (void *) netty_epoll_linuxsocket_joinSsmGroup },
  { "leaveGroup", "(IZ[B[BII)V", (void *) netty_epoll_linuxsocket_leaveGroup },
  { "leaveSsmGroup", "(IZ[B[BII[B)V", (void *) netty_epoll_linuxsocket_leaveSsmGroup }
  // "sendFile" has a dynamic signature
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2; // 2 is for the dynamic method signatures.
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
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/PeerCredentials;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(I)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "getPeerCredentials";
    dynamicMethod->fnPtr = (void *) netty_epoll_linuxsocket_getPeerCredentials;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/DefaultFileRegion;JJJ)J", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IL", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "sendFile";
    dynamicMethod->fnPtr = (void *) netty_epoll_linuxsocket_sendFile;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);
    return dynamicMethods;
error:
    free(dynamicTypeName);
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    return NULL;
}

// JNI Method Registration Table End

jint netty_epoll_linuxsocket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    char* nettyClassName = NULL;
    jclass fileRegionCls = NULL;
    jclass fileChannelCls = NULL;
    jclass fileDescriptorCls = NULL;
    // Register the methods which are not referenced by static member variables
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            LINUXSOCKET_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/PeerCredentials", nettyClassName, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, peerCredentialsClass, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_METHOD(env, peerCredentialsClass, peerCredentialsMethodId, "<init>", "(II[I)V", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/DefaultFileRegion", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, fileRegionCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_FIELD(env, fileRegionCls, fileChannelFieldId, "file", "Ljava/nio/channels/FileChannel;", done);
    NETTY_JNI_UTIL_GET_FIELD(env, fileRegionCls, transferredFieldId, "transferred", "J", done);

    NETTY_JNI_UTIL_FIND_CLASS(env, fileChannelCls, "sun/nio/ch/FileChannelImpl", done);
    NETTY_JNI_UTIL_GET_FIELD(env, fileChannelCls, fileDescriptorFieldId, "fd", "Ljava/io/FileDescriptor;", done);

    NETTY_JNI_UTIL_FIND_CLASS(env, fileDescriptorCls, "java/io/FileDescriptor", done);
    NETTY_JNI_UTIL_TRY_GET_FIELD(env, fileDescriptorCls, fdFieldId, "fd", "I");
    if (fdFieldId == NULL) {
         // Android uses a different field name, let's try it.
         NETTY_JNI_UTIL_GET_FIELD(env, fileDescriptorCls, fdFieldId, "descriptor", "I", done);
    }
    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(nettyClassName);

    return ret;
}

void netty_epoll_linuxsocket_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, peerCredentialsClass);

    netty_jni_util_unregister_natives(env, packagePrefix, LINUXSOCKET_CLASSNAME);
}

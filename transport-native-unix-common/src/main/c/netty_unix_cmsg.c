/*
 * Copyright 2026 The Netty Project
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

// Needed to get struct in_pktinfo and struct in6_pktinfo on linux.
#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

// Needed to get the definitions of RFC 3542 (Advanced Sockets API for IPv6) like IPV6_RECVPKTINFO on macOS.
#ifdef __APPLE__
#define __APPLE_USE_RFC_3542
#endif

#include <stddef.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>

#ifdef __linux__
// Needed for UDP_SEGMENT
#include <netinet/udp.h>
#endif

#include "netty_unix_cmsg.h"
#include "netty_unix_jni.h"
#include "netty_jni_util.h"

#define CMSG_CLASSNAME "io/netty/channel/unix/ControlMessageStaticallyReferencedJniMethods"

#ifdef __linux__
// Allow to compile on systems with older kernel headers.
#ifndef UDP_SEGMENT
#define UDP_SEGMENT 103
#endif
#endif // __linux__

// All the functions below return -1 if the constant is not supported on the platform.

// JNI Registered Methods Begin

// struct cmsghdr and the CMSG_* macros.
static jint netty_unix_cmsg_cmsghdrSize(JNIEnv* env, jclass clazz) {
    return sizeof(struct cmsghdr);
}

static jint netty_unix_cmsg_cmsghdrOffsetofCmsgLen(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_len);
}

static jint netty_unix_cmsg_cmsghdrSizeofCmsgLen(JNIEnv* env, jclass clazz) {
    // This is size_t on linux (glibc) and socklen_t on most other platforms.
    return sizeof(((struct cmsghdr*) 0)->cmsg_len);
}

static jint netty_unix_cmsg_cmsghdrOffsetofCmsgLevel(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_level);
}

static jint netty_unix_cmsg_cmsghdrOffsetofCmsgType(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_type);
}

static jint netty_unix_cmsg_cmsgDataOffset(JNIEnv* env, jclass clazz) {
    return CMSG_LEN(0);
}

static jint netty_unix_cmsg_cmsgAlignment(JNIEnv* env, jclass clazz) {
    return CMSG_SPACE(1) - CMSG_SPACE(0);
}

// Levels
static jint netty_unix_cmsg_ipprotoIp(JNIEnv* env, jclass clazz) {
    return IPPROTO_IP;
}

static jint netty_unix_cmsg_ipprotoIpv6(JNIEnv* env, jclass clazz) {
    return IPPROTO_IPV6;
}

static jint netty_unix_cmsg_solUdp(JNIEnv* env, jclass clazz) {
#ifdef SOL_UDP
    return SOL_UDP;
#else
    return IPPROTO_UDP;
#endif
}

// IPPROTO_IP
static jint netty_unix_cmsg_ipTos(JNIEnv* env, jclass clazz) {
    return IP_TOS;
}

static jint netty_unix_cmsg_ipTosDataSize(JNIEnv* env, jclass clazz) {
    // The size of the data of an IP_TOS cmsg when sending.
#if defined(__FreeBSD__) || defined(__DragonFly__)
    return sizeof(unsigned char);
#else
    return sizeof(int);
#endif
}

static jint netty_unix_cmsg_ipRecvTos(JNIEnv* env, jclass clazz) {
#ifdef IP_RECVTOS
    return IP_RECVTOS;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipRecvTosCmsgType(JNIEnv* env, jclass clazz) {
    // The type of the cmsg that is used when receiving the TOS.
#ifdef __linux__
    return IP_TOS;
#elif defined(IP_RECVTOS)
    return IP_RECVTOS;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipTtlSendType(JNIEnv* env, jclass clazz) {
    // The type of the cmsg that is used when sending the TTL, which is only supported on linux.
#ifdef __linux__
    return IP_TTL;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipRecvTtl(JNIEnv* env, jclass clazz) {
#ifdef IP_RECVTTL
    return IP_RECVTTL;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipRecvTtlCmsgType(JNIEnv* env, jclass clazz) {
    // The type of the cmsg that is used when receiving the TTL.
#ifdef __linux__
    return IP_TTL;
#elif defined(IP_RECVTTL)
    return IP_RECVTTL;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipPktinfo(JNIEnv* env, jclass clazz) {
#ifdef IP_PKTINFO
    return IP_PKTINFO;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipRecvPktinfo(JNIEnv* env, jclass clazz) {
    // The socket option that needs to be enabled to receive IP_PKTINFO.
#ifdef IP_RECVPKTINFO
    return IP_RECVPKTINFO;
#elif defined(IP_PKTINFO)
    return IP_PKTINFO;
#else
    return -1;
#endif
}

// IPPROTO_IPV6
static jint netty_unix_cmsg_ipv6Tclass(JNIEnv* env, jclass clazz) {
#ifdef IPV6_TCLASS
    return IPV6_TCLASS;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipv6RecvTclass(JNIEnv* env, jclass clazz) {
#ifdef IPV6_RECVTCLASS
    return IPV6_RECVTCLASS;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipv6Hoplimit(JNIEnv* env, jclass clazz) {
#ifdef IPV6_HOPLIMIT
    return IPV6_HOPLIMIT;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipv6RecvHoplimit(JNIEnv* env, jclass clazz) {
#ifdef IPV6_RECVHOPLIMIT
    return IPV6_RECVHOPLIMIT;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipv6Pktinfo(JNIEnv* env, jclass clazz) {
#ifdef IPV6_PKTINFO
    return IPV6_PKTINFO;
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_ipv6RecvPktinfo(JNIEnv* env, jclass clazz) {
#ifdef IPV6_RECVPKTINFO
    return IPV6_RECVPKTINFO;
#else
    return -1;
#endif
}

// SOL_UDP
static jint netty_unix_cmsg_udpSegment(JNIEnv* env, jclass clazz) {
#ifdef __linux__
    // UDP_SEGMENT is only supported since linux 4.18, so check if the kernel supports it.
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd == -1) {
        fd = socket(AF_INET6, SOCK_DGRAM, 0);
        if (fd == -1) {
            return -1;
        }
    }
    int gso_size = 512;
    int ret = setsockopt(fd, SOL_UDP, UDP_SEGMENT, &gso_size, sizeof(gso_size));
    close(fd);
    return ret == -1 ? -1 : UDP_SEGMENT;
#else
    return -1;
#endif
}

// struct in_pktinfo
static jint netty_unix_cmsg_sizeofInPktinfo(JNIEnv* env, jclass clazz) {
#ifdef IP_PKTINFO
    return sizeof(struct in_pktinfo);
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_inPktinfoOffsetofIpiIfindex(JNIEnv* env, jclass clazz) {
#ifdef IP_PKTINFO
    return offsetof(struct in_pktinfo, ipi_ifindex);
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_inPktinfoOffsetofIpiSpecDst(JNIEnv* env, jclass clazz) {
#ifdef IP_PKTINFO
    return offsetof(struct in_pktinfo, ipi_spec_dst);
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_inPktinfoOffsetofIpiAddr(JNIEnv* env, jclass clazz) {
#ifdef IP_PKTINFO
    return offsetof(struct in_pktinfo, ipi_addr);
#else
    return -1;
#endif
}

// struct in6_pktinfo
static jint netty_unix_cmsg_sizeofIn6Pktinfo(JNIEnv* env, jclass clazz) {
#ifdef IPV6_PKTINFO
    return sizeof(struct in6_pktinfo);
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_in6PktinfoOffsetofIpi6Addr(JNIEnv* env, jclass clazz) {
#ifdef IPV6_PKTINFO
    return offsetof(struct in6_pktinfo, ipi6_addr);
#else
    return -1;
#endif
}

static jint netty_unix_cmsg_in6PktinfoOffsetofIpi6Ifindex(JNIEnv* env, jclass clazz) {
#ifdef IPV6_PKTINFO
    return offsetof(struct in6_pktinfo, ipi6_ifindex);
#else
    return -1;
#endif
}
// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "cmsghdrSize", "()I", (void *) netty_unix_cmsg_cmsghdrSize },
  { "cmsghdrOffsetofCmsgLen", "()I", (void *) netty_unix_cmsg_cmsghdrOffsetofCmsgLen },
  { "cmsghdrSizeofCmsgLen", "()I", (void *) netty_unix_cmsg_cmsghdrSizeofCmsgLen },
  { "cmsghdrOffsetofCmsgLevel", "()I", (void *) netty_unix_cmsg_cmsghdrOffsetofCmsgLevel },
  { "cmsghdrOffsetofCmsgType", "()I", (void *) netty_unix_cmsg_cmsghdrOffsetofCmsgType },
  { "cmsgDataOffset", "()I", (void *) netty_unix_cmsg_cmsgDataOffset },
  { "cmsgAlignment", "()I", (void *) netty_unix_cmsg_cmsgAlignment },
  { "ipprotoIp", "()I", (void *) netty_unix_cmsg_ipprotoIp },
  { "ipprotoIpv6", "()I", (void *) netty_unix_cmsg_ipprotoIpv6 },
  { "solUdp", "()I", (void *) netty_unix_cmsg_solUdp },
  { "ipTos", "()I", (void *) netty_unix_cmsg_ipTos },
  { "ipTosDataSize", "()I", (void *) netty_unix_cmsg_ipTosDataSize },
  { "ipRecvTos", "()I", (void *) netty_unix_cmsg_ipRecvTos },
  { "ipRecvTosCmsgType", "()I", (void *) netty_unix_cmsg_ipRecvTosCmsgType },
  { "ipTtlSendType", "()I", (void *) netty_unix_cmsg_ipTtlSendType },
  { "ipRecvTtl", "()I", (void *) netty_unix_cmsg_ipRecvTtl },
  { "ipRecvTtlCmsgType", "()I", (void *) netty_unix_cmsg_ipRecvTtlCmsgType },
  { "ipPktinfo", "()I", (void *) netty_unix_cmsg_ipPktinfo },
  { "ipRecvPktinfo", "()I", (void *) netty_unix_cmsg_ipRecvPktinfo },
  { "ipv6Tclass", "()I", (void *) netty_unix_cmsg_ipv6Tclass },
  { "ipv6RecvTclass", "()I", (void *) netty_unix_cmsg_ipv6RecvTclass },
  { "ipv6Hoplimit", "()I", (void *) netty_unix_cmsg_ipv6Hoplimit },
  { "ipv6RecvHoplimit", "()I", (void *) netty_unix_cmsg_ipv6RecvHoplimit },
  { "ipv6Pktinfo", "()I", (void *) netty_unix_cmsg_ipv6Pktinfo },
  { "ipv6RecvPktinfo", "()I", (void *) netty_unix_cmsg_ipv6RecvPktinfo },
  { "udpSegment", "()I", (void *) netty_unix_cmsg_udpSegment },
  { "sizeofInPktinfo", "()I", (void *) netty_unix_cmsg_sizeofInPktinfo },
  { "inPktinfoOffsetofIpiIfindex", "()I", (void *) netty_unix_cmsg_inPktinfoOffsetofIpiIfindex },
  { "inPktinfoOffsetofIpiSpecDst", "()I", (void *) netty_unix_cmsg_inPktinfoOffsetofIpiSpecDst },
  { "inPktinfoOffsetofIpiAddr", "()I", (void *) netty_unix_cmsg_inPktinfoOffsetofIpiAddr },
  { "sizeofIn6Pktinfo", "()I", (void *) netty_unix_cmsg_sizeofIn6Pktinfo },
  { "in6PktinfoOffsetofIpi6Addr", "()I", (void *) netty_unix_cmsg_in6PktinfoOffsetofIpi6Addr },
  { "in6PktinfoOffsetofIpi6Ifindex", "()I", (void *) netty_unix_cmsg_in6PktinfoOffsetofIpi6Ifindex }
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Unix to reflect that.
jint netty_unix_cmsg_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            CMSG_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        return JNI_ERR;
    }

    return NETTY_JNI_UTIL_JNI_VERSION;
}

void netty_unix_cmsg_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    netty_jni_util_unregister_natives(env, packagePrefix, CMSG_CLASSNAME);
}

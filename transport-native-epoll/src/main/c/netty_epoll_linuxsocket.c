/*
 * Copyright 2016 The Netty Project
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

/*
 * Since glibc 2.8, the _GNU_SOURCE feature test macro must be defined
 * (before including any header files) in order to obtain the
 * definition of the ucred structure. See <a href=https://linux.die.net/man/7/unix>
 */
#define _GNU_SOURCE

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <linux/tcp.h> // TCP_NOTSENT_LOWAT is a linux specific define
#include <sys/ioctl.h>
#include <linux/if.h>
#include <linux/if_tun.h>

#include "netty_epoll_linuxsocket.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"

// TCP_FASTOPEN is defined in linux 3.7. We define this here so older kernels can compile.
#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

// TCP_NOTSENT_LOWAT is defined in linux 3.12. We define this here so older kernels can compile.
#ifndef TCP_NOTSENT_LOWAT
#define TCP_NOTSENT_LOWAT 25
#endif

static jclass peerCredentialsClass = NULL;
static jmethodID peerCredentialsMethodId = NULL;

// JNI Registered Methods Begin
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

static void netty_epoll_linuxsocket_setTcpMd5Sig(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jbyteArray key) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, 0, &addr, &addrSize) == -1) {
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

static jint netty_epoll_linuxsocket_openTunTapCloneDevice0(JNIEnv* env, jclass clazz, jstring cloneDevName) {
    const char* f_cloneDevName = (*env)->GetStringUTFChars(env, cloneDevName, 0);

    // Open specific clone device.  Raise an IOException if an error occurs.
    int res = open(f_cloneDevName, O_RDWR|O_NONBLOCK|O_CLOEXEC);
    if (res < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "openTunTapCloneDevice() failed: ", errno);
    }

    (*env)->ReleaseStringUTFChars(env, cloneDevName, f_cloneDevName);

    return res;
}

static jint netty_epoll_linuxsocket_allocTunTapInterface(JNIEnv *env, jclass cls, jint cloneDevFD, jstring ifName, jboolean isTapDev)
{
    jint res;
    struct ifreq ifr;
    const char *f_ifName = (*env)->GetStringUTFChars(env, ifName, NULL);

    // Initialize the IFREQ structure with the specified flags and interface name.
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_flags = ((isTapDev) ? IFF_TAP : IFF_TUN);
    strncpy(ifr.ifr_name, f_ifName, IFNAMSIZ);

    // Open/allocate the TUN/TAP device.  Raise an IOException if an error occurs.
    res = ioctl(cloneDevFD, TUNSETIFF, (void *)&ifr);
    if (res < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "allocTunTapInterface() failed: ", errno);
    }

    (*env)->ReleaseStringUTFChars(env, ifName, f_ifName);

    return res;
}

static jlong netty_epoll_linuxsocket_readTunTapPacketAddress(JNIEnv *env, jclass cls, jint fd, jlong addr, jint pos, jint limit) {
    jlong res = -1;
    struct iovec iov[2];
    struct tun_pi packetInfo;

    iov[0].iov_base = (void *) &packetInfo;
    iov[0].iov_len = sizeof(packetInfo);
    iov[1].iov_base = ((char *) addr) + pos;
    iov[1].iov_len = limit;

    // Attempt to read a packet. Loop if the read is interrupted by a signal.
    do {
        res = (jlong) readv((int) fd, iov, 2);
    } while (res < 0 && errno == EINTR);

    // If a packet was received...
    if (res >= (jlong) sizeof(packetInfo)) {

        // If the packet was truncated because the receive buffer was too small,
        // raise an IOException containing the error ENOMEM.
        if ((packetInfo.flags & TUN_PKT_STRIP) != 0) {
            netty_unix_errors_throwIOExceptionErrorNo(env, "readTunTapPacketAddress() failed: ", ENOMEM);
            res = -1;
        }

        // Otherwise return the protocol type in the upper 16 bits and the length
        // of the packet (not including the packet info header) in the lower 32 bits.
        else {
            jlong proto = htons(packetInfo.proto);
            jlong packetLen = res - sizeof(packetInfo);
            res = (proto << 32) | packetLen;
        }
    }

    // If an error occurred, return to the caller empty-handed if the kernel signaled
    // that no packet was available.  Otherwise, raise an IOException containing the error code.
    else if (res < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            res = 0;
        }
        else {
            netty_unix_errors_throwIOExceptionErrorNo(env, "readTunTapPacketAddress() failed: ", errno);
        }
    }

    // If for some reason the kernel returned less bytes than are needed to
    // convey the packet info header, treat it as if no packet had been received.
    // (Note that it's not clear the kernel will ever do this in practice,
    // therefore this code is purely defensive).
    else {
        res = 0;
    }

    return res;
}

static jlong netty_epoll_linuxsocket_readTunTapPacket(JNIEnv *env, jclass cls, jint fd, jobject jbuf, jint pos, jint limit) {
    void *buf = (*env)->GetDirectBufferAddress(env, jbuf);
    return netty_epoll_linuxsocket_readTunTapPacketAddress(env, cls, fd, (jlong)buf, pos, limit);
}

static void netty_epoll_linuxsocket_writeTunTapPacketAddress(JNIEnv *env, jclass cls, jint fd, jint protocol, jlong addr, jint pos, jint limit) {
    jint res;
    struct iovec iov[2];
    struct tun_pi packetInfo;

    memset(&packetInfo, 0, sizeof(packetInfo));
    packetInfo.flags = 0;
    packetInfo.proto = htons(protocol & 0xFFFF);

    iov[0].iov_base = (void *)&packetInfo;
    iov[0].iov_len = sizeof(packetInfo);
    iov[1].iov_base = ((char *)addr) + pos;
    iov[1].iov_len = limit;

    // Attempt to write the packet. Loop if the write is interrupted by a signal.
    do {
        res = (jint)writev((int)fd, iov, 2);
    } while (res < 0 && errno == EINTR);

    // If an error occurred raise an IOException containing the error code.
    if (res < 0) {
        netty_unix_errors_throwIOExceptionErrorNo(env, "writeTunTapPacketAddress() failed: ", errno);
    }
}

static void netty_epoll_linuxsocket_writeTunTapPacket(JNIEnv *env, jclass cls, jint fd, jint protocol, jobject jbuf, jint pos, jint limit) {
    void *buf = (*env)->GetDirectBufferAddress(env, jbuf);
    netty_epoll_linuxsocket_writeTunTapPacketAddress(env, cls, fd, protocol, (jlong)buf, pos, limit);
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "setTcpCork", "(II)V", (void *) netty_epoll_linuxsocket_setTcpCork },
  { "setTcpQuickAck", "(II)V", (void *) netty_epoll_linuxsocket_setTcpQuickAck },
  { "setTcpDeferAccept", "(II)V", (void *) netty_epoll_linuxsocket_setTcpDeferAccept },
  { "setTcpNotSentLowAt", "(II)V", (void *) netty_epoll_linuxsocket_setTcpNotSentLowAt },
  { "isTcpCork", "(I)I", (void *) netty_epoll_linuxsocket_isTcpCork },
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
  { "getTcpKeepIdle", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepIdle },
  { "getTcpKeepIntvl", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepIntvl },
  { "getTcpKeepCnt", "(I)I", (void *) netty_epoll_linuxsocket_getTcpKeepCnt },
  { "getTcpUserTimeout", "(I)I", (void *) netty_epoll_linuxsocket_getTcpUserTimeout },
  { "isIpFreeBind", "(I)I", (void *) netty_epoll_linuxsocket_isIpFreeBind },
  { "isIpTransparent", "(I)I", (void *) netty_epoll_linuxsocket_isIpTransparent },
  { "getTcpInfo", "(I[J)V", (void *) netty_epoll_linuxsocket_getTcpInfo },
  { "setTcpMd5Sig", "(I[BI[B)V", (void *) netty_epoll_linuxsocket_setTcpMd5Sig },
  { "openTunTapCloneDevice0", "(Ljava/lang/String;)I", (void *) netty_epoll_linuxsocket_openTunTapCloneDevice0 },
  { "allocTunTapInterface", "(ILjava/lang/String;Z)I", (void *) netty_epoll_linuxsocket_allocTunTapInterface },
  { "readTunTapPacket", "(ILjava/nio/ByteBuffer;II)J", (void *) netty_epoll_linuxsocket_readTunTapPacket },
  { "readTunTapPacketAddress", "(IJII)J", (void *) netty_epoll_linuxsocket_readTunTapPacketAddress },
  { "writeTunTapPacket", "(IILjava/nio/ByteBuffer;II)V", (void *) netty_epoll_linuxsocket_writeTunTapPacket },
  { "writeTunTapPacketAddress", "(IIJII)V", (void *) netty_epoll_linuxsocket_writeTunTapPacketAddress },
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 1;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * dynamicMethodsTableSize());
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/PeerCredentials;");
    dynamicMethod->name = "getPeerCredentials";
    dynamicMethod->signature = netty_unix_util_prepend("(I)L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_epoll_linuxsocket_getPeerCredentials;
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

jint netty_epoll_linuxsocket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/epoll/LinuxSocket",
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;

    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/PeerCredentials");
    jclass localPeerCredsClass = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (localPeerCredsClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    peerCredentialsClass = (jclass) (*env)->NewGlobalRef(env, localPeerCredsClass);
    if (peerCredentialsClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    peerCredentialsMethodId = (*env)->GetMethodID(env, peerCredentialsClass, "<init>", "(II[I)V");
    if (peerCredentialsMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: PeerCredentials.<init>(int, int, int[])");
        return JNI_ERR;
    }

    return NETTY_JNI_VERSION;
}

void netty_epoll_linuxsocket_JNI_OnUnLoad(JNIEnv* env) {
    if (peerCredentialsClass != NULL) {
        (*env)->DeleteGlobalRef(env, peerCredentialsClass);
        peerCredentialsClass = NULL;
    }
}

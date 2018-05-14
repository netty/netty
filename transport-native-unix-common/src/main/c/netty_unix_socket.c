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
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include "netty_unix_errors.h"
#include "netty_unix_jni.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"

// Define SO_REUSEPORT if not found to fix build issues.
// See https://github.com/netty/netty/issues/2558
#ifndef SO_REUSEPORT
#define SO_REUSEPORT 15
#endif /* SO_REUSEPORT */

static jclass datagramSocketAddressClass = NULL;
static jmethodID datagramSocketAddrMethodId = NULL;
static jmethodID inetSocketAddrMethodId = NULL;
static jclass inetSocketAddressClass = NULL;
static int socketType = AF_INET;
static const char* ip4prefix = "::ffff:";
static const unsigned char wildcardAddress[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
static const unsigned char ipv4MappedWildcardAddress[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00 };

// Optional external methods
extern int accept4(int sockFd, struct sockaddr* addr, socklen_t* addrlen, int flags) __attribute__((weak)) __attribute__((weak_import));

// macro to calculate the length of a sockaddr_un struct for a given path length.
// see sys/un.h#SUN_LEN, this is modified to allow nul bytes
#define _UNIX_ADDR_LENGTH(path_len) (uintptr_t) (((struct sockaddr_un *) 0)->sun_path) + path_len

static int nettyNonBlockingSocket(int domain, int type, int protocol) {
#ifdef SOCK_NONBLOCK
    return socket(domain, type | SOCK_NONBLOCK, protocol);
#else
    int socketFd = socket(domain, type, protocol);
    int flags;
    // Don't initialize flags until we know the socket is good so errno is preserved.
    if (socketFd < 0 ||
        (flags = fcntl(socketFd, F_GETFL, 0)) < 0 ||
         fcntl(socketFd, F_SETFL, flags | O_NONBLOCK) < 0) {
      return -1;
    }
    return socketFd;
#endif
}

static jobject createDatagramSocketAddress(JNIEnv* env, const struct sockaddr_storage* addr, int len, jobject local) {
    char ipstr[INET6_ADDRSTRLEN];
    int port;
    jstring ipString;
    if (addr->ss_family == AF_INET) {
        struct sockaddr_in* s = (struct sockaddr_in*) addr;
        port = ntohs(s->sin_port);
        inet_ntop(AF_INET, &s->sin_addr, ipstr, sizeof ipstr);
        ipString = (*env)->NewStringUTF(env, ipstr);
    } else {
        struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
        port = ntohs(s->sin6_port);
        inet_ntop(AF_INET6, &s->sin6_addr, ipstr, sizeof ipstr);

        if (strncasecmp(ipstr, ip4prefix, 7) == 0) {
            // IPv4-mapped-on-IPv6.
            // Cut of ::ffff: prefix to workaround performance issues when parsing these
            // addresses in InetAddress.getByName(...).
            //
            // See https://github.com/netty/netty/issues/2867
            ipString = (*env)->NewStringUTF(env, &ipstr[7]);
        } else {
            ipString = (*env)->NewStringUTF(env, ipstr);
        }
    }
    jobject socketAddr = (*env)->NewObject(env, datagramSocketAddressClass, datagramSocketAddrMethodId, ipString, port, len, local);
    return socketAddr;
}

static jsize addressLength(const struct sockaddr_storage* addr) {
    if (addr->ss_family == AF_INET) {
        return 8;
    }
    struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
    if (s->sin6_addr.s6_addr[11] == 0xff && s->sin6_addr.s6_addr[10] == 0xff &&
        s->sin6_addr.s6_addr[9] == 0x00 && s->sin6_addr.s6_addr[8] == 0x00 && s->sin6_addr.s6_addr[7] == 0x00 && s->sin6_addr.s6_addr[6] == 0x00 && s->sin6_addr.s6_addr[5] == 0x00 &&
        s->sin6_addr.s6_addr[4] == 0x00 && s->sin6_addr.s6_addr[3] == 0x00 && s->sin6_addr.s6_addr[2] == 0x00 && s->sin6_addr.s6_addr[1] == 0x00 && s->sin6_addr.s6_addr[0] == 0x00) {
        // IPv4-mapped-on-IPv6
        return 8;
    }
    return 24;
}

static void initInetSocketAddressArray(JNIEnv* env, const struct sockaddr_storage* addr, jbyteArray bArray, int offset, jsize len) {
    int port;
    if (addr->ss_family == AF_INET) {
        struct sockaddr_in* s = (struct sockaddr_in*) addr;
        port = ntohs(s->sin_port);

        // Encode address and port into the array
        unsigned char a[4];
        a[0] = port >> 24;
        a[1] = port >> 16;
        a[2] = port >> 8;
        a[3] = port;
        (*env)->SetByteArrayRegion(env, bArray, offset, 4, (jbyte*) &s->sin_addr.s_addr);
        (*env)->SetByteArrayRegion(env, bArray, offset + 4, 4, (jbyte*) &a);
    } else {
        struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
        port = ntohs(s->sin6_port);

        if (len == 8) {
            // IPv4-mapped-on-IPv6
            // Encode port into the array and write it into the jbyteArray
            unsigned char a[4];
            a[0] = port >> 24;
            a[1] = port >> 16;
            a[2] = port >> 8;
            a[3] = port;

            // we only need the last 4 bytes for mapped address
            (*env)->SetByteArrayRegion(env, bArray, offset, 4, (jbyte*) &(s->sin6_addr.s6_addr[12]));
            (*env)->SetByteArrayRegion(env, bArray, offset + 4, 4, (jbyte*) &a);
        } else {
            // Encode scopeid and port into the array
            unsigned char a[8];
            a[0] = s->sin6_scope_id >> 24;
            a[1] = s->sin6_scope_id >> 16;
            a[2] = s->sin6_scope_id >> 8;
            a[3] = s->sin6_scope_id;
            a[4] = port >> 24;
            a[5] = port >> 16;
            a[6] = port >> 8;
            a[7] = port;

            (*env)->SetByteArrayRegion(env, bArray, offset, 16, (jbyte*) &(s->sin6_addr.s6_addr));
            (*env)->SetByteArrayRegion(env, bArray, offset + 16, 8, (jbyte*) &a);
        }
    }
}

static jbyteArray createInetSocketAddressArray(JNIEnv* env, const struct sockaddr_storage* addr) {
    jsize len = addressLength(addr);
    jbyteArray bArray = (*env)->NewByteArray(env, len);

    initInetSocketAddressArray(env, addr, bArray, 0, len);
    return bArray;
}

static void netty_unix_socket_initialize(JNIEnv* env, jclass clazz, jboolean ipv4Preferred) {
    if (ipv4Preferred) {
        // User asked to use ipv4 explicitly.
        socketType = AF_INET;
    } else {
        int fd = nettyNonBlockingSocket(AF_INET6, SOCK_STREAM, 0);
        if (fd == -1) {
            socketType = errno == EAFNOSUPPORT ? AF_INET : AF_INET6;
        } else {
            // Explicitly try to bind to ::1 to ensure IPV6 can really be used.
            // See https://github.com/netty/netty/issues/7021.
            struct sockaddr_in6 addr;
            memset(&addr, 0, sizeof(addr));
            addr.sin6_family = AF_INET6;
            addr.sin6_addr.s6_addr[15] = 1; /* [::1]:0 */
            int res = bind(fd, (struct sockaddr *)&addr, sizeof(addr));

            close(fd);
            socketType = res == 0 ? AF_INET6 : AF_INET;
        }
    }
}

static void netty_unix_socket_optionHandleError(JNIEnv* env, int err, char* method) {
    if (err == EBADF) {
        netty_unix_errors_throwClosedChannelException(env);
    } else {
        netty_unix_errors_throwChannelExceptionErrorNo(env, method, err);
    }
}

static void netty_unix_socket_setOptionHandleError(JNIEnv* env, int err) {
    netty_unix_socket_optionHandleError(env, err, "setsockopt() failed: ");
}

static int netty_unix_socket_setOption0(jint fd, int level, int optname, const void* optval, socklen_t len) {
    return setsockopt(fd, level, optname, optval, len);
}

static jint _socket(JNIEnv* env, jclass clazz, int type) {
    int fd = nettyNonBlockingSocket(socketType, type, 0);
    if (fd == -1) {
        return -errno;
    } else if (socketType == AF_INET6) {
        // Try to allow listen /connect ipv4 and ipv6
        int optval = 0;
        if (netty_unix_socket_setOption0(fd, IPPROTO_IPV6, IPV6_V6ONLY, &optval, sizeof(optval)) < 0) {
            if (errno != EAFNOSUPPORT) {
                netty_unix_socket_setOptionHandleError(env, errno);
                // Something went wrong so close the fd and return here. setOption(...) itself throws the exception already.
                close(fd);
                return -1;
            }
            // else we failed to enable dual stack mode.
            // It is assumed the socket is re‐stricted to sending and receiving IPv6 packets only.
            // Don't close fd and don't return -1. At best we can do is log.
            // TODO: bubble this up to an actual Logger.
        }
    }
    return fd;
}

int netty_unix_socket_initSockaddr(JNIEnv* env, jbyteArray address, jint scopeId, jint jport,
                                   const struct sockaddr_storage* addr, socklen_t* addrSize) {
    uint16_t port = htons((uint16_t) jport);

    // Use GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical to signal the VM that we really would like
    // to not do a memory copy here. This is ok as we not do any blocking action here anyway.
    // This is important as the VM may suspend GC for the time!
    jbyte* addressBytes = (*env)->GetPrimitiveArrayCritical(env, address, 0);
    if (addressBytes == NULL) {
        // No memory left ?!?!?
        netty_unix_errors_throwOutOfMemoryError(env);
        return -1;
    }
    if (socketType == AF_INET6) {
        struct sockaddr_in6* ip6addr = (struct sockaddr_in6*) addr;
        *addrSize = sizeof(struct sockaddr_in6);
        ip6addr->sin6_family = AF_INET6;
        ip6addr->sin6_port = port;
        ip6addr->sin6_flowinfo = 0;
        ip6addr->sin6_scope_id = (uint32_t) scopeId;
        // check if this is an any address and if so we need to handle it like this.
        if (memcmp(addressBytes, wildcardAddress, 16) == 0 || memcmp(addressBytes, ipv4MappedWildcardAddress, 16) == 0) {
            ip6addr->sin6_addr = in6addr_any;
        } else {
            memcpy(&(ip6addr->sin6_addr.s6_addr), addressBytes, 16);
        }
    } else {
        struct sockaddr_in* ipaddr = (struct sockaddr_in*) addr;
        *addrSize = sizeof(struct sockaddr_in);
        ipaddr->sin_family = AF_INET;
        ipaddr->sin_port = port;
        memcpy(&(ipaddr->sin_addr.s_addr), addressBytes + 12, 4);
    }

    (*env)->ReleasePrimitiveArrayCritical(env, address, addressBytes, JNI_ABORT);
    return 0;
}

static jint _sendTo(JNIEnv* env, jint fd, void* buffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr, &addrSize) == -1) {
        return -1;
    }

    ssize_t res;
    int err;
    do {
       res = sendto(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, addrSize);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jobject _recvFrom(JNIEnv* env, jint fd, void* buffer, jint pos, jint limit) {
    struct sockaddr_storage addr;
    socklen_t addrlen = sizeof(addr);
    ssize_t res;
    int err;
    jobject local = NULL;

#ifdef IP_RECVORIGDSTADDR
    struct sockaddr_storage daddr;
    struct iovec iov;
    struct cmsghdr* cmsg;
    struct msghdr msg;
    char cntrlbuf[64];

    int readLocalAddr;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_RECVORIGDSTADDR,
            &readLocalAddr, sizeof(readLocalAddr)) < 0) {
        readLocalAddr = 0;
    }

    if (readLocalAddr) {
        iov.iov_base = buffer + pos;
        iov.iov_len  = (size_t) (limit - pos);
        msg.msg_name = (struct sockaddr*) &addr;
        msg.msg_namelen = addrlen;
        msg.msg_iov = &iov;
        msg.msg_iovlen = 1;
        msg.msg_control = cntrlbuf;
        msg.msg_controllen = sizeof(cntrlbuf);
    }
#endif

    do {
#ifdef IP_RECVORIGDSTADDR
        if (readLocalAddr) {
            res = recvmsg(fd, &msg, 0);
        } else {
#endif
            res = recvfrom(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, &addrlen);
#ifdef IP_RECVORIGDSTADDR
        }
#endif
        // Keep on reading if we was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            // Nothing left to read
            return NULL;
        }
        if (err == EBADF) {
            netty_unix_errors_throwClosedChannelException(env);
            return NULL;
        }
        if (err == ECONNREFUSED) {
            netty_unix_errors_throwPortUnreachableException(env, "recvfrom() failed");
            return NULL;
        }
        netty_unix_errors_throwIOExceptionErrorNo(env, "recvfrom() failed: ", err);
        return NULL;
    }

#ifdef IP_RECVORIGDSTADDR
    if (readLocalAddr) {
        for (cmsg = CMSG_FIRSTHDR(&msg); cmsg != NULL; cmsg = CMSG_NXTHDR(&msg, cmsg)) {
            if (cmsg->cmsg_level == SOL_IP && cmsg->cmsg_type == IP_RECVORIGDSTADDR) {
                memcpy (&daddr, CMSG_DATA(cmsg), sizeof (struct sockaddr_storage));
                local = createDatagramSocketAddress(env, &daddr, res, NULL);
                break;
            }
        }
    }
#endif
    return createDatagramSocketAddress(env, &addr, res, local);
}

void netty_unix_socket_getOptionHandleError(JNIEnv* env, int err) {
    netty_unix_socket_optionHandleError(env, err, "getsockopt() failed: ");
}

int netty_unix_socket_getOption0(jint fd, int level, int optname, void* optval, socklen_t optlen) {
    return getsockopt(fd, level, optname, optval, &optlen);
}

int netty_unix_socket_getOption(JNIEnv* env, jint fd, int level, int optname, void* optval, socklen_t optlen) {
    int rc = netty_unix_socket_getOption0(fd, level, optname, optval, optlen);
    if (rc < 0) {
        netty_unix_socket_getOptionHandleError(env, errno);
    }
    return rc;
}

int netty_unix_socket_setOption(JNIEnv* env, jint fd, int level, int optname, const void* optval, socklen_t len) {
    int rc = netty_unix_socket_setOption0(fd, level, optname, optval, len);
    if (rc < 0) {
        netty_unix_socket_setOptionHandleError(env, errno);
    }
    return rc;
}

// JNI Registered Methods Begin
static jint netty_unix_socket_shutdown(JNIEnv* env, jclass clazz, jint fd, jboolean read, jboolean write) {
    int mode;
    if (read && write) {
        mode = SHUT_RDWR;
    } else if (read) {
        mode = SHUT_RD;
    } else if (write) {
        mode = SHUT_WR;
    } else {
        return -EINVAL;
    }
    if (shutdown(fd, mode) < 0) {
        return -errno;
    }
    return 0;
}

static jint netty_unix_socket_bind(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr, &addrSize) == -1) {
        return -1;
    }

    if (bind(fd, (struct sockaddr*) &addr, addrSize) == -1) {
        return -errno;
    }
    return 0;
}

static jint netty_unix_socket_listen(JNIEnv* env, jclass clazz, jint fd, jint backlog) {
    if (listen(fd, backlog) == -1) {
        return -errno;
    }
    return 0;
}

static jint netty_unix_socket_connect(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr, &addrSize) == -1) {
        // A runtime exception was thrown
        return -1;
    }

    int res;
    int err;
    do {
        res = connect(fd, (struct sockaddr*) &addr, addrSize);
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return 0;
}

static jint netty_unix_socket_finishConnect(JNIEnv* env, jclass clazz, jint fd) {
    // connect may be done
    // return true if connection finished successfully
    // return false if connection is still in progress
    // throw exception if connection failed
    int optval;
    int res = netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_ERROR, &optval, sizeof(optval));
    if (res != 0) {
        // getOption failed
        return -1;
    }
    if (optval == 0) {
        // connect succeeded
        return 0;
    }
    return -optval;
}

static jint netty_unix_socket_disconnect(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    int len;

    memset(&addr, 0, sizeof(addr));

    // You can disconnect connection-less sockets by using AF_UNSPEC.
    // See man 2 connect.
    if (socketType == AF_INET6) {
        struct sockaddr_in6* ip6addr = (struct sockaddr_in6*) &addr;
        ip6addr->sin6_family = AF_UNSPEC;
        len = sizeof(struct sockaddr_in6);
    } else {
        struct sockaddr_in* ipaddr = (struct sockaddr_in*) &addr;
        ipaddr->sin_family = AF_UNSPEC;
        len = sizeof(struct sockaddr_in);
    }

    int res;
    int err;
    do {
        res = connect(fd, (struct sockaddr*) &addr, len);
    } while (res == -1 && ((err = errno) == EINTR));

    // EAFNOSUPPORT is harmless in this case.
    // See http://www.unix.com/man-page/osx/2/connect/
    if (res < 0 && err != EAFNOSUPPORT) {
        return -err;
    }
    return 0;
}

static jint netty_unix_socket_accept(JNIEnv* env, jclass clazz, jint fd, jbyteArray acceptedAddress) {
    jint socketFd;
    jsize len;
    int err;
    struct sockaddr_storage addr;
    socklen_t address_len = sizeof(addr);

    for (;;) {
#ifdef SOCK_NONBLOCK
        if (accept4) {
            socketFd = accept4(fd, (struct sockaddr*) &addr, &address_len, SOCK_NONBLOCK | SOCK_CLOEXEC);
        } else {
#endif
            socketFd = accept(fd, (struct sockaddr*) &addr, &address_len);
#ifdef SOCK_NONBLOCK
        }
#endif

        if (socketFd != -1) {
            break;
        }
        if ((err = errno) != EINTR) {
            return -err;
        }
    }

    len = addressLength(&addr);

    // Fill in remote address details
    (*env)->SetByteArrayRegion(env, acceptedAddress, 0, 4, (jbyte*) &len);
    initInetSocketAddressArray(env, &addr, acceptedAddress, 1, len);

    if (accept4)  {
        return socketFd;
    }
    if (fcntl(socketFd, F_SETFD, FD_CLOEXEC) == -1 || fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
        // accept4 was not present so need two more sys-calls ...
        return -errno;
    }
    return socketFd;
}

static jbyteArray netty_unix_socket_remoteAddress(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if (getpeername(fd, (struct sockaddr*) &addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddressArray(env, &addr);
}

static jbyteArray netty_unix_socket_localAddress(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if (getsockname(fd, (struct sockaddr*) &addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddressArray(env, &addr);
}

static jint netty_unix_socket_newSocketDgramFd(JNIEnv* env, jclass clazz) {
    return _socket(env, clazz, SOCK_DGRAM);
}

static jint netty_unix_socket_newSocketStreamFd(JNIEnv* env, jclass clazz) {
    return _socket(env, clazz, SOCK_STREAM);
}

static jint netty_unix_socket_newSocketDomainFd(JNIEnv* env, jclass clazz) {
    int fd = nettyNonBlockingSocket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        return -errno;
    }
    return fd;
}

static jint netty_unix_socket_sendTo(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _sendTo(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit, address, scopeId, port);
}

static jint netty_unix_socket_sendToAddress(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    return _sendTo(env, fd, (void *) (intptr_t) memoryAddress, pos, limit, address, scopeId, port);
}

static jint netty_unix_socket_sendToAddresses(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr, &addrSize) == -1) {
        return -1;
    }

    struct msghdr m = { 0 };
    m.msg_name = (void*) &addr;
    m.msg_namelen = addrSize;
    m.msg_iov = (struct iovec*) (intptr_t) memoryAddress;
    m.msg_iovlen = length;

    ssize_t res;
    int err;
    do {
       res = sendmsg(fd, &m, 0);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jobject netty_unix_socket_recvFrom(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _recvFrom(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit);
}

static jobject netty_unix_socket_recvFromAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return _recvFrom(env, fd, (void *) (intptr_t) address, pos, limit);
}

static jint netty_unix_socket_bindDomainSocket(JNIEnv* env, jclass clazz, jint fd, jbyteArray socketPath) {
    struct sockaddr_un addr;

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    jbyte* socket_path = (*env)->GetByteArrayElements(env, socketPath, 0);
    jint socket_path_len = (*env)->GetArrayLength(env, socketPath);
    if (socket_path_len > sizeof(addr.sun_path)) {
        socket_path_len = sizeof(addr.sun_path);
    }
    memcpy(addr.sun_path, socket_path, socket_path_len);

    if (unlink((const char*) socket_path) == -1 && errno != ENOENT) {
        return -errno;
    }

    int res = bind(fd, (struct sockaddr*) &addr, _UNIX_ADDR_LENGTH(socket_path_len));
    (*env)->ReleaseByteArrayElements(env, socketPath, socket_path, 0);

    if (res == -1) {
        return -errno;
    }
    return res;
}

static jint netty_unix_socket_connectDomainSocket(JNIEnv* env, jclass clazz, jint fd, jbyteArray socketPath) {
    struct sockaddr_un addr;
    jint socket_path_len;

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    jbyte* socket_path = (*env)->GetByteArrayElements(env, socketPath, 0);
    socket_path_len = (*env)->GetArrayLength(env, socketPath);
    if (socket_path_len > sizeof(addr.sun_path)) {
        socket_path_len = sizeof(addr.sun_path);
    }
    memcpy(addr.sun_path, socket_path, socket_path_len);

    int res;
    int err;
    do {
        res = connect(fd, (struct sockaddr*) &addr, _UNIX_ADDR_LENGTH(socket_path_len));
    } while (res == -1 && ((err = errno) == EINTR));

    (*env)->ReleaseByteArrayElements(env, socketPath, socket_path, 0);

    if (res < 0) {
        return -err;
    }
    return 0;
}

static jint netty_unix_socket_recvFd(JNIEnv* env, jclass clazz, jint fd) {
    int socketFd;
    struct msghdr descriptorMessage = { 0 };
    struct iovec iov[1] = { { 0 } };
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
            // set as non blocking as we want to use it with kqueue/epoll
            if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
                err = errno;
                close(socketFd);
                return -err;
            }
            return socketFd;
        }
    }
}

static jint netty_unix_socket_sendFd(JNIEnv* env, jclass clazz, jint socketFd, jint fd) {
    struct msghdr descriptorMessage = { 0 };
    struct iovec iov[1] = { { 0 } };
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

static void netty_unix_socket_setReuseAddress(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
}

static void netty_unix_socket_setReusePort(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval));
}

static void netty_unix_socket_setTcpNoDelay(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval));
}

static void netty_unix_socket_setReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval));
}

static void netty_unix_socket_setSendBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval));
}

static void netty_unix_socket_setKeepAlive(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_KEEPALIVE, &optval, sizeof(optval));
}

static void netty_unix_socket_setBroadcast(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval));
}

static void netty_unix_socket_setSoLinger(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    struct linger solinger;
    if (optval < 0) {
        solinger.l_onoff = 0;
        solinger.l_linger = 0;
    } else {
        solinger.l_onoff = 1;
        solinger.l_linger = optval;
    }
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_LINGER, &solinger, sizeof(solinger));
}

static void netty_unix_socket_setTrafficClass(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    if (socketType == AF_INET6) {
        // This call will put an exception on the stack to be processed once the JNI calls completes if
        // setsockopt failed and return a negative value.
        int rc = netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_TCLASS, &optval, sizeof(optval));

        if (rc >= 0) {
/* Linux allows both ipv4 and ipv6 families to be set */
#ifdef __linux__
            // Previous call successful now try to set also for ipv4
            if (netty_unix_socket_setOption0(fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval)) == -1) {
                if (errno != ENOPROTOOPT) {
                    // throw exception
                    netty_unix_socket_setOptionHandleError(env, errno);
                }
            }
#endif
        }
    } else {
        netty_unix_socket_setOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval));
    }
}

static jint netty_unix_socket_isKeepAlive(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_KEEPALIVE, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_isTcpNoDelay(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_getReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_getSendBufferSize(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_getSoLinger(JNIEnv* env, jclass clazz, jint fd) {
    struct linger optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_LINGER, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    if (optval.l_onoff == 0) {
        return -1;
    } else {
        return optval.l_linger;
    }
}

static jint netty_unix_socket_getTrafficClass(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (socketType == AF_INET6) {
        if (netty_unix_socket_getOption0(fd, IPPROTO_IPV6, IPV6_TCLASS, &optval, sizeof(optval)) == -1) {
            if (errno == ENOPROTOOPT) {
                if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval)) == -1) {
                    return -1;
                }
            } else {
                netty_unix_socket_getOptionHandleError(env, errno);
                return -1;
            }
        }
    } else {
         if (netty_unix_socket_getOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval)) == -1) {
            return -1;
         }
    }
    return optval;
}

static jint netty_unix_socket_getSoError(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_ERROR, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_isReuseAddress(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_isReusePort(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

static jint netty_unix_socket_isBroadcast(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}


// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "shutdown", "(IZZ)I", (void *) netty_unix_socket_shutdown },
  { "bind", "(I[BII)I", (void *) netty_unix_socket_bind },
  { "listen", "(II)I", (void *) netty_unix_socket_listen },
  { "connect", "(I[BII)I", (void *) netty_unix_socket_connect },
  { "finishConnect", "(I)I", (void *) netty_unix_socket_finishConnect },
  { "disconnect", "(I)I", (void *) netty_unix_socket_disconnect},
  { "accept", "(I[B)I", (void *) netty_unix_socket_accept },
  { "remoteAddress", "(I)[B", (void *) netty_unix_socket_remoteAddress },
  { "localAddress", "(I)[B", (void *) netty_unix_socket_localAddress },
  { "newSocketDgramFd", "()I", (void *) netty_unix_socket_newSocketDgramFd },
  { "newSocketStreamFd", "()I", (void *) netty_unix_socket_newSocketStreamFd },
  { "newSocketDomainFd", "()I", (void *) netty_unix_socket_newSocketDomainFd },
  { "sendTo", "(ILjava/nio/ByteBuffer;II[BII)I", (void *) netty_unix_socket_sendTo },
  { "sendToAddress", "(IJII[BII)I", (void *) netty_unix_socket_sendToAddress },
  { "sendToAddresses", "(IJI[BII)I", (void *) netty_unix_socket_sendToAddresses },
  // "recvFrom" has a dynamic signature
  // "recvFromAddress" has a dynamic signature
  { "recvFd", "(I)I", (void *) netty_unix_socket_recvFd },
  { "sendFd", "(II)I", (void *) netty_unix_socket_sendFd },
  { "bindDomainSocket", "(I[B)I", (void *) netty_unix_socket_bindDomainSocket },
  { "connectDomainSocket", "(I[B)I", (void *) netty_unix_socket_connectDomainSocket },
  { "setTcpNoDelay", "(II)V", (void *) netty_unix_socket_setTcpNoDelay },
  { "setReusePort", "(II)V", (void *) netty_unix_socket_setReusePort },
  { "setBroadcast", "(II)V", (void *) netty_unix_socket_setBroadcast },
  { "setReuseAddress", "(II)V", (void *) netty_unix_socket_setReuseAddress },
  { "setReceiveBufferSize", "(II)V", (void *) netty_unix_socket_setReceiveBufferSize },
  { "setSendBufferSize", "(II)V", (void *) netty_unix_socket_setSendBufferSize },
  { "setKeepAlive", "(II)V", (void *) netty_unix_socket_setKeepAlive },
  { "setSoLinger", "(II)V", (void *) netty_unix_socket_setSoLinger },
  { "setTrafficClass", "(II)V", (void *) netty_unix_socket_setTrafficClass },
  { "isKeepAlive", "(I)I", (void *) netty_unix_socket_isKeepAlive },
  { "isTcpNoDelay", "(I)I", (void *) netty_unix_socket_isTcpNoDelay },
  { "isBroadcast", "(I)I", (void *) netty_unix_socket_isBroadcast },
  { "isReuseAddress", "(I)I", (void *) netty_unix_socket_isReuseAddress },
  { "isReusePort", "(I)I", (void *) netty_unix_socket_isReusePort },
  { "getReceiveBufferSize", "(I)I", (void *) netty_unix_socket_getReceiveBufferSize },
  { "getSendBufferSize", "(I)I", (void *) netty_unix_socket_getSendBufferSize },
  { "getSoLinger", "(I)I", (void *) netty_unix_socket_getSoLinger },
  { "getTrafficClass", "(I)I", (void *) netty_unix_socket_getTrafficClass },
  { "getSoError", "(I)I", (void *) netty_unix_socket_getSoError },
  { "initialize", "(Z)V", (void *) netty_unix_socket_initialize }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    return fixed_method_table_size + 2;
}

static JNINativeMethod* createDynamicMethodsTable(const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = malloc(sizeof(JNINativeMethod) * dynamicMethodsTableSize());
    memcpy(dynamicMethods, fixed_method_table, sizeof(fixed_method_table));
    char* dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress;");
    JNINativeMethod* dynamicMethod = &dynamicMethods[fixed_method_table_size];
    dynamicMethod->name = "recvFrom";
    dynamicMethod->signature = netty_unix_util_prepend("(ILjava/nio/ByteBuffer;II)L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFrom;
    free(dynamicTypeName);

    ++dynamicMethod;
    dynamicTypeName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress;");
    dynamicMethod->name = "recvFromAddress";
    dynamicMethod->signature = netty_unix_util_prepend("(IJII)L", dynamicTypeName);
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFromAddress;
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

jint netty_unix_socket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/unix/Socket",
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        freeDynamicMethodsTable(dynamicMethods);
        return JNI_ERR;
    }
    freeDynamicMethodsTable(dynamicMethods);
    dynamicMethods = NULL;
    char* nettyClassName = netty_unix_util_prepend(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress");
    jclass localDatagramSocketAddressClass = (*env)->FindClass(env, nettyClassName);
    free(nettyClassName);
    nettyClassName = NULL;
    if (localDatagramSocketAddressClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    datagramSocketAddressClass = (jclass) (*env)->NewGlobalRef(env, localDatagramSocketAddressClass);
    if (datagramSocketAddressClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    datagramSocketAddrMethodId = (*env)->GetMethodID(env, datagramSocketAddressClass, "<init>", "(Ljava/lang/String;IILio/netty/channel/unix/DatagramSocketAddress;)V");
    if (datagramSocketAddrMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: DatagramSocketAddress.<init>(String, int, int, DatagramSocketAddress)");
        return JNI_ERR;
    }
    jclass localInetSocketAddressClass = (*env)->FindClass(env, "java/net/InetSocketAddress");
    if (localInetSocketAddressClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    inetSocketAddressClass = (jclass) (*env)->NewGlobalRef(env, localInetSocketAddressClass);
    if (inetSocketAddressClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    inetSocketAddrMethodId = (*env)->GetMethodID(env, inetSocketAddressClass, "<init>", "(Ljava/lang/String;I)V");
    if (inetSocketAddrMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: InetSocketAddress.<init>(String, int)");
        return JNI_ERR;
    }

    void* mem = malloc(1);
    if (mem == NULL) {
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    jobject directBuffer = (*env)->NewDirectByteBuffer(env, mem, 1);
    if (directBuffer == NULL) {
        free(mem);

        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    if ((*env)->GetDirectBufferAddress(env, directBuffer) == NULL) {
        free(mem);

        netty_unix_errors_throwRuntimeException(env, "failed to get direct buffer address");
        return JNI_ERR;
    }
    free(mem);

    return NETTY_JNI_VERSION;
}

void netty_unix_socket_JNI_OnUnLoad(JNIEnv* env) {
    if (datagramSocketAddressClass != NULL) {
        (*env)->DeleteGlobalRef(env, datagramSocketAddressClass);
        datagramSocketAddressClass = NULL;
    }
    if (inetSocketAddressClass != NULL) {
        (*env)->DeleteGlobalRef(env, inetSocketAddressClass);
        inetSocketAddressClass = NULL;
    }
}

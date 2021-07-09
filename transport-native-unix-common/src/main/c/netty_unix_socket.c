/*
 * Copyright 2015 The Netty Project
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
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stddef.h>
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
#include "netty_jni_util.h"

#define SOCKET_CLASSNAME "io/netty/channel/unix/Socket"
// Define SO_REUSEPORT if not found to fix build issues.
// See https://github.com/netty/netty/issues/2558
#ifndef SO_REUSEPORT
#define SO_REUSEPORT 15
#endif /* SO_REUSEPORT */

// MSG_FASTOPEN is defined in linux 3.6. We define this here so older kernels can compile.
#ifndef MSG_FASTOPEN
#define MSG_FASTOPEN 0x20000000
#endif

static jclass datagramSocketAddressClass = NULL;
static jclass domainDatagramSocketAddressClass = NULL;
static jmethodID datagramSocketAddrMethodId = NULL;
static jmethodID domainDatagramSocketAddrMethodId = NULL;
static jmethodID inetSocketAddrMethodId = NULL;
static jclass inetSocketAddressClass = NULL;
static int socketType = AF_INET;
static const unsigned char wildcardAddress[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
static const unsigned char ipv4MappedWildcardAddress[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00 };
static const unsigned char ipv4MappedAddress[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff };

// Optional external methods
extern int accept4(int sockFd, struct sockaddr* addr, socklen_t* addrlen, int flags) __attribute__((weak)) __attribute__((weak_import));

// macro to calculate the length of a sockaddr_un struct for a given path length.
// see sys/un.h#SUN_LEN, this is modified to allow nul bytes
#define _UNIX_ADDR_LENGTH(path_len) ((uintptr_t) offsetof(struct sockaddr_un, sun_path) + (uintptr_t) path_len)

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

int netty_unix_socket_ipAddressLength(const struct sockaddr_storage* addr) {
    if (addr->ss_family == AF_INET) {
        return 4;
    }
    struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
    if (memcmp(s->sin6_addr.s6_addr, ipv4MappedAddress, 12) == 0) {
         // IPv4-mapped-on-IPv6
         return 4;
    }
    return 16;
}

static jobject createDatagramSocketAddress(JNIEnv* env, const struct sockaddr_storage* addr, int len, jobject local) {
    int port;
    int scopeId;
    int ipLength = netty_unix_socket_ipAddressLength(addr);
    jbyteArray addressBytes = (*env)->NewByteArray(env, ipLength);
    if (addressBytes == NULL) {
        return NULL;
    }
    if (addr->ss_family == AF_INET) {
        struct sockaddr_in* s = (struct sockaddr_in*) addr;
        port = ntohs(s->sin_port);
        scopeId = 0;
        (*env)->SetByteArrayRegion(env, addressBytes, 0, ipLength, (jbyte*) &s->sin_addr.s_addr);
    } else {
        struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
        port = ntohs(s->sin6_port);
        scopeId = s->sin6_scope_id;

        int offset;
        if (ipLength == 4) {
            // IPv4-mapped-on-IPv6.
            offset = 12;
        } else {
            offset = 0;
        }
        jbyte* addr = (jbyte*) &s->sin6_addr.s6_addr;
        (*env)->SetByteArrayRegion(env, addressBytes, 0, ipLength, addr + offset);
    }
    jobject obj = (*env)->NewObject(env, datagramSocketAddressClass, datagramSocketAddrMethodId, addressBytes, scopeId, port, len, local);
    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        return NULL;
    }
    return obj;
}

static jobject createDomainDatagramSocketAddress(JNIEnv* env, const struct sockaddr_storage* addr, int len, jobject local) {
    struct sockaddr_un* s = (struct sockaddr_un*) addr;
    int pathLength = strlen(s->sun_path);
    jbyteArray pathBytes = (*env)->NewByteArray(env, pathLength);
    if (pathBytes == NULL) {
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, pathBytes, 0, pathLength, (jbyte*) &s->sun_path);
    jobject obj = (*env)->NewObject(env, domainDatagramSocketAddressClass, domainDatagramSocketAddrMethodId, pathBytes, len, local);
    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        return NULL;
    }
    return obj;
}

static jsize addressLength(const struct sockaddr_storage* addr) {
    int len = netty_unix_socket_ipAddressLength(addr);
    if (len == 4) {
        // Only encode port into it
        return len + 4;
    }
    // we encode port + scope into it
    return len + 8;
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

jbyteArray netty_unix_socket_createInetSocketAddressArray(JNIEnv* env, const struct sockaddr_storage* addr) {
    jsize len = addressLength(addr);
    jbyteArray bArray = (*env)->NewByteArray(env, len);
    if (bArray == NULL) {
        return NULL;
    }
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

static jboolean netty_unix_socket_isIPv6Preferred(JNIEnv* env, jclass clazz) {
    return socketType == AF_INET6;
}


static jboolean netty_unix_socket_isIPv6(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t addrlen = sizeof(addr);
    if (getsockname(fd, (struct sockaddr*) &addr, &addrlen) == 0) {
        return ((struct sockaddr*) &addr)->sa_family == AF_INET6;
    }

    netty_unix_errors_throwChannelExceptionErrorNo(env, "getsockname(...) failed: ", errno);
    return JNI_FALSE;
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

static jint _socket(JNIEnv* env, jclass clazz, int domain, int type) {
    int fd = nettyNonBlockingSocket(domain, type, 0);
    if (fd == -1) {
        return -errno;
    } else if (domain == AF_INET6) {
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

int netty_unix_socket_initSockaddr(JNIEnv* env, jboolean ipv6, jbyteArray address, jint scopeId, jint jport,
                                   const struct sockaddr_storage* addr, socklen_t* addrSize) {
    uint16_t port = htons((uint16_t) jport);
    // We use 16 bytes as this allows us to fit ipv6, ipv4 and ipv4 mapped ipv6 addresses in the array.
    jbyte addressBytes[16];

    int len = (*env)->GetArrayLength(env, address);

    if (len > 16) {
        // This should never happen but let's guard against it anyway.
        return -1;
    }

    // We use GetByteArrayRegion(...) and copy into a small stack allocated buffer and NOT GetPrimitiveArrayCritical(...)
    // as there are still multiple GCLocker related bugs which are not fixed yet.
    //
    // For example:
    //     https://bugs.openjdk.java.net/browse/JDK-8048556
    //     https://bugs.openjdk.java.net/browse/JDK-8057573
    //     https://bugs.openjdk.java.net/browse/JDK-8057586
    (*env)->GetByteArrayRegion(env, address, 0, len, addressBytes);

    if (ipv6 == JNI_TRUE) {
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
    return 0;
}

static jint _sendTo(JNIEnv* env, jint fd, jboolean ipv6, void* buffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port, jint flags) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, port, &addr, &addrSize) == -1) {
        return -1;
    }

    ssize_t res;
    int err;
    do {
       res = sendto(fd, buffer + pos, (size_t) (limit - pos), flags, (struct sockaddr*) &addr, addrSize);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jint _sendToDomainSocket(JNIEnv* env, jint fd, void* buffer, jint pos, jint limit, jbyteArray socketPath) {
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

    ssize_t res;
    int err;
    do {
        res = sendto(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, _UNIX_ADDR_LENGTH(socket_path_len));
        // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    (*env)->ReleaseByteArrayElements(env, socketPath, socket_path, 0);

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

    do {
        res = recvfrom(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, &addrlen);
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

    return createDatagramSocketAddress(env, &addr, res, NULL);
}

static jobject _recvFromDomainSocket(JNIEnv* env, jint fd, void* buffer, jint pos, jint limit) {
    struct sockaddr_storage addr;
    socklen_t addrlen = sizeof(addr);
    ssize_t res;
    int err;

    do {
        res = recvfrom(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, &addrlen);
        // Keep on reading if it was interrupted
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
        netty_unix_errors_throwIOExceptionErrorNo(env, "_recvFromDomainSocket() failed: ", err);
        return NULL;
    }

    return createDomainDatagramSocketAddress(env, &addr, res, NULL);
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

static jint netty_unix_socket_bind(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, port, &addr, &addrSize) == -1) {
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

static jint netty_unix_socket_connect(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, port, &addr, &addrSize) == -1) {
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

static jint netty_unix_socket_disconnect(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6) {
    struct sockaddr_storage addr;
    int len;

    memset(&addr, 0, sizeof(addr));

    // You can disconnect connection-less sockets by using AF_UNSPEC.
    // See man 2 connect.
    if (ipv6 == JNI_TRUE) {
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
    // See https://www.unix.com/man-page/osx/2/connect/
    if (res < 0 && err != EAFNOSUPPORT) {
        return -err;
    }
    return 0;
}

static jint netty_unix_socket_accept(JNIEnv* env, jclass clazz, jint fd, jbyteArray acceptedAddress) {
    jint socketFd;
    jsize len;
    jbyte len_b;
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
    len_b = (jbyte) len;

    // Fill in remote address details
    (*env)->SetByteArrayRegion(env, acceptedAddress, 0, 1, (jbyte*) &len_b);
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
    return netty_unix_socket_createInetSocketAddressArray(env, &addr);
}

static jbyteArray netty_unix_socket_localAddress(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if (getsockname(fd, (struct sockaddr*) &addr, &len) == -1) {
        return NULL;
    }
    return netty_unix_socket_createInetSocketAddressArray(env, &addr);
}

static jint netty_unix_socket_newSocketDgramFd(JNIEnv* env, jclass clazz, jboolean ipv6) {
    int domain = ipv6 == JNI_TRUE ? AF_INET6 : AF_INET;
    return _socket(env, clazz, domain, SOCK_DGRAM);
}

static jint netty_unix_socket_newSocketStreamFd(JNIEnv* env, jclass clazz, jboolean ipv6) {
    int domain = ipv6 == JNI_TRUE ? AF_INET6 : AF_INET;
    return _socket(env, clazz, domain, SOCK_STREAM);
}

static jint netty_unix_socket_newSocketDomainFd(JNIEnv* env, jclass clazz) {
    int fd = nettyNonBlockingSocket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        return -errno;
    }
    return fd;
}

static jint netty_unix_socket_newSocketDomainDgramFd(JNIEnv* env, jclass clazz) {
    int fd = nettyNonBlockingSocket(PF_UNIX, SOCK_DGRAM, 0);
    if (fd == -1) {
        return -errno;
    }
    return fd;
}

static jint netty_unix_socket_sendTo(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port, jint flags) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _sendTo(env, fd, ipv6, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit, address, scopeId, port, flags);
}

static jint netty_unix_socket_sendToAddress(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jlong memoryAddress, jint pos, jint limit, jbyteArray address, jint scopeId, jint port, jint flags) {
    return _sendTo(env, fd, ipv6, (void *) (intptr_t) memoryAddress, pos, limit, address, scopeId, port, flags);
}

static jint netty_unix_socket_sendToAddresses(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jlong memoryAddress, jint length, jbyteArray address, jint scopeId, jint port, jint flags) {
    struct sockaddr_storage addr;
    socklen_t addrSize;
    if (netty_unix_socket_initSockaddr(env, ipv6, address, scopeId, port, &addr, &addrSize) == -1) {
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
       res = sendmsg(fd, &m, flags);
       // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return (jint) res;
}

static jint netty_unix_socket_sendToDomainSocket(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray socketPath) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _sendToDomainSocket(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit, socketPath);
}

static jint netty_unix_socket_sendToAddressDomainSocket(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit, jbyteArray socketPath) {
    return _sendToDomainSocket(env, fd, (void *) (intptr_t) memoryAddress, pos, limit, socketPath);
}

static jint netty_unix_socket_sendToAddressesDomainSocket(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length, jbyteArray socketPath) {
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

    struct msghdr m = { 0 };
    m.msg_name = (void*) &addr;
    m.msg_namelen = sizeof(struct sockaddr_un);
    m.msg_iov = (struct iovec*) (intptr_t) memoryAddress;
    m.msg_iovlen = length;

    ssize_t res;
    int err;
    do {
        res = sendmsg(fd, &m, 0);
        // keep on writing if it was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    (*env)->ReleaseByteArrayElements(env, socketPath, socket_path, 0);

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

static jobject netty_unix_socket_recvFromDomainSocket(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _recvFromDomainSocket(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit);
}

static jobject netty_unix_socket_recvFromAddressDomainSocket(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return _recvFromDomainSocket(env, fd, (void *) (intptr_t) address, pos, limit);
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

static void netty_unix_socket_setTrafficClass(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6, jint optval) {
    if (ipv6 == JNI_TRUE) {
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

static jint netty_unix_socket_getTrafficClass(JNIEnv* env, jclass clazz, jint fd, jboolean ipv6) {
    int optval;
    if (ipv6 == JNI_TRUE) {
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

static jint netty_unit_socket_msgFastopen(JNIEnv* env, jclass clazz) {
    return MSG_FASTOPEN;
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod fixed_method_table[] = {
  { "shutdown", "(IZZ)I", (void *) netty_unix_socket_shutdown },
  { "bind", "(IZ[BII)I", (void *) netty_unix_socket_bind },
  { "listen", "(II)I", (void *) netty_unix_socket_listen },
  { "connect", "(IZ[BII)I", (void *) netty_unix_socket_connect },
  { "finishConnect", "(I)I", (void *) netty_unix_socket_finishConnect },
  { "disconnect", "(IZ)I", (void *) netty_unix_socket_disconnect},
  { "accept", "(I[B)I", (void *) netty_unix_socket_accept },
  { "remoteAddress", "(I)[B", (void *) netty_unix_socket_remoteAddress },
  { "localAddress", "(I)[B", (void *) netty_unix_socket_localAddress },
  { "newSocketDgramFd", "(Z)I", (void *) netty_unix_socket_newSocketDgramFd },
  { "newSocketStreamFd", "(Z)I", (void *) netty_unix_socket_newSocketStreamFd },
  { "newSocketDomainFd", "()I", (void *) netty_unix_socket_newSocketDomainFd },
  { "newSocketDomainDgramFd", "()I", (void *) netty_unix_socket_newSocketDomainDgramFd },
  { "sendTo", "(IZLjava/nio/ByteBuffer;II[BIII)I", (void *) netty_unix_socket_sendTo },
  { "sendToAddress", "(IZJII[BIII)I", (void *) netty_unix_socket_sendToAddress },
  { "sendToAddresses", "(IZJI[BIII)I", (void *) netty_unix_socket_sendToAddresses },
  { "sendToDomainSocket", "(ILjava/nio/ByteBuffer;II[B)I", (void *) netty_unix_socket_sendToDomainSocket },
  { "sendToAddressDomainSocket", "(IJII[B)I", (void *) netty_unix_socket_sendToAddressDomainSocket },
  { "sendToAddressesDomainSocket", "(IJI[B)I", (void *) netty_unix_socket_sendToAddressesDomainSocket },
  // "recvFrom" has a dynamic signature
  // "recvFromAddress" has a dynamic signature
  // "recvFromDomainSocket" has a dynamic signature
  // "recvFromAddressDomainSocket" has a dynamic signature
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
  { "setTrafficClass", "(IZI)V", (void *) netty_unix_socket_setTrafficClass },
  { "isKeepAlive", "(I)I", (void *) netty_unix_socket_isKeepAlive },
  { "isTcpNoDelay", "(I)I", (void *) netty_unix_socket_isTcpNoDelay },
  { "isBroadcast", "(I)I", (void *) netty_unix_socket_isBroadcast },
  { "isReuseAddress", "(I)I", (void *) netty_unix_socket_isReuseAddress },
  { "isReusePort", "(I)I", (void *) netty_unix_socket_isReusePort },
  { "getReceiveBufferSize", "(I)I", (void *) netty_unix_socket_getReceiveBufferSize },
  { "getSendBufferSize", "(I)I", (void *) netty_unix_socket_getSendBufferSize },
  { "getSoLinger", "(I)I", (void *) netty_unix_socket_getSoLinger },
  { "getTrafficClass", "(IZ)I", (void *) netty_unix_socket_getTrafficClass },
  { "getSoError", "(I)I", (void *) netty_unix_socket_getSoError },
  { "initialize", "(Z)V", (void *) netty_unix_socket_initialize },
  { "isIPv6Preferred", "()Z", (void *) netty_unix_socket_isIPv6Preferred },
  { "isIPv6", "(I)Z", (void *) netty_unix_socket_isIPv6 },
  { "msgFastopen", "()I", (void *) netty_unit_socket_msgFastopen }
};
static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

static jint dynamicMethodsTableSize() {
    // 4 is for the dynamic method signatures.
    return fixed_method_table_size + 4;
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
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(ILjava/nio/ByteBuffer;II)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvFrom";
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFrom;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IJII)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvFromAddress";
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFromAddress;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DomainDatagramSocketAddress;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(ILjava/nio/ByteBuffer;II)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvFromDomainSocket";
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFromDomainSocket;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    ++dynamicMethod;
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DomainDatagramSocketAddress;", dynamicTypeName, error);
    NETTY_JNI_UTIL_PREPEND("(IJII)L", dynamicTypeName,  dynamicMethod->signature, error);
    dynamicMethod->name = "recvFromAddressDomainSocket";
    dynamicMethod->fnPtr = (void *) netty_unix_socket_recvFromAddressDomainSocket;
    netty_jni_util_free_dynamic_name(&dynamicTypeName);

    return dynamicMethods;
error:
    free(dynamicTypeName);
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    return NULL;
}

// JNI Method Registration Table End

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Unix to reflect that.
jint netty_unix_socket_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    char* nettyClassName = NULL;
    void* mem = NULL;
    JNINativeMethod* dynamicMethods = createDynamicMethodsTable(packagePrefix);
    if (dynamicMethods == NULL) {
        goto done;
    }
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            SOCKET_CLASSNAME,
            dynamicMethods,
            dynamicMethodsTableSize()) != 0) {
        goto done;
    }
  
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DatagramSocketAddress", nettyClassName, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, datagramSocketAddressClass, nettyClassName, done);

    // Respect shading...
    char parameters[1024] = {0};
    snprintf(parameters, sizeof(parameters), "([BIIIL%s;)V", nettyClassName);
    netty_jni_util_free_dynamic_name(&nettyClassName);
    NETTY_JNI_UTIL_GET_METHOD(env, datagramSocketAddressClass, datagramSocketAddrMethodId, "<init>", parameters, done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/unix/DomainDatagramSocketAddress", nettyClassName, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, domainDatagramSocketAddressClass, nettyClassName, done);

    char parameters1[1024] = {0};
    snprintf(parameters1, sizeof(parameters1), "([BIL%s;)V", nettyClassName);
    netty_jni_util_free_dynamic_name(&nettyClassName);
    NETTY_JNI_UTIL_GET_METHOD(env, domainDatagramSocketAddressClass, domainDatagramSocketAddrMethodId, "<init>", parameters1, done);

    NETTY_JNI_UTIL_LOAD_CLASS(env, inetSocketAddressClass, "java/net/InetSocketAddress", done);
    NETTY_JNI_UTIL_GET_METHOD(env, inetSocketAddressClass, inetSocketAddrMethodId, "<init>", "(Ljava/lang/String;I)V", done);

    if ((mem = malloc(1)) == NULL) {
        goto done;
    }

    jobject directBuffer = (*env)->NewDirectByteBuffer(env, mem, 1);
    if (directBuffer == NULL) {
        goto done;
    }
    if ((*env)->GetDirectBufferAddress(env, directBuffer) == NULL) {
        goto done;
    }

    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    netty_jni_util_free_dynamic_methods_table(dynamicMethods, fixed_method_table_size, dynamicMethodsTableSize());
    free(nettyClassName);
    free(mem);
    return ret;
}

void netty_unix_socket_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, datagramSocketAddressClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, domainDatagramSocketAddressClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, inetSocketAddressClass);

    netty_jni_util_unregister_natives(env, packagePrefix, SOCKET_CLASSNAME);
}

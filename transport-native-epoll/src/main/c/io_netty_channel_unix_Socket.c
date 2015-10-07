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
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>

#include "netty_unix_errors.h"
#include "netty_unix_socket.h"
#include "io_netty_channel_unix_Socket.h"

static jclass datagramSocketAddressClass = NULL;
static jmethodID datagramSocketAddrMethodId = NULL;
static jmethodID inetSocketAddrMethodId = NULL;
static jclass inetSocketAddressClass = NULL;
static jclass netUtilClass = NULL;
static jmethodID netUtilClassIpv4PreferredMethodId = NULL;
static int socketType;
static const char* ip4prefix = "::ffff:";

// Optional external methods
extern int accept4(int sockFd, struct sockaddr* addr, socklen_t* addrlen, int flags) __attribute__((weak));

// macro to calculate the length of a sockaddr_un struct for a given path length.
// see sys/un.h#SUN_LEN, this is modified to allow nul bytes
#define _UNIX_ADDR_LENGTH(path_len) (uintptr_t) (((struct sockaddr_un *) 0)->sun_path) + path_len

static jobject createDatagramSocketAddress(JNIEnv* env, const struct sockaddr_storage* addr, int len) {
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
    jobject socketAddr = (*env)->NewObject(env, datagramSocketAddressClass, datagramSocketAddrMethodId, ipString, port, len);
    return socketAddr;
}

static int addressLength(const struct sockaddr_storage* addr) {
    if (addr->ss_family == AF_INET) {
        return 8;
    } else {
        struct sockaddr_in6* s = (struct sockaddr_in6*) addr;
        if (s->sin6_addr.s6_addr[0] == 0x00 && s->sin6_addr.s6_addr[1] == 0x00 && s->sin6_addr.s6_addr[2] == 0x00 && s->sin6_addr.s6_addr[3] == 0x00 && s->sin6_addr.s6_addr[4] == 0x00
            && s->sin6_addr.s6_addr[5] == 0x00 && s->sin6_addr.s6_addr[6] == 0x00 && s->sin6_addr.s6_addr[7] == 0x00 && s->sin6_addr.s6_addr[8] == 0x00 && s->sin6_addr.s6_addr[9] == 0x00
            && s->sin6_addr.s6_addr[10] == 0xff && s->sin6_addr.s6_addr[11] == 0xff) {
            // IPv4-mapped-on-IPv6
            return 8;
        } else {
            return 24;
        }
    }
}

static void initInetSocketAddressArray(JNIEnv* env, const struct sockaddr_storage* addr, jbyteArray bArray, int offset, int len) {
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
    int len = addressLength(addr);
    jbyteArray bArray = (*env)->NewByteArray(env, len);

    initInetSocketAddressArray(env, addr, bArray, 0, len);
    return bArray;
}

static jobject createInetSocketAddress(JNIEnv* env, const struct sockaddr_storage* addr) {
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
    jobject socketAddr = (*env)->NewObject(env, inetSocketAddressClass, inetSocketAddrMethodId, ipString, port);
    return socketAddr;
}

static int socket_type(JNIEnv* env) {
    jboolean ipv4Preferred = (*env)->CallStaticBooleanMethod(env, netUtilClass, netUtilClassIpv4PreferredMethodId);

    if (ipv4Preferred) {
        // User asked to use ipv4 explicitly.
        return AF_INET;
    }
    int fd = socket(AF_INET6, SOCK_STREAM | SOCK_NONBLOCK, 0);
    if (fd == -1) {
        if (errno == EAFNOSUPPORT) {
            return AF_INET;
        }
        return AF_INET6;
    } else {
        close(fd);
        return AF_INET6;
    }
}

static jint _socket(JNIEnv* env, jclass clazz, int type) {
    int fd = socket(socketType, type | SOCK_NONBLOCK, 0);
    if (fd == -1) {
        return -errno;
    } else if (socketType == AF_INET6) {
        // Allow to listen /connect ipv4 and ipv6
        int optval = 0;
        if (netty_unix_socket_setOption(env, fd, IPPROTO_IPV6, IPV6_V6ONLY, &optval, sizeof(optval)) < 0) {
            // Something went wrong so close the fd and return here. setOption(...) itself throws the exception already.
            close(fd);
            return -1;
        }
    }
    return fd;
}

int netty_unix_socket_initSockaddr(JNIEnv* env, jbyteArray address, jint scopeId, jint jport, const struct sockaddr_storage* addr) {
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
        ip6addr->sin6_family = AF_INET6;
        ip6addr->sin6_port = port;

        if (scopeId != 0) {
           ip6addr->sin6_scope_id = (uint32_t) scopeId;
        }
        memcpy(&(ip6addr->sin6_addr.s6_addr), addressBytes, 16);
    } else {
        struct sockaddr_in* ipaddr = (struct sockaddr_in*) addr;
        ipaddr->sin_family = AF_INET;
        ipaddr->sin_port = port;
        memcpy(&(ipaddr->sin_addr.s_addr), addressBytes + 12, 4);
    }

    (*env)->ReleasePrimitiveArrayCritical(env, address, addressBytes, JNI_ABORT);
    return 0;
}

static jint _sendTo(JNIEnv* env, jint fd, void* buffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    ssize_t res;
    int err;
    do {
       res = sendto(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr*) &addr, sizeof(struct sockaddr_storage));
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
        netty_unix_errors_throwIOExceptionErrorNo(env, "recvfrom() failed: ", err);
        return NULL;
    }

    return createDatagramSocketAddress(env, &addr, res);
}

int netty_unix_socket_getOption(JNIEnv* env, jint fd, int level, int optname, void* optval, socklen_t optlen) {
    int rc = getsockopt(fd, level, optname, optval, &optlen);
    if (rc < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "getsockopt() failed: ", errno);
    }
    return rc;
}

int netty_unix_socket_setOption(JNIEnv* env, jint fd, int level, int optname, const void* optval, socklen_t len) {
    int rc = setsockopt(fd, level, optname, optval, len);
    if (rc < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "setsockopt() failed: ", errno);
    }
    return rc;
}

jint netty_unix_socket_JNI_OnLoad(JNIEnv* env) {
    jclass localDatagramSocketAddressClass = (*env)->FindClass(env, "io/netty/channel/unix/DatagramSocketAddress");
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
    datagramSocketAddrMethodId = (*env)->GetMethodID(env, datagramSocketAddressClass, "<init>", "(Ljava/lang/String;II)V");
    if (datagramSocketAddrMethodId == NULL) {
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: DatagramSocketAddress.<init>(String, int, int)");
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
    jclass localNetUtilClass = (*env)->FindClass(env, "io/netty/util/NetUtil" );
    if (localNetUtilClass == NULL) {
        // pending exception...
        return JNI_ERR;
    }
    netUtilClass = (jclass) (*env)->NewGlobalRef(env, localNetUtilClass);
    if (netUtilClass == NULL) {
        // out-of-memory!
        netty_unix_errors_throwOutOfMemoryError(env);
        return JNI_ERR;
    }
    netUtilClassIpv4PreferredMethodId = (*env)->GetStaticMethodID(env, netUtilClass, "isIpV4StackPreferred", "()Z" );
    if (netUtilClassIpv4PreferredMethodId == NULL) {
        // position method was not found.. something is wrong so bail out
        netty_unix_errors_throwRuntimeException(env, "failed to get method ID: NetUild.isIpV4StackPreferred()");
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

    socketType = socket_type(env);
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
    if (netUtilClass != NULL) {
        (*env)->DeleteGlobalRef(env, netUtilClass);
        netUtilClass = NULL;
    }
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_shutdown(JNIEnv* env, jclass clazz, jint fd, jboolean read, jboolean write) {
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

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_bind(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    if (bind(fd, (struct sockaddr*) &addr, sizeof(addr)) == -1) {
        return -errno;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_listen(JNIEnv* env, jclass clazz, jint fd, jint backlog) {
    if (listen(fd, backlog) == -1) {
        return -errno;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_connect(JNIEnv* env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr) == -1) {
        // A runtime exception was thrown
        return -1;
    }

    int res;
    int err;
    do {
        res = connect(fd, (struct sockaddr*) &addr, sizeof(addr));
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        return -err;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_finishConnect(JNIEnv* env, jclass clazz, jint fd) {
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

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_accept(JNIEnv* env, jclass clazz, jint fd, jbyteArray acceptedAddress) {
    jint socketFd;
    int err;
    struct sockaddr_storage addr;
    socklen_t address_len = sizeof(addr);

    do {
        if (accept4) {
            socketFd = accept4(fd, (struct sockaddr*) &addr, &address_len, SOCK_NONBLOCK | SOCK_CLOEXEC);
        } else  {
            socketFd = accept(fd, (struct sockaddr*) &addr, &address_len);
        }
    } while (socketFd == -1 && ((err = errno) == EINTR));

    if (socketFd == -1) {
        return -err;
    }

    int len = addressLength(&addr);

    // Fill in remote address details
    (*env)->SetByteArrayRegion(env, acceptedAddress, 0, 4, (jbyte*) &len);
    initInetSocketAddressArray(env, &addr, acceptedAddress, 1, len);

    if (accept4)  {
        return socketFd;
    } else  {
        // accept4 was not present so need two more sys-calls ...
        if (fcntl(socketFd, F_SETFD, FD_CLOEXEC) == -1 || fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
            return -errno;
        }
    }
    return socketFd;
}

JNIEXPORT jbyteArray JNICALL Java_io_netty_channel_unix_Socket_remoteAddress(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if (getpeername(fd, (struct sockaddr*) &addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddressArray(env, &addr);
}

JNIEXPORT jbyteArray JNICALL Java_io_netty_channel_unix_Socket_localAddress(JNIEnv* env, jclass clazz, jint fd) {
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if (getsockname(fd, (struct sockaddr*) &addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddressArray(env, &addr);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_newSocketDgramFd(JNIEnv* env, jclass clazz) {
    return _socket(env, clazz, SOCK_DGRAM);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_newSocketStreamFd(JNIEnv* env, jclass clazz) {
    return _socket(env, clazz, SOCK_STREAM);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_newSocketDomainFd(JNIEnv* env, jclass clazz) {
    int fd = socket(PF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0);
    if (fd == -1) {
        return -errno;
    }
    return fd;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_sendTo(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _sendTo(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit, address, scopeId, port);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_sendToAddress(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit ,jbyteArray address, jint scopeId, jint port) {
    return _sendTo(env, fd, (void*) memoryAddress, pos, limit, address, scopeId, port);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_sendToAddresses(JNIEnv* env, jclass clazz, jint fd, jlong memoryAddress, jint length, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;

    if (netty_unix_socket_initSockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    struct msghdr m;
    m.msg_name = (void*) &addr;
    m.msg_namelen = (socklen_t) sizeof(struct sockaddr_storage);
    m.msg_iov = (struct iovec*) memoryAddress;
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

JNIEXPORT jobject JNICALL Java_io_netty_channel_unix_Socket_recvFrom(JNIEnv* env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    // We check that GetDirectBufferAddress will not return NULL in OnLoad
    return _recvFrom(env, fd, (*env)->GetDirectBufferAddress(env, jbuffer), pos, limit);
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_unix_Socket_recvFromAddress(JNIEnv* env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return _recvFrom(env, fd, (void*) address, pos, limit);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_bindDomainSocket(JNIEnv* env, jclass clazz, jint fd, jbyteArray socketPath) {
    struct sockaddr_un addr;

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    jbyte* socket_path = (*env)->GetByteArrayElements(env, socketPath, 0);
    jint socket_path_len = (*env)->GetArrayLength(env, socketPath);
    if (socket_path_len > sizeof(addr.sun_path)) {
        socket_path_len = sizeof(addr.sun_path);
    }
    memcpy(addr.sun_path, socket_path, socket_path_len);

    if (unlink(socket_path) == -1 && errno != ENOENT) {
        return -errno;
    }

    int res = bind(fd, (struct sockaddr*) &addr, _UNIX_ADDR_LENGTH(socket_path_len));
    (*env)->ReleaseByteArrayElements(env, socketPath, socket_path, 0);

    if (res == -1) {
        return -errno;
    }
    return res;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_connectDomainSocket(JNIEnv* env, jclass clazz, jint fd, jbyteArray socketPath) {
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

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setTcpNoDelay(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setSendBufferSize(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setKeepAlive(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, SOL_SOCKET, SO_KEEPALIVE, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setTcpCork(JNIEnv* env, jclass clazz, jint fd, jint optval) {
    netty_unix_socket_setOption(env, fd, IPPROTO_TCP, TCP_CORK, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_unix_Socket_setSoLinger(JNIEnv* env, jclass clazz, jint fd, jint optval) {
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

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_isTcpNoDelay(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_getReceiveBufferSize(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_getSendBufferSize(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_isTcpCork(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, IPPROTO_TCP, TCP_CORK, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_getSoLinger(JNIEnv* env, jclass clazz, jint fd) {
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

JNIEXPORT jint JNICALL Java_io_netty_channel_unix_Socket_getSoError(JNIEnv* env, jclass clazz, jint fd) {
    int optval;
    if (netty_unix_socket_getOption(env, fd, SOL_SOCKET, SO_ERROR, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

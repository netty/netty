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
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/sendfile.h>
#include <netinet/tcp.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include "io_netty_channel_epoll_Native.h"


// optional
extern int accept4(int sockFd, struct sockaddr *addr, socklen_t *addrlen, int flags) __attribute__((weak));
extern int epoll_create1(int flags) __attribute__((weak));

// Those are initialized in the init(...) method and cached for performance reasons
jmethodID updatePosId = NULL;
jmethodID posId = NULL;
jmethodID limitId = NULL;
jfieldID posFieldId = NULL;
jfieldID limitFieldId = NULL;
jfieldID fileChannelFieldId = NULL;
jfieldID transferedFieldId = NULL;
jfieldID fdFieldId = NULL;
jfieldID fileDescriptorFieldId = NULL;;
jmethodID inetSocketAddrMethodId = NULL;
jmethodID datagramSocketAddrMethodId = NULL;
jclass runtimeExceptionClass = NULL;
jclass ioExceptionClass = NULL;
jclass closedChannelExceptionClass = NULL;
jmethodID closedChannelExceptionMethodId = NULL;
jclass inetSocketAddressClass = NULL;
jclass datagramSocketAddressClass = NULL;

static int socketType;

// util methods
void throwRuntimeException(JNIEnv *env, char *message) {
    (*env)->ThrowNew(env, runtimeExceptionClass, message);
}

void throwIOException(JNIEnv *env, char *message) {
    (*env)->ThrowNew(env, ioExceptionClass, message);
}

void throwClosedChannelException(JNIEnv *env) {
    jobject exception = (*env)->NewObject(env, closedChannelExceptionClass, closedChannelExceptionMethodId);
    (*env)->Throw(env, exception);
}

void throwOutOfMemoryError( JNIEnv *env, char *message) {
    jclass exceptionClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
    (*env)->ThrowNew(env, exceptionClass, message);
}

char *exceptionMessage(char *msg, int error) {
    char *err = strerror(error);
    char *result = malloc(strlen(msg) + strlen(err) + 1);
    strcpy(result, msg);
    strcat(result, err);
    return result;
}

jint epollCtl(JNIEnv * env, jint efd, int op, jint fd, jint flags, jint id) {
    uint32_t events = EPOLLET;

    if (flags & EPOLL_ACCEPT) {
        events |= EPOLLIN;
    }
    if (flags & EPOLL_READ) {
        events |= EPOLLIN | EPOLLRDHUP;
    }
    if (flags & EPOLL_WRITE) {
        events |= EPOLLOUT;
    }

    struct epoll_event ev = {
        .events = events,
        // encode the id into the events
        .data.u64 = (((uint64_t) id) << 32L)
    };

    return epoll_ctl(efd, op, fd, &ev);
}

jint getOption(JNIEnv *env, jint fd, int level, int optname, const void *optval, socklen_t optlen) {
    int code;
    code = getsockopt(fd, level, optname, optval, &optlen);
    if (code == 0) {
        return 0;
    }
    int err = errno;
    throwRuntimeException(env, exceptionMessage("Error during getsockopt(...): ", err));
    return code;
}

int setOption(JNIEnv *env, jint fd, int level, int optname, const void *optval, socklen_t len) {
    int rc = setsockopt(fd, level, optname, optval, len);
    if (rc < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error during setsockopt(...): ", err));
    }
    return rc;
}

jobject createInetSocketAddress(JNIEnv * env, struct sockaddr_storage addr) {
    char ipstr[INET6_ADDRSTRLEN];
    int port;
    if (addr.ss_family == AF_INET) {
        struct sockaddr_in *s = (struct sockaddr_in *)&addr;
        port = ntohs(s->sin_port);
        inet_ntop(AF_INET, &s->sin_addr, ipstr, sizeof ipstr);
    } else {
        struct sockaddr_in6 *s = (struct sockaddr_in6 *)&addr;
        port = ntohs(s->sin6_port);
        inet_ntop(AF_INET6, &s->sin6_addr, ipstr, sizeof ipstr);
    }
    jstring ipString = (*env)->NewStringUTF(env, ipstr);
    jobject socketAddr = (*env)->NewObject(env, inetSocketAddressClass, inetSocketAddrMethodId, ipString, port);
    return socketAddr;
}

jobject createDatagramSocketAddress(JNIEnv * env, struct sockaddr_storage addr, int len) {
    char ipstr[INET6_ADDRSTRLEN];
    int port;
    if (addr.ss_family == AF_INET) {
        struct sockaddr_in *s = (struct sockaddr_in *)&addr;
        port = ntohs(s->sin_port);
        inet_ntop(AF_INET, &s->sin_addr, ipstr, sizeof ipstr);
    } else {
        struct sockaddr_in6 *s = (struct sockaddr_in6 *)&addr;
        port = ntohs(s->sin6_port);
        inet_ntop(AF_INET6, &s->sin6_addr, ipstr, sizeof ipstr);
    }
    jstring ipString = (*env)->NewStringUTF(env, ipstr);
    jobject socketAddr = (*env)->NewObject(env, datagramSocketAddressClass, datagramSocketAddrMethodId, ipString, port, len);
    return socketAddr;
}

int init_sockaddr(JNIEnv * env, jbyteArray address, jint scopeId, jint jport, struct sockaddr_storage * addr) {
    uint16_t port = htons((uint16_t) jport);
    // Use GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical to signal the VM that we really would like
    // to not do a memory copy here. This is ok as we not do any blocking action here anyway.
    // This is important as the VM may suspend GC for the time!
    jbyte* addressBytes = (*env)->GetPrimitiveArrayCritical(env, address, 0);
    if (addressBytes == NULL) {
        // No memory left ?!?!?
        throwOutOfMemoryError(env, "Can't allocate memory");
        return -1;
    }
    if (socketType == AF_INET6) {
        struct sockaddr_in6* ip6addr = (struct sockaddr_in6 *) addr;
        ip6addr->sin6_family = AF_INET6;
        ip6addr->sin6_port = port;

        if (scopeId != 0) {
           ip6addr->sin6_scope_id = (uint32_t) scopeId;
        }
        memcpy( &(ip6addr->sin6_addr.s6_addr), addressBytes, 16);
    } else {
        struct sockaddr_in* ipaddr = (struct sockaddr_in *) addr;
        ipaddr->sin_family = AF_INET;
        ipaddr->sin_port = port;
        memcpy( &(ipaddr->sin_addr.s_addr), addressBytes + 12, 4);
    }

    (*env)->ReleasePrimitiveArrayCritical(env, address, addressBytes, JNI_ABORT);
    return 0;
}

static int socket_type() {
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

int init_in_addr(JNIEnv * env, jbyteArray address, struct in_addr * addr) {
    // Use GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical to signal the VM that we really would like
    // to not do a memory copy here. This is ok as we not do any blocking action here anyway.
    // This is important as the VM may suspend GC for the time!
    jbyte* addressBytes = (*env)->GetPrimitiveArrayCritical(env, address, 0);
    if (addressBytes == NULL) {
        // No memory left ?!?!?
        throwOutOfMemoryError(env, "Can't allocate memory");
        return -1;
    }
    if (socketType == AF_INET6) {
        memcpy(addr, addressBytes, 16);
    } else {
        memcpy(addr, addressBytes + 12, 4);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, address, addressBytes, JNI_ABORT);
    return 0;
}
// util methods end

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    } else {
        // cache classes that are used within other jni methods for performance reasons
        jclass localClosedChannelExceptionClass = (*env)->FindClass(env, "java/nio/channels/ClosedChannelException");
        if (localClosedChannelExceptionClass == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        closedChannelExceptionClass = (jclass) (*env)->NewGlobalRef(env, localClosedChannelExceptionClass);
        if (closedChannelExceptionClass == NULL) {
            // out-of-memory!
            throwOutOfMemoryError(env, "Error allocating memory");
            return JNI_ERR;
        }
        closedChannelExceptionMethodId = (*env)->GetMethodID(env, closedChannelExceptionClass, "<init>", "()V");
        if (closedChannelExceptionMethodId == NULL) {
            throwRuntimeException(env, "Unable to obtain constructor of ClosedChannelException");
            return JNI_ERR;
        }
        jclass localRuntimeExceptionClass = (*env)->FindClass(env, "java/lang/RuntimeException");
        if (localRuntimeExceptionClass == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        runtimeExceptionClass = (jclass) (*env)->NewGlobalRef(env, localRuntimeExceptionClass);
        if (runtimeExceptionClass == NULL) {
            // out-of-memory!
            throwOutOfMemoryError(env, "Error allocating memory");
            return JNI_ERR;
        }

        jclass localIoExceptionClass = (*env)->FindClass(env, "java/io/IOException");
        if (localIoExceptionClass == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        ioExceptionClass = (jclass) (*env)->NewGlobalRef(env, localIoExceptionClass);
        if (ioExceptionClass == NULL) {
            // out-of-memory!
            throwOutOfMemoryError(env, "Error allocating memory");
            return JNI_ERR;
        }

        jclass localInetSocketAddressClass = (*env)->FindClass(env, "java/net/InetSocketAddress");
        if (localIoExceptionClass == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        inetSocketAddressClass = (jclass) (*env)->NewGlobalRef(env, localInetSocketAddressClass);
        if (inetSocketAddressClass == NULL) {
            // out-of-memory!
            throwOutOfMemoryError(env, "Error allocating memory");
            return JNI_ERR;
        }

        jclass localDatagramSocketAddressClass = (*env)->FindClass(env, "io/netty/channel/epoll/EpollDatagramChannel$DatagramSocketAddress");
        if (localDatagramSocketAddressClass == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        datagramSocketAddressClass = (jclass) (*env)->NewGlobalRef(env, localDatagramSocketAddressClass);
        if (datagramSocketAddressClass == NULL) {
            // out-of-memory!
            throwOutOfMemoryError(env, "Error allocating memory");
            return JNI_ERR;
        }

        void *mem = malloc(1);
        if (mem == NULL) {
            throwOutOfMemoryError(env, "Error allocating native buffer");
            return JNI_ERR;
        }
        jobject directBuffer = (*env)->NewDirectByteBuffer(env, mem, 1);
        if (directBuffer == NULL) {
            throwOutOfMemoryError(env, "Error allocating native buffer");
            return JNI_ERR;
        }

        jclass cls = (*env)->GetObjectClass(env, directBuffer);

        // Get the method id for Buffer.position() and Buffer.limit(). These are used as fallback if
        // it is not possible to obtain the position and limit using the fields directly.
        posId = (*env)->GetMethodID(env, cls, "position", "()I");
        if (posId == NULL) {
            // position method was not found.. something is wrong so bail out
            throwRuntimeException(env, "Unable to find method ByteBuffer.position()");
            return JNI_ERR;
        }

        limitId = (*env)->GetMethodID(env, cls, "limit", "()I");
        if (limitId == NULL) {
            // limit method was not found.. something is wrong so bail out
            throwRuntimeException(env, "Unable to find method ByteBuffer.limit()");
            return JNI_ERR;
        }
        updatePosId = (*env)->GetMethodID(env, cls, "position", "(I)Ljava/nio/Buffer;");
        if (updatePosId == NULL) {
            // position method was not found.. something is wrong so bail out
            throwRuntimeException(env, "Unable to find method ByteBuffer.position(int)");
            return JNI_ERR;
        }
        // Try to get the ids of the position and limit fields. We later then check if we was able
        // to find them and if so use them get the position and limit of the buffer. This is
        // much faster then call back into java via (*env)->CallIntMethod(...).
        posFieldId = (*env)->GetFieldID(env, cls, "position", "I");
        if (posFieldId == NULL) {
            // this is ok as we can still use the method so just clear the exception
            (*env)->ExceptionClear(env);
        }
        limitFieldId = (*env)->GetFieldID(env, cls, "limit", "I");
        if (limitFieldId == NULL) {
            // this is ok as we can still use the method so just clear the exception
            (*env)->ExceptionClear(env);
        }
        jclass fileRegionCls = (*env)->FindClass(env, "io/netty/channel/DefaultFileRegion");
        if (fileRegionCls == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        fileChannelFieldId = (*env)->GetFieldID(env, fileRegionCls, "file", "Ljava/nio/channels/FileChannel;");
        if (fileChannelFieldId == NULL) {
            throwRuntimeException(env, "Unable to obtain FileChannel field for DefaultFileRegion");
            return JNI_ERR;
        }
        transferedFieldId = (*env)->GetFieldID(env, fileRegionCls, "transfered", "J");
        if (transferedFieldId == NULL) {
            throwRuntimeException(env, "Unable to obtain transfered field for DefaultFileRegion");
            return JNI_ERR;
        }

        jclass fileChannelCls = (*env)->FindClass(env, "sun/nio/ch/FileChannelImpl");
        if (fileChannelCls == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        fileDescriptorFieldId = (*env)->GetFieldID(env, fileChannelCls, "fd", "Ljava/io/FileDescriptor;");
        if (fileDescriptorFieldId == NULL) {
            throwRuntimeException(env, "Unable to obtain fd field for FileChannelImpl");
            return JNI_ERR;
        }

        jclass fileDescriptorCls = (*env)->FindClass(env, "java/io/FileDescriptor");
        if (fileDescriptorCls == NULL) {
            // pending exception...
            return JNI_ERR;
        }
        fdFieldId = (*env)->GetFieldID(env, fileDescriptorCls, "fd", "I");
        if (fdFieldId == NULL) {
            throwRuntimeException(env, "Unable to obtain fd field for FileDescriptor");
            return JNI_ERR;
        }

        inetSocketAddrMethodId = (*env)->GetMethodID(env, inetSocketAddressClass, "<init>", "(Ljava/lang/String;I)V");
        if (inetSocketAddrMethodId == NULL) {
            throwRuntimeException(env, "Unable to obtain constructor of InetSocketAddress");
            return JNI_ERR;
        }
        socketType = socket_type();

        datagramSocketAddrMethodId = (*env)->GetMethodID(env, datagramSocketAddressClass, "<init>", "(Ljava/lang/String;II)V");
        if (datagramSocketAddrMethodId == NULL) {
            throwRuntimeException(env, "Unable to obtain constructor of DatagramSocketAddress");
            return JNI_ERR;
        }
        return JNI_VERSION_1_6;
    }
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    } else {
        // delete global references so the GC can collect them
        if (runtimeExceptionClass != NULL) {
            (*env)->DeleteGlobalRef(env, runtimeExceptionClass);
        }
        if (ioExceptionClass != NULL) {
            (*env)->DeleteGlobalRef(env, ioExceptionClass);
        }
        if (closedChannelExceptionClass != NULL) {
            (*env)->DeleteGlobalRef(env, closedChannelExceptionClass);
        }
        if (inetSocketAddressClass != NULL) {
            (*env)->DeleteGlobalRef(env, inetSocketAddressClass);
        }
        if (datagramSocketAddressClass != NULL) {
            (*env)->DeleteGlobalRef(env, datagramSocketAddressClass);
        }
    }
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_eventFd(JNIEnv * env, jclass clazz) {
    jint eventFD =  eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);

    if (eventFD < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error calling eventfd(...): ", err));
    }
    return eventFD;
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_eventFdWrite(JNIEnv * env, jclass clazz, jint fd, jlong value) {
    jint eventFD = eventfd_write(fd, (eventfd_t)value);

    if (eventFD < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error calling eventfd_write(...): ", err));
    }
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_eventFdRead(JNIEnv * env, jclass clazz, jint fd) {
    uint64_t eventfd_t;

    if (eventfd_read(fd, &eventfd_t) != 0) {
        // something is serious wrong
        throwRuntimeException(env, "Error calling eventfd_read(...)");
    }
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_epollCreate(JNIEnv * env, jclass clazz) {
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
            throwRuntimeException(env, exceptionMessage("Error during epoll_create1(...): ", err));
        } else {
            throwRuntimeException(env, exceptionMessage("Error during epoll_create(...): ", err));
        }
        return efd;
    }
    if (!epoll_create1) {
        if (fcntl(efd, F_SETFD, FD_CLOEXEC) < 0) {
            int err = errno;
            close(efd);
            throwRuntimeException(env, exceptionMessage("Error during fcntl(...): ", err));
            return err;
        }
    }
    return efd;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_epollWait(JNIEnv * env, jclass clazz, jint efd, jlongArray events, jint timeout) {
    int len = (*env)->GetArrayLength(env, events);
    struct epoll_event ev[len];
    int ready;
    int err;
    do {
       ready = epoll_wait(efd, ev, len, timeout);
       // was interrupted try again.
    } while (ready == -1 && (( err = errno) == EINTR));

    if (ready < 0) {
         throwIOException(env, exceptionMessage("Error during epoll_wait(...): ", err));
         return -1;
    }
    if (ready == 0) {
        // nothing ready for process
        return 0;
    }

    jboolean isCopy;
    // Use GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical to signal the VM that we really would like
    // to not do a memory copy here. This is ok as we not do any blocking action here anyway.
    // This is important as the VM may suspend GC for the time!
    jlong *elements = (*env)->GetPrimitiveArrayCritical(env, events, &isCopy);
    if (elements == NULL) {
        // No memory left ?!?!?
        throwOutOfMemoryError(env, "Can't allocate memory");
        return -1;
    }
    int i;
    for (i = 0; i < ready; i++) {
        // store the ready ops and id
        elements[i] = (jlong) ev[i].data.u64;
        if (ev[i].events & EPOLLIN) {
            elements[i] |= EPOLL_READ;
        }
        if (ev[i].events & EPOLLRDHUP) {
            elements[i] |= EPOLL_RDHUP;
        }
        if (ev[i].events & EPOLLOUT) {
            elements[i] |= EPOLL_WRITE;
        }
    }
    jint mode;
    // release again to prevent memory leak
    if (isCopy) {
        mode = 0;
    } else {
        // was just pinned so use JNI_ABORT to eliminate not needed copy.
        mode = JNI_ABORT;
    }
    (*env)->ReleasePrimitiveArrayCritical(env, events, elements, mode);

    return ready;
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_epollCtlAdd(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id) {
    if (epollCtl(env, efd, EPOLL_CTL_ADD, fd, flags, id) < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error during calling epoll_ctl(...): ", err));
    }
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_epollCtlMod(JNIEnv * env, jclass clazz, jint efd, jint fd, jint flags, jint id) {
    if (epollCtl(env, efd, EPOLL_CTL_MOD, fd, flags, id) < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error during calling epoll_ctl(...): ", err));
    }
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_epollCtlDel(JNIEnv * env, jclass clazz, jint efd, jint fd) {
    // Create an empty event to workaround a bug in older kernels which can not handle NULL.
    struct epoll_event event = { 0 };
    if (epoll_ctl(efd, EPOLL_CTL_DEL, fd, &event) < 0) {
        int err = errno;
        throwRuntimeException(env, exceptionMessage("Error during calling epoll_ctl(...): ", err));
    }
}

jint write0(JNIEnv * env, jclass clazz, jint fd, void *buffer, jint pos, jint limit) {
    ssize_t res;
    int err;
    do {
       res = write(fd, buffer + pos, (size_t) (limit - pos));
       // keep on writing if it was interrupted
    } while(res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        // network stack saturated... try again later
        if (err == EAGAIN || err == EWOULDBLOCK) {
            return 0;
        }
        if (err == EBADF) {
            throwClosedChannelException(env);
            return -1;
        }
        throwIOException(env, exceptionMessage("Error while write(...): ", err));
        return -1;
    }
    return (jint) res;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_write(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    void *buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
    if (buffer == NULL) {
        throwRuntimeException(env, "Unable to access address of buffer");
        return -1;
    }
    return  write0(env, clazz, fd, buffer, pos, limit);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_writeAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return write0(env, clazz, fd, (void *) address, pos, limit);
}

jint sendTo0(JNIEnv * env, jint fd, void* buffer, jint pos, jint limit ,jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (init_sockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    ssize_t res;
    int err;
    do {
       res = sendto(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr *)&addr, sizeof(struct sockaddr_storage));
       // keep on writing if it was interrupted
    } while(res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        // network stack saturated... try again later
        if (err == EAGAIN || err == EWOULDBLOCK) {
            return 0;
        }
        if (err == EBADF) {
            throwClosedChannelException(env);
            return -1;
        }
        throwIOException(env, exceptionMessage("Error while sendto(...): ", err));
        return -1;
    }
    return (jint) res;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_sendTo(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit, jbyteArray address, jint scopeId, jint port) {
    void *buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
    if (buffer == NULL) {
        throwRuntimeException(env, "Unable to access address of buffer");
        return -1;
    }
    return sendTo0(env, fd, buffer, pos, limit, address, scopeId, port);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_sendToAddress(JNIEnv * env, jclass clazz, jint fd, jlong memoryAddress, jint pos, jint limit ,jbyteArray address, jint scopeId, jint port) {
    return sendTo0(env, fd, (void*) memoryAddress, pos, limit, address, scopeId, port);
}

jobject recvFrom0(JNIEnv * env, jint fd, void* buffer, jint pos, jint limit) {
    struct sockaddr_storage addr;
    socklen_t addrlen = sizeof(addr);
    ssize_t res;
    int err;

    do {
        res = recvfrom(fd, buffer + pos, (size_t) (limit - pos), 0, (struct sockaddr *)&addr, &addrlen);
        // Keep on reading if we was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            // Nothing left to read
            return NULL;
        }
        if (err == EBADF) {
            throwClosedChannelException(env);
            return NULL;
        }
        throwIOException(env, exceptionMessage("Error while recvFrom(...): ", err));
        return NULL;
    }

    return createDatagramSocketAddress(env, addr, res);
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_epoll_Native_recvFrom(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    void *buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
    if (buffer == NULL) {
        throwRuntimeException(env, "Unable to access address of buffer");
        return NULL;
    }

    return recvFrom0(env, fd, buffer, pos, limit);
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_epoll_Native_recvFromAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return recvFrom0(env, fd, (void*) address, pos, limit);
}

jlong writev0(JNIEnv * env, jclass clazz, jint fd, struct iovec * iov, jint length) {
    ssize_t res;
    int err;
    do {
        res = writev(fd, iov, length);
        // keep on writing if it was interrupted
    } while(res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            // network stack is saturated we will try again later
            return 0;
        }
        if (err == EBADF) {
            throwClosedChannelException(env);
            return -1;
        }
        throwIOException(env, exceptionMessage("Error while writev(...): ", err));
        return -1;
    }
    return (jlong) res;
}

JNIEXPORT jlong JNICALL Java_io_netty_channel_epoll_Native_writev(JNIEnv * env, jclass clazz, jint fd, jobjectArray buffers, jint offset, jint length) {
    struct iovec iov[length];
    int iovidx = 0;
    int i;
    int num = offset + length;
    for (i = offset; i < num; i++) {
        jobject bufObj = (*env)->GetObjectArrayElement(env, buffers, i);
        jint pos;
        // Get the current position using the (*env)->GetIntField if possible and fallback
        // to slower (*env)->CallIntMethod(...) if needed
        if (posFieldId == NULL) {
            pos = (*env)->CallIntMethod(env, bufObj, posId, NULL);
        } else {
            pos = (*env)->GetIntField(env, bufObj, posFieldId);
        }
        jint limit;
        // Get the current limit using the (*env)->GetIntField if possible and fallback
        // to slower (*env)->CallIntMethod(...) if needed
        if (limitFieldId == NULL) {
            limit = (*env)->CallIntMethod(env, bufObj, limitId, NULL);
        } else {
            limit = (*env)->GetIntField(env, bufObj, limitFieldId);
        }
        void *buffer = (*env)->GetDirectBufferAddress(env, bufObj);
        if (buffer == NULL) {
            throwRuntimeException(env, "Unable to access address of buffer");
            return -1;
        }
        iov[iovidx].iov_base = buffer + pos;
        iov[iovidx].iov_len = (size_t) (limit - pos);
        iovidx++;

        // Explicit delete local reference as otherwise the local references will only be released once the native method returns.
        // Also there may be a lot of these and JNI specification only specify that 16 must be able to be created.
        //
        // See https://github.com/netty/netty/issues/2623
        (*env)->DeleteLocalRef(env, bufObj);
    }
    return writev0(env, clazz, fd, iov, length);
}

JNIEXPORT jlong JNICALL Java_io_netty_channel_epoll_Native_writevAddresses(JNIEnv * env, jclass clazz, jint fd, jlong memoryAddress, jint length) {
    struct iovec * iov = (struct iovec *) memoryAddress;
    return writev0(env, clazz, fd, iov, length);
}

jint read0(JNIEnv * env, jclass clazz, jint fd, void *buffer, jint pos, jint limit) {
    ssize_t res;
    int err;
    do {
        res = read(fd, buffer + pos, (size_t) (limit - pos));
        // Keep on reading if we was interrupted
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            // Nothing left to read
            return 0;
        }
        if (err == EBADF) {
            throwClosedChannelException(env);
            return -1;
        }
        throwIOException(env, exceptionMessage("Error while read(...): ", err));
        return -1;
    }

    if (res == 0) {
        // end-of-stream
        return -1;
    }
    return (jint) res;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_read(JNIEnv * env, jclass clazz, jint fd, jobject jbuffer, jint pos, jint limit) {
    void *buffer = (*env)->GetDirectBufferAddress(env, jbuffer);
    if (buffer == NULL) {
        throwRuntimeException(env, "Unable to access address of buffer");
        return -1;
    }
    return read0(env, clazz, fd, buffer, pos, limit);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_readAddress(JNIEnv * env, jclass clazz, jint fd, jlong address, jint pos, jint limit) {
    return read0(env, clazz, fd, (void*) address, pos, limit);
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_close(JNIEnv * env, jclass clazz, jint fd) {
   if (close(fd) < 0) {
      throwIOException(env, "Error closing file descriptor");
   }
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_shutdown(JNIEnv * env, jclass clazz, jint fd, jboolean read, jboolean write) {
    int mode;
    if (read && write) {
        mode = SHUT_RDWR;
    } else if (read) {
        mode = SHUT_RD;
    } else if (write) {
        mode = SHUT_WR;
    }
    if (shutdown(fd, mode) < 0) {
        throwIOException(env, "Error shutdown socket file descriptor");
    }
}

jint socket0(JNIEnv * env, jclass clazz, int type) {
    // TODO: Maybe also respect -Djava.net.preferIPv4Stack=true
    int fd = socket(socketType, type | SOCK_NONBLOCK, 0);
    if (fd == -1) {
        int err = errno;
        throwIOException(env, exceptionMessage("Error creating socket: ", err));
        return -1;
    } else if (socketType == AF_INET6){
        // Allow to listen /connect ipv4 and ipv6
        int optval = 0;
        if (setOption(env, fd, IPPROTO_IPV6, IPV6_V6ONLY, &optval, sizeof(optval)) < 0) {
            // Something went wrong so close the fd and return here. setOption(...) itself throws the exception already.
            close(fd);
            return -1;
        }
    }
    return fd;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_socketDgram(JNIEnv * env, jclass clazz) {
    return socket0(env, clazz, SOCK_DGRAM);
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_socketStream(JNIEnv * env, jclass clazz) {
    return socket0(env, clazz, SOCK_STREAM);
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_bind(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (init_sockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    if(bind(fd, (struct sockaddr *) &addr, sizeof(addr)) == -1){
        int err = errno;
        throwIOException(env, exceptionMessage("Error during bind(...): ", err));
    }
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_listen(JNIEnv * env, jclass clazz, jint fd, jint backlog) {
    if(listen(fd, backlog) == -1) {
        int err = errno;
        throwIOException(env, exceptionMessage("Error during listen(...): ", err));
    }
}

JNIEXPORT jboolean JNICALL Java_io_netty_channel_epoll_Native_connect(JNIEnv * env, jclass clazz, jint fd, jbyteArray address, jint scopeId, jint port) {
    struct sockaddr_storage addr;
    if (init_sockaddr(env, address, scopeId, port, &addr) == -1) {
        return -1;
    }

    int res;
    int err;
    do {
        res = connect(fd, (struct sockaddr *) &addr, sizeof(addr));
    } while (res == -1 && ((err = errno) == EINTR));

    if (res < 0) {
        if (err == EINPROGRESS) {
            // connect not complete yet need to wait for EPOLLOUT event
            return JNI_FALSE;
        }
        throwIOException(env, exceptionMessage("Unable to connect to remote host: ", err));

        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_io_netty_channel_epoll_Native_finishConnect(JNIEnv * env, jclass clazz, jint fd) {
    // connect may be done
    // return true if connection finished successfully
    // return false if connection is still in progress
    // throw exception if connection failed
    int optval;
    int res = getOption(env, fd, SOL_SOCKET, SO_ERROR, &optval, sizeof(optval));
    if (res != 0) {
        // getOption failed
        throwIOException(env, exceptionMessage("finishConnect getOption failed: ", res));
        return JNI_FALSE;
    } else if (optval == EINPROGRESS) {
        // connect still in progress
        return JNI_FALSE;
    } else if (optval == 0) {
        // connect succeeded
        return JNI_TRUE;
    } else {
        // connect failed
        throwIOException(env, exceptionMessage("Unable to connect to remote host: ", optval));
        return JNI_FALSE;
    }
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_accept(JNIEnv * env, jclass clazz, jint fd) {
    jint socketFd;
    int err;

    do {
        if (accept4) {
            socketFd = accept4(fd, NULL, 0, SOCK_NONBLOCK | SOCK_CLOEXEC);
        } else  {
            socketFd = accept(fd, NULL, 0);
        }
    } while (socketFd == -1 && ((err = errno) == EINTR));

    if (socketFd == -1) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            // Everything consumed so just return -1 here.
            return -1;
        } else {
            throwIOException(env, exceptionMessage("Error during accept(...): ", err));
            return -1;
        }
    }
    if (accept4)  {
        return socketFd;
    } else  {
        // accept4 was not present so need two more sys-calls ...
        if (fcntl(socketFd, F_SETFD, FD_CLOEXEC) == -1) {
            throwIOException(env, exceptionMessage("Error during accept(...): ", err));
            return -1;
        }
        if (fcntl(socketFd, F_SETFL, O_NONBLOCK) == -1) {
            throwIOException(env, exceptionMessage("Error during accept(...): ", err));
            return -1;
        }
    }
    return socketFd;
}

JNIEXPORT jlong JNICALL Java_io_netty_channel_epoll_Native_sendfile(JNIEnv *env, jclass clazz, jint fd, jobject fileRegion, jlong base_off, jlong off, jlong len) {
    jobject fileChannel = (*env)->GetObjectField(env, fileRegion, fileChannelFieldId);
    if (fileChannel == NULL) {
        throwRuntimeException(env, "Unable to obtain FileChannel from FileRegion");
        return -1;
    }
    jobject fileDescriptor = (*env)->GetObjectField(env, fileChannel, fileDescriptorFieldId);
    if (fileDescriptor == NULL) {
        throwRuntimeException(env, "Unable to obtain FileDescriptor from FileChannel");
        return -1;
    }
    jint srcFd = (*env)->GetIntField(env, fileDescriptor, fdFieldId);
    if (srcFd == -1) {
        throwRuntimeException(env, "Unable to obtain the fd from the FileDescriptor");
        return -1;
    }
    ssize_t res;
    off_t offset = base_off + off;
    int err;
    do {
      res = sendfile(fd, srcFd, &offset, (size_t) len);
    } while (res == -1 && ((err = errno) == EINTR));
    if (res < 0) {
        if (err == EAGAIN) {
            return 0;
        }
        throwIOException(env, exceptionMessage("Error during sendfile(...): ", err));
        return -1;
    }
    if (res > 0) {
        // update the transfered field in DefaultFileRegion
        (*env)->SetLongField(env, fileRegion, transferedFieldId, off + res);
    }

    return res;
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_epoll_Native_remoteAddress(JNIEnv * env, jclass clazz, jint fd) {
    socklen_t len;
    struct sockaddr_storage addr;

    len = sizeof addr;
    if (getpeername(fd, (struct sockaddr*)&addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddress(env, addr);
}

JNIEXPORT jobject JNICALL Java_io_netty_channel_epoll_Native_localAddress(JNIEnv * env, jclass clazz, jint fd) {
    socklen_t len;
    struct sockaddr_storage addr;

    len = sizeof addr;
    if (getsockname(fd, (struct sockaddr*)&addr, &len) == -1) {
        return NULL;
    }
    return createInetSocketAddress(env, addr);
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setReuseAddress(JNIEnv * env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setReusePort(JNIEnv * env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setTcpNoDelay(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setReceiveBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setSendBufferSize(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setKeepAlive(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_KEEPALIVE, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setTcpCork(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_TCP, TCP_CORK, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setSoLinger(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setTrafficClass(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    struct linger solinger;
    if (optval < 0) {
        solinger.l_onoff = 0;
        solinger.l_linger = 0;
    } else {
        solinger.l_onoff = 1;
        solinger.l_linger = optval;
    }
    setOption(env, fd, SOL_SOCKET, SO_LINGER, &solinger, sizeof(solinger));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setBroadcast(JNIEnv * env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setTcpKeepIdle(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_TCP, TCP_KEEPIDLE, &optval, sizeof(optval));
}

JNIEXPORT void JNICALL Java_io_netty_channel_epoll_Native_setTcpKeepIntvl(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_TCP, TCP_KEEPINTVL, &optval, sizeof(optval));
}

JNIEXPORT void Java_io_netty_channel_epoll_Native_setTcpKeepCnt(JNIEnv *env, jclass clazz, jint fd, jint optval) {
    setOption(env, fd, SOL_TCP, TCP_KEEPCNT, &optval, sizeof(optval));
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_isReuseAddresss(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_isReusePort(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_SOCKET, SO_REUSEPORT, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_isTcpNoDelay(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getReceiveBufferSize(JNIEnv * env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_SOCKET, SO_RCVBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getSendBufferSize(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_SOCKET, SO_SNDBUF, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_isTcpCork(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_TCP, TCP_CORK, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getSoLinger(JNIEnv *env, jclass clazz, jint fd) {
    struct linger optval;
    if (getOption(env, fd, SOL_SOCKET, SO_LINGER, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    if (optval.l_onoff == 0) {
        return -1;
    } else {
        return optval.l_linger;
    }
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getTrafficClass(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, IPPROTO_IP, IP_TOS, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_isBroadcast(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_SOCKET, SO_BROADCAST, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getTcpKeepIdle(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_TCP, TCP_KEEPIDLE, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getTcpKeepIntvl(JNIEnv *env, jclass clazz, jint fd) {
    int optval;
    if (getOption(env, fd, SOL_TCP, TCP_KEEPINTVL, &optval, sizeof(optval)) == -1) {
        return -1;
    }
    return optval;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_getTcpKeepCnt(JNIEnv *env, jclass clazz, jint fd) {
     int optval;
     if (getOption(env, fd, SOL_TCP, TCP_KEEPCNT, &optval, sizeof(optval)) == -1) {
         return -1;
     }
     return optval;
}

JNIEXPORT jstring JNICALL Java_io_netty_channel_epoll_Native_kernelVersion(JNIEnv *env, jclass clazz) {
    struct utsname name;

    int res = uname(&name);
    if (res == 0) {
        return (*env)->NewStringUTF(env, name.release);
    }
    int err = errno;
    throwRuntimeException(env, exceptionMessage("Error during uname(...): ", err));
    return NULL;
}

JNIEXPORT jint JNICALL Java_io_netty_channel_epoll_Native_iovMax(JNIEnv *env, jclass clazz) {
    return IOV_MAX;
}

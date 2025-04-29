/*
 * Copyright 2020 The Netty Project
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
#define _GNU_SOURCE // RTLD_DEFAULT
#include "netty_io_uring.h"
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "syscall.h"
#include "netty_io_uring_linuxsocket.h"
#include <syscall.h>

#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <sys/eventfd.h>
#include <poll.h>
// Needed for UDP_SEGMENT
#include <netinet/udp.h>
#include <sys/utsname.h>

// Allow to compile on systems with older kernels.
#ifndef UDP_SEGMENT
#define UDP_SEGMENT 103
#endif

// Add define if NETTY_IO_URING_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_IO_URING_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

// Allow to compile on systems with older kernels
#ifndef MSG_FASTOPEN
#define MSG_FASTOPEN 0x20000000
#endif

#define NATIVE_CLASSNAME "io/netty/channel/uring/Native"
#define STATICALLY_CLASSNAME "io/netty/channel/uring/NativeStaticallyReferencedJniMethods"
#define LIBRARYNAME "netty_transport_native_io_uring42"

static jclass longArrayClass = NULL;
static char* staticPackagePrefix = NULL;
static int register_unix_called = 0;

static jfieldID fileChannelFieldId = NULL;
static jfieldID fdFieldId = NULL;
static jfieldID fileDescriptorFieldId = NULL;

static void netty_io_uring_native_JNI_OnUnLoad(JNIEnv* env, const char* packagePrefix) {
    netty_unix_limits_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_errors_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_filedescriptor_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_socket_JNI_OnUnLoad(env, packagePrefix);
    netty_unix_buffer_JNI_OnUnLoad(env, packagePrefix);

    NETTY_JNI_UTIL_UNLOAD_CLASS(env, longArrayClass);
}

static void io_uring_unmap_rings(struct io_uring_sq *sq, struct io_uring_cq *cq) {
    if (sq->ring_ptr != NULL) {
        munmap(sq->ring_ptr, sq->ring_sz);
    }
    if (cq->ring_ptr != NULL && cq->ring_ptr != sq->ring_ptr) {
        munmap(cq->ring_ptr, cq->ring_sz);
  }
}

static int io_uring_mmap(int fd, struct io_uring_params *p, struct io_uring_sq *sq, struct io_uring_cq *cq) {
    size_t size;
    int ret;
    int index;

    sq->ring_sz = p->sq_off.array + p->sq_entries * sizeof(unsigned);
    cq->ring_sz = p->cq_off.cqes + p->cq_entries * sizeof(struct io_uring_cqe);

    if ((p->features & IORING_FEAT_SINGLE_MMAP) == 1) {
        if (cq->ring_sz > sq->ring_sz) {
            sq->ring_sz = cq->ring_sz;
        }
        cq->ring_sz = sq->ring_sz;
    }
    sq->ring_ptr = mmap(0, sq->ring_sz, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_SQ_RING);
    if (sq->ring_ptr == MAP_FAILED) {
        return -errno;
    }

    if ((p->features & IORING_FEAT_SINGLE_MMAP) == 1) {
        cq->ring_ptr = sq->ring_ptr;
    } else {
        cq->ring_ptr = mmap(0, cq->ring_sz, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_CQ_RING);
        if (cq->ring_ptr == MAP_FAILED) {
            cq->ring_ptr = NULL;
            ret = -errno;
            goto err;
        }
    }

    sq->khead = sq->ring_ptr + p->sq_off.head;
    sq->ktail = sq->ring_ptr + p->sq_off.tail;
    sq->kring_mask = sq->ring_ptr + p->sq_off.ring_mask;
    sq->kring_entries = sq->ring_ptr + p->sq_off.ring_entries;
    sq->kflags = sq->ring_ptr + p->sq_off.flags;
    sq->kdropped = sq->ring_ptr + p->sq_off.dropped;
    sq->array = sq->ring_ptr + p->sq_off.array;
    size = p->sq_entries * sizeof(struct io_uring_sqe);
    sq->sqes = mmap(0, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_SQES);
    if (sq->sqes == MAP_FAILED) {
        ret = -errno;
        goto err;
    }

    cq->khead = cq->ring_ptr + p->cq_off.head;
    cq->ktail = cq->ring_ptr + p->cq_off.tail;
    cq->kring_mask = cq->ring_ptr + p->cq_off.ring_mask;
    cq->kring_entries = cq->ring_ptr + p->cq_off.ring_entries;
    cq->koverflow = cq->ring_ptr + p->cq_off.overflow;
    cq->cqes = cq->ring_ptr + p->cq_off.cqes;

    if (!(p->flags & IORING_SETUP_NO_SQARRAY)) {
        for (index = 0; index < p->sq_entries; index++) {
            sq->array[index] = index;
        }
    }

    return 0;
err:
    io_uring_unmap_rings(sq, cq);
    return ret;
}

static jint netty_io_uring_enter(JNIEnv *env, jclass class1, jint ring_fd, jint to_submit,
                                 jint min_complete, jint flags) {
    int result;
    int err;
    do {
        result = sys_io_uring_enter(ring_fd, to_submit, min_complete, flags, NULL);
        if (result >= 0) {
            return result;
        }
    } while ((err = errno) == EINTR);
    return -err;
}

static jstring netty_io_uring_kernel_version(JNIEnv* env, jclass clazz) {
    struct utsname u;
    uname(&u);

    jstring result = (*env)->NewStringUTF(env, u.release);
    return result;
}

static jint netty_epoll_native_blocking_event_fd(JNIEnv* env, jclass clazz) {
    // We use a blocking fd with io_uring FAST_POLL read
    jint eventFD = eventfd(0, EFD_CLOEXEC);

    if (eventFD < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd() failed: ", errno);
    }
    return eventFD;
}

static void netty_io_uring_eventFdWrite(JNIEnv* env, jclass clazz, jint fd, jlong value) {
    int result;
    int err;
    do {
        result = eventfd_write(fd, (eventfd_t) value);
        if (result >= 0) {
            return;
        }
    } while ((err = errno) == EINTR);
    netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd_write(...) failed: ", err);
}

static jint netty_io_uring_getFd0(JNIEnv* env, jclass clazz, jobject fileRegion) {
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
    return srcFd;
}

static void netty_io_uring_ring_buffer_exit(JNIEnv *env, jclass clazz,
        jlong submissionQueueArrayAddress, jint submissionQueueRingEntries, jlong submissionQueueRingAddress, jint submissionQueueRingSize,
        jlong completionQueueRingAddress, jint completionQueueRingSize, jint ringFd, jint enterRingFd) {
    void* sqa = (void *) submissionQueueArrayAddress;
    void* sqr = (void *) submissionQueueRingAddress;
    void* cqr = (void *) completionQueueRingAddress;

    if (sqa != NULL) {
        munmap(sqa, submissionQueueRingEntries * sizeof(struct io_uring_sqe));
    }
    if (sqr != NULL) {
        munmap(sqr, submissionQueueRingSize);
    }
    if (cqr != NULL && cqr != sqr) {
        munmap(cqr, completionQueueRingSize);
    }
    if (enterRingFd != ringFd) {
         struct io_uring_rsrc_update up = {
            .offset = enterRingFd,
        };
        sys_io_uring_register(ringFd, IORING_UNREGISTER_RING_FDS, &up, 1);
    }
    close(ringFd);
}

static jboolean netty_io_uring_setup_supports_flags(JNIEnv *env, jclass clazz, jint flags) {
    struct io_uring_params p;
    memset(&p, 0, sizeof(p));
    p.flags = (__u32) flags;

    if (flags & IORING_SETUP_CQSIZE) {
        p.cq_entries = (__u32) 4;
    }

    // Just try to create the ring and if it works we know the flags are supported.
    int ring_fd = sys_io_uring_setup((int) 1, &p);
    if (ring_fd < 0) {
        return JNI_FALSE;
    }
    close(ring_fd);
    return JNI_TRUE;
}

static jboolean netty_io_uring_probe(JNIEnv *env, jclass clazz, jint ring_fd, jintArray ops) {
    jboolean supported = JNI_FALSE;
    struct io_uring_probe *probe;
    size_t mallocLen = sizeof(*probe) + 256 * sizeof(struct io_uring_probe_op);
    probe = malloc(mallocLen);
    memset(probe, 0, mallocLen);

    if (sys_io_uring_register(ring_fd, IORING_REGISTER_PROBE, probe, 256) < 0) {
        netty_unix_errors_throwRuntimeExceptionErrorNo(env, "failed to probe via sys_io_uring_register(....) ", errno);
        goto done;
    }

    jsize opsLen = (*env)->GetArrayLength(env, ops);
    jint *opsElements = (*env)->GetIntArrayElements(env, ops, 0);
    if (opsElements == NULL) {
        goto done;
    }
    int i;
    for (i = 0; i < opsLen; i++) {
        int op = opsElements[i];
        if (op > probe->last_op || (probe->ops[op].flags & IO_URING_OP_SUPPORTED) == 0) {
            goto done;
        }
    }
    // all supported
    supported = JNI_TRUE;
done:
    free(probe);
    return supported;
}


static jlongArray netty_io_uring_setup(JNIEnv *env, jclass clazz, jint entries, jint cqSize, jint setupFlags) {
    struct io_uring_params p;
    memset(&p, 0, sizeof(p));

    p.flags = (__u32) setupFlags;

    if (setupFlags & IORING_SETUP_CQSIZE) {
        p.cq_entries = (__u32) cqSize;
    }

    jlongArray array = (*env)->NewLongArray(env, 18);
    if (array == NULL) {
        // This will put an OOME on the stack
        return NULL;
    }

    int ring_fd = sys_io_uring_setup((int)entries, &p);

    if (ring_fd < 0) {
        if (errno == ENOMEM) {
            netty_unix_errors_throwRuntimeExceptionErrorNo(env,
                "failed to allocate memory for io_uring ring; "
                "try raising memlock limit (see getrlimit(RLIMIT_MEMLOCK, ...) or ulimit -l): ", errno);
        } else {
            netty_unix_errors_throwRuntimeExceptionErrorNo(env, "failed to create io_uring ring fd: ", errno);
        }
        return NULL;
    }

    struct io_uring io_uring_ring;
    memset(&io_uring_ring, 0, sizeof(io_uring_ring));
    int ret = io_uring_mmap(ring_fd, &p, &io_uring_ring.sq, &io_uring_ring.cq);

    if (ret != 0) {
        // Close ring fd before return.
        close(ring_fd);
        netty_unix_errors_throwRuntimeExceptionErrorNo(env, "failed to mmap io_uring ring buffer: ", ret);
        return NULL;
    }

    jlong completionArrayElements[] = {
        (jlong)io_uring_ring.cq.khead,
        (jlong)io_uring_ring.cq.ktail,
        // Should be replaced by ring_mask when we depend on later kernel versions
        (jlong)*io_uring_ring.cq.kring_mask,
        // Should be replaced by ring_entries when we depend on later kernel versions
        (jlong)*io_uring_ring.cq.kring_entries,
        (jlong)io_uring_ring.cq.cqes,
        (jlong)io_uring_ring.cq.ring_sz,
        (jlong)io_uring_ring.cq.ring_ptr,
        (jlong)ring_fd,
        (jlong)p.cq_entries
    };
    (*env)->SetLongArrayRegion(env, array, 0, 9, completionArrayElements);

    jlong submissionArrayElements[] = {
        (jlong)io_uring_ring.sq.khead,
        (jlong)io_uring_ring.sq.ktail,
        // Should be replaced by ring_mask when we depend on later kernel versions
        (jlong)*io_uring_ring.sq.kring_mask,
        // Should be replaced by ring_entries when we depend on later kernel versions
        (jlong)*io_uring_ring.sq.kring_entries,
        (jlong)io_uring_ring.sq.sqes,
        (jlong)io_uring_ring.sq.ring_sz,
        (jlong)io_uring_ring.sq.ring_ptr,
        (jlong)ring_fd
    };
    (*env)->SetLongArrayRegion(env, array, 9, 8, submissionArrayElements);

    jlong features = (jlong) p.features;
    (*env)->SetLongArrayRegion(env, array, 17, 1, &features);
    return array;
}

static jint netty_io_uring_register_iowq_max_workers(JNIEnv *env, jclass clazz, jint ringFd, jint maxBoundedValue, jint maxUnboundedValue) {
     int values[2];
     values[0] = maxBoundedValue;
     values[1] = maxUnboundedValue;
     return sys_io_uring_register(ringFd, IORING_REGISTER_IOWQ_MAX_WORKERS, values, 2);
}

static jint netty_io_uring_register_enable_rings(JNIEnv *env, jclass clazz, jint ringFd) {
     return sys_io_uring_register(ringFd, IORING_REGISTER_ENABLE_RINGS, NULL, 0);
}

static jint netty_io_uring_register_ring_fds(JNIEnv *env, jclass clazz, jint ringFd) {
    struct io_uring_rsrc_update up = {
        .data = ringFd,
        .offset = -1U,
    };
    int ret = sys_io_uring_register(ringFd, IORING_REGISTER_RING_FDS, &up, 1);
    if (ret == 1) {
        return up.offset;
    }
    return -1;
}

static jlong netty_io_uring_register_buf_ring(JNIEnv* env, jclass clazz,
                                           jint ringFd, jint nentries,
                                           jshort bgid, jint flags) {
    struct io_uring_buf_ring *br;
    struct io_uring_buf_reg reg;
    size_t ring_size;

    memset(&reg, 0, sizeof(reg));
    ring_size = nentries * sizeof(struct io_uring_buf);
    br = mmap(NULL, ring_size, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
    if (br == MAP_FAILED) {
        return -errno;
    }

    reg.ring_addr = (unsigned long) (uintptr_t) br;
    reg.ring_entries = (__u32) nentries;
    reg.bgid = (__u16) bgid;
    reg.flags |= (__u16) flags;

    int registerRes = sys_io_uring_register(ringFd, IORING_REGISTER_PBUF_RING, &reg, 1);

    if (registerRes) {
        munmap(br, ring_size);
        return registerRes;
    }
    br->tail = 0;
    return (jlong) br;
}

static jint netty_io_uring_buf_ring_size(JNIEnv* env, jclass clazz, jint nentries) {
    return (jint) nentries * sizeof(struct io_uring_buf);
}

static jint netty_io_uring_unregister_buf_ring(JNIEnv* env, jclass clazz,
                                        jint ringFd, jlong br,
                                        jint nentries, jshort bgid) {
    struct io_uring_buf_reg reg = { .bgid = (__u16) bgid };
    int registerRes = sys_io_uring_register(ringFd, IORING_UNREGISTER_PBUF_RING, &reg, 1);
    if (registerRes) {
        return registerRes;
    }
    size_t ring_size = nentries * sizeof(struct io_uring_buf);

    struct io_uring_buf_ring* ring = (struct io_uring_buf_ring *) br;
    if (ring != NULL) {
        munmap(ring, ring_size);
    }
    return 0;
}

static jint netty_create_file(JNIEnv *env, jclass class, jstring filename) {
    const char *file = (*env)->GetStringUTFChars(env, filename, 0);

    int fd =  open(file, O_RDWR | O_TRUNC | O_CREAT, 0644);
    (*env)->ReleaseStringUTFChars(env, filename, file);
    return fd;
}

static jint netty_io_uring_registerUnix(JNIEnv* env, jclass clazz) {
    register_unix_called = 1;
    return netty_unix_register(env, staticPackagePrefix);
}

static jint netty_io_uring_sockNonblock(JNIEnv* env, jclass clazz) {
    return SOCK_NONBLOCK;
}

static jint netty_io_uring_sockCloexec(JNIEnv* env, jclass clazz) {
    return SOCK_CLOEXEC;
}

static jint netty_io_uring_pageSize(JNIEnv* env, jclass clazz) {
    return getpagesize();
}

static jint netty_io_uring_afInet(JNIEnv* env, jclass clazz) {
    return AF_INET;
}

static jint netty_io_uring_afInet6(JNIEnv* env, jclass clazz) {
    return AF_INET6;
}

static jint netty_io_uring_sizeofSockaddrIn(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_in);
}

static jint netty_io_uring_sizeofSockaddrIn6(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_in6);
}

static jint netty_io_uring_sockaddrInOffsetofSinFamily(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_family);
}

static jint netty_io_uring_sockaddrInOffsetofSinPort(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_port);
}

static jint netty_io_uring_sockaddrInOffsetofSinAddr(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in, sin_addr);
}

static jint netty_io_uring_inAddressOffsetofSAddr(JNIEnv* env, jclass clazz) {
    return offsetof(struct in_addr, s_addr);
}

static jint netty_io_uring_sockaddrIn6OffsetofSin6Family(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_family);
}

static jint netty_io_uring_sockaddrIn6OffsetofSin6Port(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_port);
}

static jint netty_io_uring_sockaddrIn6OffsetofSin6Flowinfo(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_flowinfo);
}

static jint netty_io_uring_sockaddrIn6OffsetofSin6Addr(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_addr);
}

static jint netty_io_uring_sockaddrIn6OffsetofSin6ScopeId(JNIEnv* env, jclass clazz) {
    return offsetof(struct sockaddr_in6, sin6_scope_id);
}

static jint netty_io_uring_in6AddressOffsetofS6Addr(JNIEnv* env, jclass clazz) {
    return offsetof(struct in6_addr, s6_addr);
}

static jint netty_io_uring_sizeofSockaddrStorage(JNIEnv* env, jclass clazz) {
    return sizeof(struct sockaddr_storage);
}

static jint netty_io_uring_sizeofSizeT(JNIEnv* env, jclass clazz) {
    return sizeof(size_t);
}

static jint netty_io_uring_sizeofIovec(JNIEnv* env, jclass clazz) {
    return sizeof(struct iovec);
}

static jint netty_io_uring_iovecOffsetofIovBase(JNIEnv* env, jclass clazz) {
    return offsetof(struct iovec, iov_base);
}

static jint netty_io_uring_iovecOffsetofIovLen(JNIEnv* env, jclass clazz) {
    return offsetof(struct iovec, iov_len);
}

static jint netty_io_uring_sizeofMsghdr(JNIEnv* env, jclass clazz) {
    return sizeof(struct msghdr);
}

static jint netty_io_uring_msghdrOffsetofMsgName(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_name);
}

static jint netty_io_uring_msghdrOffsetofMsgNamelen(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_namelen);
}
static jint netty_io_uring_msghdrOffsetofMsgIov(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_iov);
}
static jint netty_io_uring_msghdrOffsetofMsgIovlen(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_iovlen);
}

static jint netty_io_uring_msghdrOffsetofMsgControl(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_control);
}

static jint netty_io_uring_msghdrOffsetofMsgControllen(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_controllen);
}

static jint netty_io_uring_msghdrOffsetofMsgFlags(JNIEnv* env, jclass clazz) {
    return offsetof(struct msghdr, msg_flags);
}

static jint netty_io_uring_cmsghdrOffsetofCmsgLen(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_len);
}

static jint netty_io_uring_cmsghdrOffsetofCmsgLevel(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_level);
}

static jint netty_io_uring_cmsghdrOffsetofCmsgType(JNIEnv* env, jclass clazz) {
    return offsetof(struct cmsghdr, cmsg_type);
}

static jlong netty_io_uring_cmsghdrData(JNIEnv* env, jclass clazz, jlong cmsghdrAddr) {
    return (jlong) CMSG_DATA((struct cmsghdr*) cmsghdrAddr);
}

static jint netty_io_uring_ioUringBufRingOffsetoftail(JNIEnv* env, jclass clazz) {
   return offsetof(struct io_uring_buf_ring, tail);
}

static jint netty_io_uring_ioUringBufOffsetofaddr(JNIEnv* env, jclass clazz) {
   return offsetof(struct io_uring_buf, addr);
}

static jint netty_io_uring_ioUringBufOffsetoflen(JNIEnv* env, jclass clazz) {
    return offsetof(struct io_uring_buf, len);
}

static jint netty_io_uring_ioUringBufOffsetofbid(JNIEnv* env, jclass clazz) {
    return offsetof(struct io_uring_buf, bid);
}

static jint netty_io_uring_sizeofIoUringBuf(JNIEnv* env, jclass clazz) {
    return sizeof(struct io_uring_buf);
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

static jint netty_io_uring_tcpFastopenMode(JNIEnv* env, jclass clazz) {
    int fastopen = 0;
    getSysctlValue("/proc/sys/net/ipv4/tcp_fastopen", &fastopen);
    return fastopen;
}

static jint netty_io_uring_etime(JNIEnv* env, jclass clazz) {
    return ETIME;
}

static jint netty_io_uring_ecanceled(JNIEnv* env, jclass clazz) {
    return ECANCELED;
}

static jint netty_io_uring_enobufs(JNIEnv* env, jclass clazz) {
    return ENOBUFS;
}

static jint netty_io_uring_pollin(JNIEnv* env, jclass clazz) {
    return POLLIN;
}

static jint netty_io_uring_pollout(JNIEnv* env, jclass clazz) {
    return POLLOUT;
}

static jint netty_io_uring_pollrdhup(JNIEnv* env, jclass clazz) {
    return POLLRDHUP;
}

static jint netty_io_uring_ioringEnterGetevents(JNIEnv* env, jclass clazz) {
    return IORING_ENTER_GETEVENTS;
}

static jint netty_io_uring_iosqeAsync(JNIEnv* env, jclass clazz) {
    return IOSQE_ASYNC;
}

static jint netty_io_uring_iosqeLink(JNIEnv* env, jclass clazz) {
    return IOSQE_IO_LINK;
}

static jint netty_io_uring_iosqeDrain(JNIEnv* env, jclass clazz) {
    return IOSQE_IO_DRAIN;
}

static jint netty_io_uring_BufferSelect(JNIEnv* env, jclass clazz) {
    return IOSQE_BUFFER_SELECT;
}

static jint netty_io_uring_msgDontwait(JNIEnv* env, jclass clazz) {
    return MSG_DONTWAIT;
}

static jint netty_io_uring_msgFastopen(JNIEnv* env, jclass clazz) {
    return MSG_FASTOPEN;
}

static jint netty_io_uring_cmsgSpace(JNIEnv* env, jclass clazz) {
    return CMSG_SPACE(sizeof(uint16_t));
}

static jint netty_io_uring_cmsgLen(JNIEnv* env, jclass clazz) {
    return CMSG_LEN(sizeof(uint16_t));
}

static jint netty_io_uring_solUdp(JNIEnv* env, jclass clazz) {
    return SOL_UDP;
}

static jint netty_io_uring_udpSegment(JNIEnv* env, jclass clazz) {
    return UDP_SEGMENT;
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "sockNonblock", "()I", (void *) netty_io_uring_sockNonblock },
  { "sockCloexec", "()I", (void *) netty_io_uring_sockCloexec },
  { "afInet", "()I", (void *) netty_io_uring_afInet },
  { "afInet6", "()I", (void *) netty_io_uring_afInet6 },
  { "sizeofSockaddrIn", "()I", (void *) netty_io_uring_sizeofSockaddrIn },
  { "sizeofSockaddrIn6", "()I", (void *) netty_io_uring_sizeofSockaddrIn6 },
  { "pageSize", "()I", (void*) netty_io_uring_pageSize},
  { "sockaddrInOffsetofSinFamily", "()I", (void *) netty_io_uring_sockaddrInOffsetofSinFamily },
  { "sockaddrInOffsetofSinPort", "()I", (void *) netty_io_uring_sockaddrInOffsetofSinPort },
  { "sockaddrInOffsetofSinAddr", "()I", (void *) netty_io_uring_sockaddrInOffsetofSinAddr },
  { "inAddressOffsetofSAddr", "()I", (void *) netty_io_uring_inAddressOffsetofSAddr },
  { "sockaddrIn6OffsetofSin6Family", "()I", (void *) netty_io_uring_sockaddrIn6OffsetofSin6Family },
  { "sockaddrIn6OffsetofSin6Port", "()I", (void *) netty_io_uring_sockaddrIn6OffsetofSin6Port },
  { "sockaddrIn6OffsetofSin6Flowinfo", "()I", (void *) netty_io_uring_sockaddrIn6OffsetofSin6Flowinfo },
  { "sockaddrIn6OffsetofSin6Addr", "()I", (void *) netty_io_uring_sockaddrIn6OffsetofSin6Addr },
  { "sockaddrIn6OffsetofSin6ScopeId", "()I", (void *) netty_io_uring_sockaddrIn6OffsetofSin6ScopeId },
  { "in6AddressOffsetofS6Addr", "()I", (void *) netty_io_uring_in6AddressOffsetofS6Addr },
  { "sizeofSockaddrStorage", "()I", (void *) netty_io_uring_sizeofSockaddrStorage },
  { "sizeofSizeT", "()I", (void *) netty_io_uring_sizeofSizeT },
  { "sizeofIovec", "()I", (void *) netty_io_uring_sizeofIovec },
  { "cmsgSpace", "()I", (void *) netty_io_uring_cmsgSpace},
  { "cmsgLen", "()I", (void *) netty_io_uring_cmsgLen},
  { "iovecOffsetofIovBase", "()I", (void *) netty_io_uring_iovecOffsetofIovBase },
  { "iovecOffsetofIovLen", "()I", (void *) netty_io_uring_iovecOffsetofIovLen },
  { "sizeofMsghdr", "()I", (void *) netty_io_uring_sizeofMsghdr },
  { "msghdrOffsetofMsgName", "()I", (void *) netty_io_uring_msghdrOffsetofMsgName },
  { "msghdrOffsetofMsgNamelen", "()I", (void *) netty_io_uring_msghdrOffsetofMsgNamelen },
  { "msghdrOffsetofMsgIov", "()I", (void *) netty_io_uring_msghdrOffsetofMsgIov },
  { "msghdrOffsetofMsgIovlen", "()I", (void *) netty_io_uring_msghdrOffsetofMsgIovlen },
  { "msghdrOffsetofMsgControl", "()I", (void *) netty_io_uring_msghdrOffsetofMsgControl },
  { "msghdrOffsetofMsgControllen", "()I", (void *) netty_io_uring_msghdrOffsetofMsgControllen },
  { "msghdrOffsetofMsgFlags", "()I", (void *) netty_io_uring_msghdrOffsetofMsgFlags },
  { "etime", "()I", (void *) netty_io_uring_etime },
  { "ecanceled", "()I", (void *) netty_io_uring_ecanceled },
  { "enobufs", "()I", (void*) netty_io_uring_enobufs},
  { "pollin", "()I", (void *) netty_io_uring_pollin },
  { "pollout", "()I", (void *) netty_io_uring_pollout },
  { "pollrdhup", "()I", (void *) netty_io_uring_pollrdhup },
  { "ioringEnterGetevents", "()I", (void *) netty_io_uring_ioringEnterGetevents },
  { "iosqeAsync", "()I", (void *) netty_io_uring_iosqeAsync },
  { "iosqeLink", "()I", (void *) netty_io_uring_iosqeLink },
  { "iosqeDrain", "()I", (void *) netty_io_uring_iosqeDrain },
  { "iosqeBufferSelect", "()I", (void *) netty_io_uring_BufferSelect },
  { "msgDontwait", "()I", (void *) netty_io_uring_msgDontwait },
  { "msgFastopen", "()I", (void *) netty_io_uring_msgFastopen },
  { "solUdp", "()I", (void *) netty_io_uring_solUdp },
  { "udpSegment", "()I", (void *) netty_io_uring_udpSegment },
  { "cmsghdrOffsetofCmsgLen", "()I", (void *) netty_io_uring_cmsghdrOffsetofCmsgLen },
  { "cmsghdrOffsetofCmsgLevel", "()I", (void *) netty_io_uring_cmsghdrOffsetofCmsgLevel },
  { "cmsghdrOffsetofCmsgType", "()I", (void *) netty_io_uring_cmsghdrOffsetofCmsgType },
  { "ioUringBufferRingOffsetTail", "()I", (void *) netty_io_uring_ioUringBufRingOffsetoftail },
  { "sizeofIoUringBuf", "()I", (void *) netty_io_uring_sizeofIoUringBuf },
  { "ioUringBufferOffsetAddr", "()I", (void *) netty_io_uring_ioUringBufOffsetofaddr },
  { "ioUringBufferOffsetLen", "()I", (void *) netty_io_uring_ioUringBufOffsetoflen },
  { "ioUringBufferOffsetBid", "()I", (void *) netty_io_uring_ioUringBufOffsetofbid },
  { "tcpFastopenMode", "()I", (void *) netty_io_uring_tcpFastopenMode },
};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);

static const JNINativeMethod method_table[] = {
    {"ioUringSetupSupportsFlags", "(I)Z", (void *) netty_io_uring_setup_supports_flags },
    {"ioUringSetup", "(III)[J", (void *) netty_io_uring_setup},
    {"ioUringRegisterIoWqMaxWorkers","(III)I", (void*) netty_io_uring_register_iowq_max_workers },
    {"ioUringRegisterEnableRings","(I)I", (void*) netty_io_uring_register_enable_rings },
    {"ioUringRegisterRingFds","(I)I", (void*) netty_io_uring_register_ring_fds },
    {"ioUringProbe", "(I[I)Z", (void *) netty_io_uring_probe},
    {"ioUringExit", "(JIJIJIII)V", (void *) netty_io_uring_ring_buffer_exit},
    {"createFile", "(Ljava/lang/String;)I", (void *) netty_create_file},
    {"ioUringEnter", "(IIII)I", (void *) netty_io_uring_enter},
    {"blockingEventFd", "()I", (void *) netty_epoll_native_blocking_event_fd},
    {"eventFdWrite", "(IJ)V", (void *) netty_io_uring_eventFdWrite },
    {"registerUnix", "()I", (void *) netty_io_uring_registerUnix },
    {"cmsghdrData", "(J)J", (void *) netty_io_uring_cmsghdrData},
    {"kernelVersion", "()Ljava/lang/String;", (void *) netty_io_uring_kernel_version },
    {"getFd0", "(Ljava/lang/Object;)I", (void *) netty_io_uring_getFd0 },
    {"ioUringRegisterBufRing", "(IISI)J", (void *) netty_io_uring_register_buf_ring },
    {"ioUringUnRegisterBufRing", "(IJIS)I", (void *) netty_io_uring_unregister_buf_ring },
    {"ioUringBufRingSize", "(I)I", (void *) netty_io_uring_buf_ring_size }
};
static const jint method_table_size =
    sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

static jint netty_iouring_native_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int nativeRegistered = 0;
    int staticallyRegistered = 0;
    int linuxsocketOnLoadCalled = 0;
    char* nettyClassName = NULL;
    jclass fileRegionCls = NULL;
    jclass fileChannelCls = NULL;
    jclass fileDescriptorCls = NULL;
    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
    }
    nativeRegistered = 1;

    if (netty_jni_util_register_natives(env, packagePrefix,
                                       NATIVE_CLASSNAME,
                                       method_table, method_table_size) != 0) {
        goto done;
    }
    staticallyRegistered = 1;

    if (netty_io_uring_linuxsocket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    linuxsocketOnLoadCalled = 1;

    NETTY_JNI_UTIL_LOAD_CLASS(env, longArrayClass, "[J", done);

    if (packagePrefix != NULL) {
        staticPackagePrefix = strdup(packagePrefix);
    }

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/channel/DefaultFileRegion", nettyClassName, done);
    NETTY_JNI_UTIL_FIND_CLASS(env, fileRegionCls, nettyClassName, done);
    netty_jni_util_free_dynamic_name(&nettyClassName);

    NETTY_JNI_UTIL_GET_FIELD(env, fileRegionCls, fileChannelFieldId, "file", "Ljava/nio/channels/FileChannel;", done);

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
    if (ret == JNI_ERR) {
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, NATIVE_CLASSNAME);
        }
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (linuxsocketOnLoadCalled == 1) {
            netty_io_uring_linuxsocket_JNI_OnUnLoad(env, packagePrefix);
        }
    }
    return ret;
}

static void netty_iouring_native_JNI_OnUnload(JNIEnv* env) {
    netty_jni_util_unregister_natives(env, staticPackagePrefix, NATIVE_CLASSNAME);
    netty_jni_util_unregister_natives(env, staticPackagePrefix, STATICALLY_CLASSNAME);
    netty_io_uring_native_JNI_OnUnLoad(env, staticPackagePrefix);

    if (register_unix_called == 1) {
        register_unix_called = 0;
        netty_unix_unregister(env, staticPackagePrefix);
    }
    if (staticPackagePrefix != NULL) {
        free((void *) staticPackagePrefix);
        staticPackagePrefix = NULL;
    }
}

// Invoked by the JVM when statically linked
JNIEXPORT jint JNI_OnLoad_netty_transport_native_io_uring(JavaVM* vm, void* reserved) {
    jint ret = netty_jni_util_JNI_OnLoad(vm, reserved, LIBRARYNAME, netty_iouring_native_JNI_OnLoad);
    return ret;
}

// Invoked by the JVM when statically linked
JNIEXPORT void JNI_OnUnload_netty_transport_native_io_uring(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_iouring_native_JNI_OnUnload);
}

#ifndef NETTY_IO_URING_BUILD_STATIC
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return netty_jni_util_JNI_OnLoad(vm, reserved, LIBRARYNAME, netty_iouring_native_JNI_OnLoad);
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    netty_jni_util_JNI_OnUnload(vm, reserved, netty_iouring_native_JNI_OnUnload);
}
#endif /* NETTY_IO_URING_BUILD_STATIC */


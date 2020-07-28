/*
 * Copyright 2020 The Netty Project
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
#define _GNU_SOURCE // RTLD_DEFAULT
#include "io_uring.h"
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
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <syscall.h>
#include <unistd.h>

#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

static jmethodID ringBufferMethodId = NULL;
static jmethodID ioUringSubmissionQueueMethodId = NULL;
static jmethodID ioUringCommpletionQueueMethodId = NULL;
static jclass ringBufferClass = NULL;
static jclass ioUringCompletionQueueClass = NULL;
static jclass ioUringSubmissionQueueClass = NULL;

void io_uring_unmap_rings(struct io_uring_sq *sq, struct io_uring_cq *cq) {
    munmap(sq->ring_ptr, sq->ring_sz);
    if (cq->ring_ptr && cq->ring_ptr != sq->ring_ptr) {
        munmap(cq->ring_ptr, cq->ring_sz);
  }
}

static int io_uring_mmap(int fd, struct io_uring_params *p,
			 struct io_uring_sq *sq, struct io_uring_cq *cq)
{
	size_t size;
	int ret;

	sq->ring_sz = p->sq_off.array + p->sq_entries * sizeof(unsigned);
	cq->ring_sz = p->cq_off.cqes + p->cq_entries * sizeof(struct io_uring_cqe);

	if ((p->features & IORING_FEAT_SINGLE_MMAP) == 1) {
		if (cq->ring_sz > sq->ring_sz) {
			sq->ring_sz = cq->ring_sz;
		}
		cq->ring_sz = sq->ring_sz;
	}
	sq->ring_ptr = mmap(0, sq->ring_sz, PROT_READ | PROT_WRITE,
			MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_SQ_RING);
	if (sq->ring_ptr == MAP_FAILED) {
		return -errno;
    }

	if ((p->features & IORING_FEAT_SINGLE_MMAP) == 1) {
	    cq->ring_ptr = sq->ring_ptr;
	} else {
		cq->ring_ptr = mmap(0, cq->ring_sz, PROT_READ | PROT_WRITE,
				MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_CQ_RING);
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
	sq->sqes = mmap(0, size, PROT_READ | PROT_WRITE,
				MAP_SHARED | MAP_POPULATE, fd,
				IORING_OFF_SQES);
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

	return 0;
err:
	io_uring_unmap_rings(sq, cq);
	return ret;
}

void setup_io_uring(int ring_fd, struct io_uring *io_uring_ring,
                    struct io_uring_params *p) {
    int ret;

    ret = io_uring_mmap(ring_fd, p, &io_uring_ring->sq, &io_uring_ring->cq);
    if (ret == 0) {
        io_uring_ring->flags = p->flags;
        io_uring_ring->ring_fd = ring_fd;
    } else {
        //Todo signal this back to EventLoop
        perror("setup_io_uring error \n");
    }
}

static jint netty_io_uring_enter(JNIEnv *env, jclass class1, jint ring_fd, jint to_submit,
                                 jint min_complete, jint flags) {
    return sys_io_uring_enter(ring_fd, to_submit, min_complete, flags, NULL);
}

static int nettyBlockingSocket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

static jobject netty_io_uring_setup(JNIEnv *env, jclass class1, jint entries) {
    struct io_uring_params p;
    memset(&p, 0, sizeof(p));

    int ring_fd = sys_io_uring_setup((int)entries, &p);

    //Todo
    if (ring_fd < -1) {
      //throw Exception
      return NULL;
    }
    struct io_uring io_uring_ring;
    //Todo memset instead
    io_uring_ring.flags = 0;
    io_uring_ring.sq.sqe_tail = 0;
    io_uring_ring.sq.sqe_head = 0;
    setup_io_uring(ring_fd, &io_uring_ring, &p);

    jobject ioUringSubmissionQueue = (*env)->NewObject(
        env, ioUringSubmissionQueueClass, ioUringSubmissionQueueMethodId,
        (jlong)io_uring_ring.sq.khead, (jlong)io_uring_ring.sq.ktail,
        (jlong)io_uring_ring.sq.kring_mask,
        (jlong)io_uring_ring.sq.kring_entries, (jlong)io_uring_ring.sq.kflags,
        (jlong)io_uring_ring.sq.kdropped, (jlong)io_uring_ring.sq.array,
        (jlong)io_uring_ring.sq.sqes, (jlong)io_uring_ring.sq.ring_sz,
        (jlong)io_uring_ring.cq.ring_ptr, (jint)ring_fd);

    jobject ioUringCompletionQueue = (*env)->NewObject(
        env, ioUringCompletionQueueClass, ioUringCommpletionQueueMethodId,
        (jlong)io_uring_ring.cq.khead, (jlong)io_uring_ring.cq.ktail,
        (jlong)io_uring_ring.cq.kring_mask,
        (jlong)io_uring_ring.cq.kring_entries,
        (jlong)io_uring_ring.cq.koverflow, (jlong)io_uring_ring.cq.cqes,
        (jlong)io_uring_ring.cq.ring_sz, (jlong)io_uring_ring.cq.ring_ptr,
        (jint)ring_fd);

    jobject ringBuffer =
        (*env)->NewObject(env, ringBufferClass, ringBufferMethodId,
                        ioUringSubmissionQueue, ioUringCompletionQueue);

    return ringBuffer;
}

static jint netty_create_file(JNIEnv *env, jclass class) {
    return open("io-uring-test.txt", O_RDWR | O_TRUNC | O_CREAT, 0644);
}

static void netty_io_uring_native_JNI_OnUnLoad(JNIEnv *env) {
    // Todo OnUnLoad
}

// JNI Method Registration Table Begin
static const JNINativeMethod method_table[] = {
    {"ioUringSetup", "(I)Lio/netty/channel/uring/RingBuffer;", (void *)netty_io_uring_setup},
    {"createFile", "()I", (void *)netty_create_file},
    {"ioUringEnter", "(IIII)I", (void *)netty_io_uring_enter}};
static const jint method_table_size =
    sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    char *nettyClassName = NULL;

    if ((*vm)->GetEnv(vm, (void **)&env, NETTY_JNI_VERSION) != JNI_OK) {
        return JNI_ERR;
    }
    char *packagePrefix = NULL;

    Dl_info dlinfo;
    jint status = 0;

    if (!dladdr((void *)netty_io_uring_native_JNI_OnUnLoad, &dlinfo)) {
        fprintf(stderr,
            "FATAL: transport-native-epoll JNI call to dladdr failed!\n");
        return JNI_ERR;
    }
    packagePrefix = netty_unix_util_parse_package_prefix(
      dlinfo.dli_fname, "netty_transport_native_io_uring", &status);
    if (status == JNI_ERR) {
        fprintf(stderr,
            "FATAL: netty_transport_native_io_uring JNI encountered unexpected "
            "dlinfo.dli_fname: %s\n",
            dlinfo.dli_fname);
        return JNI_ERR;
    }

    if (netty_unix_util_register_natives(env, packagePrefix,
                                       "io/netty/channel/uring/Native",
                                       method_table, method_table_size) != 0) {
        printf("netty register natives error\n");
    }

    // Load all c modules that we depend upon
    if (netty_unix_limits_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }

    if (netty_unix_errors_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }

    if (netty_unix_filedescriptor_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }

    if (netty_unix_socket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }

    if (netty_unix_buffer_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }

    NETTY_PREPEND(packagePrefix, "io/netty/channel/uring/RingBuffer",
                nettyClassName, done);
    NETTY_LOAD_CLASS(env, ringBufferClass, nettyClassName, done);
    NETTY_GET_METHOD(env, ringBufferClass, ringBufferMethodId, "<init>",
                   "(Lio/netty/channel/uring/IOUringSubmissionQueue;Lio/netty/"
                   "channel/uring/IOUringCompletionQueue;)V",
                   done);

    NETTY_PREPEND(packagePrefix, "io/netty/channel/uring/IOUringSubmissionQueue",
                nettyClassName, done);
    NETTY_LOAD_CLASS(env, ioUringSubmissionQueueClass, nettyClassName, done);
    NETTY_GET_METHOD(env, ioUringSubmissionQueueClass,
                   ioUringSubmissionQueueMethodId, "<init>", "(JJJJJJJJIJI)V",
                   done);

    NETTY_PREPEND(packagePrefix, "io/netty/channel/uring/IOUringCompletionQueue",
                nettyClassName, done);
    NETTY_LOAD_CLASS(env, ioUringCompletionQueueClass, nettyClassName, done);
    NETTY_GET_METHOD(env, ioUringCompletionQueueClass,
                   ioUringCommpletionQueueMethodId, "<init>", "(JJJJJJIJI)V",
                   done);

    done:
    //unload

    return NETTY_JNI_VERSION;
}

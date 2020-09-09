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
#include "netty_io_uring_linuxsocket.h"
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
#include <sys/eventfd.h>
#include <poll.h>

static jmethodID ringBufferMethodId = NULL;
static jmethodID ioUringSubmissionQueueMethodId = NULL;
static jmethodID ioUringCommpletionQueueMethodId = NULL;
static jclass ringBufferClass = NULL;
static jclass ioUringCompletionQueueClass = NULL;
static jclass ioUringSubmissionQueueClass = NULL;

static void netty_io_uring_native_JNI_OnUnLoad(JNIEnv* env) {
    netty_unix_limits_JNI_OnUnLoad(env);
    netty_unix_errors_JNI_OnUnLoad(env);
    netty_unix_filedescriptor_JNI_OnUnLoad(env);
    netty_unix_socket_JNI_OnUnLoad(env);
    netty_unix_buffer_JNI_OnUnLoad(env);

    ringBufferMethodId = NULL;
    ioUringSubmissionQueueMethodId = NULL;
    ioUringCommpletionQueueMethodId = NULL;
    ringBufferClass = NULL;
    ioUringCompletionQueueClass = NULL;
    ioUringSubmissionQueueClass = NULL;
}

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
    int result;
    int err;
    do {
        result = sys_io_uring_enter(ring_fd, to_submit, min_complete, flags, NULL);
        if (result >= 0) {
            return result;
        }
    } while((err = errno) == EINTR);
    return -err;
}

static jint netty_epoll_native_eventFd(JNIEnv* env, jclass clazz) {
    jint eventFD = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);

    if (eventFD < 0) {
        netty_unix_errors_throwChannelExceptionErrorNo(env, "eventfd() failed: ", errno);
    }
    return eventFD;
}

static jint netty_io_uring_register_event_fd(JNIEnv *env, jclass class1, jint ring_fd, jint event_fd) {
    int ret;
    ret = sys_io_uring_register(ring_fd, IORING_REGISTER_EVENTFD,
					&event_fd, 1);
	if (ret < 0) {
		return -errno;
    }

	return 0;
}

static jint netty_io_uring_unregister_event_fd(JNIEnv *env, jclass class1, jint ring_fd) {
    int ret;

	ret = sys_io_uring_register(ring_fd, IORING_UNREGISTER_EVENTFD,
					NULL, 0);
	if (ret < 0) {
		return -errno;
    }

	return 0;
}


static void netty_io_uring_eventFdRead(JNIEnv* env, jclass clazz, jint fd) {
    uint64_t eventfd_t;

    if (eventfd_read(fd, &eventfd_t) != 0) {
        // something is serious wrong
        netty_unix_errors_throwRuntimeException(env, "eventfd_read() failed");
    }
}

static void netty_io_uring_eventFdWrite(JNIEnv* env, jclass clazz, jint fd, jlong value) {
    uint64_t val;

    for (;;) {
        jint ret = eventfd_write(fd, (eventfd_t) value);

        if (ret < 0) {
            // We need to read before we can write again, let's try to read and then write again and if this
            // fails we will bail out.
            //
            // See http://man7.org/linux/man-pages/man2/eventfd.2.html.
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

static void netty_io_uring_ring_buffer_exit(JNIEnv *env, jclass class, jobject ringBuffer) {
     // Find the id of the Java method to be called

    jclass ringBufferClass = (*env)->GetObjectClass(env, ringBuffer);
    jmethodID submissionQueueMethodId = (*env)->GetMethodID(env, ringBufferClass, "getIoUringSubmissionQueue", "()Lio/netty/channel/uring/IOUringSubmissionQueue;");
    jmethodID completionQueueMethodId = (*env)->GetMethodID(env, ringBufferClass, "getIoUringCompletionQueue", "()Lio/netty/channel/uring/IOUringCompletionQueue;");

    jobject submissionQueue = (*env)->CallObjectMethod(env, ringBuffer, submissionQueueMethodId);
    jobject completionQueue = (*env)->CallObjectMethod(env, ringBuffer, completionQueueMethodId);
    jclass submissionQueueClass = (*env)->GetObjectClass(env, submissionQueue);
    jclass completionQueueClass = (*env)->GetObjectClass(env, completionQueue);

    jmethodID submissionQueueArrayAddressMethodId = (*env)->GetMethodID(env, submissionQueueClass, "getSubmissionQueueArrayAddress", "()J");
    jmethodID submissionQueueKringEntriesAddressMethodId = (*env)->GetMethodID(env, submissionQueueClass, "getKRingEntriesAddress", "()J");
    jmethodID submissionQueueRingFdMethodId = (*env)->GetMethodID(env, submissionQueueClass, "getRingFd", "()I");
    jmethodID submissionQueueRingAddressMethodId = (*env)->GetMethodID(env, submissionQueueClass, "getRingAddress", "()J");
    jmethodID submissionQueueRingSizeMethodId = (*env)->GetMethodID(env, submissionQueueClass, "getRingSize", "()I");

    jmethodID completionQueueRingAddressMethodId = (*env)->GetMethodID(env, completionQueueClass, "getRingAddress", "()J");
    jmethodID completionQueueRingSizeMethodId = (*env)->GetMethodID(env, completionQueueClass, "getRingSize", "()I");

    jlong submissionQueueArrayAddress = (*env)->CallLongMethod(env, submissionQueue, submissionQueueArrayAddressMethodId);
    jlong submissionQueueKringEntriesAddress = (*env)->CallLongMethod(env, submissionQueue, submissionQueueKringEntriesAddressMethodId);
    jint submissionQueueRingFd = (*env)->CallIntMethod(env, submissionQueue, submissionQueueRingFdMethodId);
    jlong submissionQueueRingAddress = (*env)->CallLongMethod(env, submissionQueue, submissionQueueRingAddressMethodId);
    jint submissionQueueRingSize = (*env)->CallIntMethod(env, submissionQueue, submissionQueueRingSizeMethodId);

    jlong completionQueueRingAddress = (*env)->CallLongMethod(env, completionQueue, completionQueueRingAddressMethodId);
    jint completionQueueRingSize = (*env)->CallIntMethod(env, completionQueue, completionQueueRingSizeMethodId);

    unsigned submissionQueueKringEntries = *((unsigned int *) submissionQueueKringEntriesAddress);

    munmap((struct io_uring_sqe*) submissionQueueArrayAddress, submissionQueueKringEntries * sizeof(struct io_uring_sqe));
    munmap((void*) submissionQueueRingAddress, submissionQueueRingSize);
	if (((void *) completionQueueRingAddress) && ((void *) completionQueueRingAddress) != ((void *) submissionQueueRingAddress)) {
		munmap((void *)completionQueueRingAddress, completionQueueRingSize);
	}
	close(submissionQueueRingFd);
}

static int nettyBlockingSocket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

static jobject netty_io_uring_setup(JNIEnv *env, jclass class1, jint entries) {
    struct io_uring_params p;
    memset(&p, 0, sizeof(p));

    int ring_fd = sys_io_uring_setup((int)entries, &p);

    //Todo
    if (ring_fd < 0) {
      printf("RingFd error: %d\n", ring_fd);
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

static jint netty_io_uring_sockNonblock(JNIEnv* env, jclass clazz) {
    return SOCK_NONBLOCK;
}

static jint netty_io_uring_sockCloexec(JNIEnv* env, jclass clazz) {
    return SOCK_CLOEXEC;
}

static jint netty_io_uring_etime(JNIEnv* env, jclass clazz) {
    return ETIME;
}

static jint netty_io_uring_ecanceled(JNIEnv* env, jclass clazz) {
    return ECANCELED;
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

static jint netty_io_uring_ioringOpWritev(JNIEnv* env, jclass clazz) {
    return IORING_OP_WRITEV;
}

static jint netty_io_uring_ioringOpPollAdd(JNIEnv* env, jclass clazz) {
    return IORING_OP_POLL_ADD;
}

static jint netty_io_uring_ioringOpPollRemove(JNIEnv* env, jclass clazz) {
    return IORING_OP_POLL_REMOVE;
}

static jint netty_io_uring_ioringOpTimeout(JNIEnv* env, jclass clazz) {
    return IORING_OP_TIMEOUT;
}

static jint netty_io_uring_ioringOpAccept(JNIEnv* env, jclass clazz) {
    return IORING_OP_ACCEPT;
}

static jint netty_io_uring_ioringOpRead(JNIEnv* env, jclass clazz) {
    return IORING_OP_READ;
}

static jint netty_io_uring_ioringOpWrite(JNIEnv* env, jclass clazz) {
    return IORING_OP_WRITE;
}

static jint netty_io_uring_ioringOpConnect(JNIEnv* env, jclass clazz) {
    return IORING_OP_CONNECT;
}

static jint netty_io_uring_ioringOpClose(JNIEnv* env, jclass clazz) {
    return IORING_OP_CLOSE;
}

static jint netty_io_uring_ioringEnterGetevents(JNIEnv* env, jclass clazz) {
    return IORING_ENTER_GETEVENTS;
}

static jint netty_io_uring_iosqeAsync(JNIEnv* env, jclass clazz) {
    return IOSQE_ASYNC;
}

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "sockNonblock", "()I", (void *) netty_io_uring_sockNonblock },
  { "sockCloexec", "()I", (void *) netty_io_uring_sockCloexec },
  { "etime", "()I", (void *) netty_io_uring_etime },
  { "ecanceled", "()I", (void *) netty_io_uring_ecanceled },
  { "pollin", "()I", (void *) netty_io_uring_pollin },
  { "pollout", "()I", (void *) netty_io_uring_pollout },
  { "pollrdhup", "()I", (void *) netty_io_uring_pollrdhup },
  { "ioringOpWritev", "()I", (void *) netty_io_uring_ioringOpWritev },
  { "ioringOpPollAdd", "()I", (void *) netty_io_uring_ioringOpPollAdd },
  { "ioringOpPollRemove", "()I", (void *) netty_io_uring_ioringOpPollRemove },
  { "ioringOpTimeout", "()I", (void *) netty_io_uring_ioringOpTimeout },
  { "ioringOpAccept", "()I", (void *) netty_io_uring_ioringOpAccept },
  { "ioringOpRead", "()I", (void *) netty_io_uring_ioringOpRead },
  { "ioringOpWrite", "()I", (void *) netty_io_uring_ioringOpWrite },
  { "ioringOpConnect", "()I", (void *) netty_io_uring_ioringOpConnect },
  { "ioringOpClose", "()I", (void *) netty_io_uring_ioringOpClose },
  { "ioringEnterGetevents", "()I", (void *) netty_io_uring_ioringEnterGetevents },
  { "iosqeAsync", "()I", (void *) netty_io_uring_iosqeAsync }

};
static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);

static const JNINativeMethod method_table[] = {
    {"ioUringSetup", "(I)Lio/netty/channel/uring/RingBuffer;", (void *) netty_io_uring_setup},
    {"ioUringExit", "(Lio/netty/channel/uring/RingBuffer;)V", (void *) netty_io_uring_ring_buffer_exit},
    {"createFile", "()I", (void *) netty_create_file},
    {"ioUringEnter", "(IIII)I", (void *)netty_io_uring_enter},
    {"eventFd", "()I", (void *) netty_epoll_native_eventFd},
    {"eventFdWrite", "(IJ)V", (void *) netty_io_uring_eventFdWrite },
    {"eventFdRead", "(I)V", (void *) netty_io_uring_eventFdRead },
    {"ioUringRegisterEventFd", "(II)I", (void *) netty_io_uring_register_event_fd},
    {"ioUringUnregisterEventFd", "(I)I", (void *) netty_io_uring_unregister_event_fd}
    };
static const jint method_table_size =
    sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    int ret = JNI_ERR;
    int limitsOnLoadCalled = 0;
    int errorsOnLoadCalled = 0;
    int filedescriptorOnLoadCalled = 0;
    int socketOnLoadCalled = 0;
    int bufferOnLoadCalled = 0;
    int linuxsocketOnLoadCalled = 0;
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

    // We must register the statically referenced methods first!
    if (netty_unix_util_register_natives(env,
            packagePrefix,
            "io/netty/channel/uring/NativeStaticallyReferencedJniMethods",
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
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
    limitsOnLoadCalled = 1;

    if (netty_unix_errors_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    errorsOnLoadCalled = 1;

    if (netty_unix_filedescriptor_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    filedescriptorOnLoadCalled = 1;

    if (netty_unix_socket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    socketOnLoadCalled = 1;

    if (netty_unix_buffer_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    bufferOnLoadCalled = 1;

    if (netty_io_uring_linuxsocket_JNI_OnLoad(env, packagePrefix) == JNI_ERR) {
        goto done;
    }
    linuxsocketOnLoadCalled = 1;

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
    ret = NETTY_JNI_VERSION;
done:
    //unload
    free(nettyClassName);

    if (ret == JNI_ERR) {
        if (limitsOnLoadCalled == 1) {
            netty_unix_limits_JNI_OnUnLoad(env);
        }
        if (errorsOnLoadCalled == 1) {
            netty_unix_errors_JNI_OnUnLoad(env);
        }
        if (filedescriptorOnLoadCalled == 1) {
            netty_unix_filedescriptor_JNI_OnUnLoad(env);
        }
        if (socketOnLoadCalled == 1) {
            netty_unix_socket_JNI_OnUnLoad(env);
        }
        if (bufferOnLoadCalled == 1) {
            netty_unix_buffer_JNI_OnUnLoad(env);
        }
        if (linuxsocketOnLoadCalled == 1) {
            netty_io_uring_linuxsocket_JNI_OnUnLoad(env);
        }

        ringBufferMethodId = NULL;
        ioUringSubmissionQueueMethodId = NULL;
        ioUringCommpletionQueueMethodId = NULL;
        ringBufferClass = NULL;
        ioUringCompletionQueueClass = NULL;
        ioUringSubmissionQueueClass = NULL;

    }
    return ret;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    // Todo OnUnLoad
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**) &env, NETTY_JNI_VERSION) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    }
    netty_io_uring_native_JNI_OnUnLoad(env);
}
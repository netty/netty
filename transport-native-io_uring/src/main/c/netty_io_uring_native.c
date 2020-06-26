#define _GNU_SOURCE // RTLD_DEFAULT
#include "io_uring.h"
#include "netty_unix_errors.h"
#include "netty_unix_filedescriptor.h"
#include "netty_unix_jni.h"
#include "netty_unix_socket.h"
#include "netty_unix_util.h"
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

// From netty jni unix socket
static jsize addressLength(const struct sockaddr_storage *addr) {
  int len = netty_unix_socket_ipAddressLength(addr);
  if (len == 4) {
    // Only encode port into it
    return len + 4;
  }
  // we encode port + scope into it
  return len + 8;
}

/*
 * Sync internal state with kernel ring state on the SQ side. Returns the
 * number of pending items in the SQ ring, for the shared ring.
 */
int io_uring_flush_sq(struct io_uring *ring) {
  struct io_uring_sq *sq = &ring->sq;
  const unsigned mask = *sq->kring_mask;
  unsigned ktail, to_submit;

  if (sq->sqe_head == sq->sqe_tail) {
    ktail = *sq->ktail;
    goto out;
  }

  /*
   * Fill in sqes that we have queued up, adding them to the kernel ring
   */
  ktail = *sq->ktail;
  to_submit = sq->sqe_tail - sq->sqe_head;
  while (to_submit--) {
    sq->array[ktail & mask] = sq->sqe_head & mask;
    ktail++;
    sq->sqe_head++;
  }

  /*
   * Ensure that the kernel sees the SQE updates before it sees the tail
   * update.
   */
  io_uring_smp_store_release(sq->ktail, ktail);
out:
  return ktail - *sq->khead;
}

// From netty unix socket jni
static void initInetSocketAddressArray(JNIEnv *env,
                                       const struct sockaddr_storage *addr,
                                       jbyteArray bArray, int offset,
                                       jsize len) {
  int port;
  if (addr->ss_family == AF_INET) {
    struct sockaddr_in *s = (struct sockaddr_in *)addr;
    port = ntohs(s->sin_port);

    // Encode address and port into the array
    unsigned char a[4];
    a[0] = port >> 24;
    a[1] = port >> 16;
    a[2] = port >> 8;
    a[3] = port;
    (*env)->SetByteArrayRegion(env, bArray, offset, 4,
                               (jbyte *)&s->sin_addr.s_addr);
    (*env)->SetByteArrayRegion(env, bArray, offset + 4, 4, (jbyte *)&a);
  } else {
    struct sockaddr_in6 *s = (struct sockaddr_in6 *)addr;
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
      (*env)->SetByteArrayRegion(env, bArray, offset, 4,
                                 (jbyte *)&(s->sin6_addr.s6_addr[12]));
      (*env)->SetByteArrayRegion(env, bArray, offset + 4, 4, (jbyte *)&a);
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

      (*env)->SetByteArrayRegion(env, bArray, offset, 16,
                                 (jbyte *)&(s->sin6_addr.s6_addr));
      (*env)->SetByteArrayRegion(env, bArray, offset + 16, 8, (jbyte *)&a);
    }
  }
}

static struct io_uring_sqe *__io_uring_get_sqe(struct io_uring_sq *sq,
                                               unsigned int __head) {
  unsigned int __next = (sq)->sqe_tail + 1;
  struct io_uring_sqe *__sqe = NULL;

  if (__next - __head <= *(sq)->kring_entries) {
    __sqe = &(sq)->sqes[(sq)->sqe_tail & *(sq)->kring_mask];

    if (!__sqe) {
      printf("SQE is null \n");
    }
    (sq)->sqe_tail = __next;
  }
  return __sqe;
}

struct io_uring_sqe *io_uring_get_sqe(struct io_uring *ring) {
  struct io_uring_sq *sq = &ring->sq;
  return __io_uring_get_sqe(sq, sq->sqe_head);
}

static inline void io_uring_prep_rw(int op, struct io_uring_sqe *sqe, int fd,
                                    const void *addr, unsigned len,
                                    __u64 offset) {
  sqe->opcode = op;
  sqe->flags = 0;
  sqe->ioprio = 0;
  sqe->fd = fd;
  sqe->off = offset;
  sqe->addr = (unsigned long)addr;
  sqe->len = len;
  sqe->rw_flags = 0;
  sqe->user_data = 0;
  sqe->__pad2[0] = sqe->__pad2[1] = sqe->__pad2[2] = 0;
}

void io_uring_unmap_rings(struct io_uring_sq *sq, struct io_uring_cq *cq) {
  munmap(sq->ring_ptr, sq->ring_sz);
  if (cq->ring_ptr && cq->ring_ptr != sq->ring_ptr)
    munmap(cq->ring_ptr, cq->ring_sz);
}

int io_uring_mmap(int fd, struct io_uring_params *p, struct io_uring_sq *sq,
                  struct io_uring_cq *cq) {
  size_t size;
  int ret;

  sq->ring_sz = p->sq_off.array + p->sq_entries * sizeof(unsigned);
  cq->ring_sz = p->cq_off.cqes + p->cq_entries * sizeof(struct io_uring_cqe);

  if (p->features & IORING_FEAT_SINGLE_MMAP) {
    if (cq->ring_sz > sq->ring_sz)
      sq->ring_sz = cq->ring_sz;
    cq->ring_sz = sq->ring_sz;
  }
  sq->ring_ptr = mmap(0, sq->ring_sz, PROT_READ | PROT_WRITE,
                      MAP_SHARED | MAP_POPULATE, fd, IORING_OFF_SQ_RING);
  if (sq->ring_ptr == MAP_FAILED)
    return -errno;

  if (p->features & IORING_FEAT_SINGLE_MMAP) {
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
  sq->sqes = mmap(0, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE,
                  fd, IORING_OFF_SQES);
  if (sq->sqes == MAP_FAILED) {
    ret = -errno;
  err:
    io_uring_unmap_rings(sq, cq);
    return ret;
  }

  cq->khead = cq->ring_ptr + p->cq_off.head;
  cq->ktail = cq->ring_ptr + p->cq_off.tail;
  cq->kring_mask = cq->ring_ptr + p->cq_off.ring_mask;
  cq->kring_entries = cq->ring_ptr + p->cq_off.ring_entries;
  cq->koverflow = cq->ring_ptr + p->cq_off.overflow;
  cq->cqes = cq->ring_ptr + p->cq_off.cqes;
  return 0;
}

void setup_io_uring(int ring_fd, struct io_uring *io_uring_ring,
                    struct io_uring_params *p) {
  int ret;

  ret = io_uring_mmap(ring_fd, p, &io_uring_ring->sq, &io_uring_ring->cq);
  if (!ret) {
    io_uring_ring->flags = p->flags;
    io_uring_ring->ring_fd = ring_fd;
  } else {
    perror("setup_io_uring error \n");
  }
}

void io_uring_prep_write(struct io_uring_sqe *sqe, int fd, const void *buf,
                         unsigned nbytes, off_t offset) {
  io_uring_prep_rw(IORING_OP_WRITE, sqe, fd, buf, nbytes, offset);
}

void io_uring_prep_read(struct io_uring_sqe *sqe, int fd, void *buf,
                        unsigned nbytes, off_t offset) {
  io_uring_prep_rw(IORING_OP_READ, sqe, fd, buf, nbytes, offset);
}

void io_uring_sqe_set_data(struct io_uring_sqe *sqe, unsigned long data) {
  sqe->user_data = (unsigned long)data;
}

void queue_read(int file_fd, struct io_uring *ring, void *buffer, jint event_id,
                jint pos, jint limit) {
  struct io_uring_sqe *sqe = NULL;

  sqe = io_uring_get_sqe(ring);
  if (!sqe) {
    fprintf(stderr, "Could not get SQE.\n");
    return;
  }
  io_uring_prep_read(sqe, file_fd, buffer + pos, (size_t)(limit - pos), 0);
  io_uring_sqe_set_data(sqe, (int)event_id);
}

void queue_write(int file_fd, struct io_uring *ring, void *buffer,
                 jint event_id, jint pos, jint limit) {
  struct io_uring_sqe *sqe;

  sqe = io_uring_get_sqe(ring);
  if (!sqe) {
    fprintf(stderr, "Could not get SQE.\n");
    return;
  }
  io_uring_prep_write(sqe, file_fd, buffer + pos, (size_t)(limit - pos), 0);
  io_uring_sqe_set_data(sqe, (unsigned long)event_id);
}

int __io_uring_peek_cqe(struct io_uring *ring, struct io_uring_cqe **cqe_ptr) {
  struct io_uring_cqe *cqe;
  unsigned head;
  int err = 0;

  do {
    io_uring_for_each_cqe(ring, head, cqe) break;
    break;
  } while (1);

  *cqe_ptr = cqe;
  return err;
}

long io_uring_wait_cqe(struct io_uring *ring, unsigned wait_nr) {
  struct io_uring_cqe *cqe = NULL;
  int ret = 0, err;
  unsigned flags = 0;

  err = __io_uring_peek_cqe(ring, &cqe);
  if (err) {
    printf("error peek \n");
    return -errno;
  }

  if (cqe) {
    return (long)cqe;
  }

  flags = IORING_ENTER_GETEVENTS;
  ret = sys_io_uring_enter(ring->ring_fd, 0, wait_nr, flags, NULL);

  if (ret < 0) {
    return -1;
  } else if (ret == 0) {

    err = __io_uring_peek_cqe(ring, &cqe);
    if (err) {
      printf("error peek \n");
      return -1;
    }
    if (cqe) {
      return (long)cqe;
    }
  }
  return -1;
}

/*
 * Submit sqes acquired from io_uring_get_sqe() to the kernel.
 *
 * Returns number of sqes submitted
 */
int io_uring_submit(struct io_uring *ring) {
  int submitted = io_uring_flush_sq(ring);
  int ret;

  ret = sys_io_uring_enter(ring->ring_fd, submitted, 0, 0, NULL);
  if (ret < 0)
    return -errno;
  return ret;
}

// all jni methods

static jlong netty_io_uring_setup(JNIEnv *env, jclass class1, jint entries) {
  struct io_uring_params p;
  memset(&p, 0, sizeof(p));

  int ring_fd = sys_io_uring_setup((int)entries, &p);

  struct io_uring *io_uring_ring =
      (struct io_uring *)malloc(sizeof(struct io_uring));
  io_uring_ring->flags = 0;
  io_uring_ring->sq.sqe_tail = 0;
  io_uring_ring->sq.sqe_head = 0;
  setup_io_uring(ring_fd, io_uring_ring, &p);

  return (long)io_uring_ring;
}

static jint netty_read_operation(JNIEnv *jenv, jclass clazz, jlong uring,
                                 jlong fd, jlong event_id, jlong buffer_address,
                                 jint pos, jint limit) {

  queue_read((int)fd, (struct io_uring *)uring, (void *)buffer_address,
             event_id, pos, limit);
  return 0;
}

static jint netty_write_operation(JNIEnv *jenv, jclass clazz, jlong uring,
                                  jlong fd, jlong event_id,
                                  jlong buffer_address, jint pos, jint limit) {
  queue_write((int)fd, (struct io_uring *)uring, (void *)buffer_address,
              event_id, pos, limit);
  return 0;
}

static jint netty_accept_operation(JNIEnv *env, jclass clazz, jlong uring,
                                   jlong fd, jbyteArray byte_array) {

  jint socketFd;
  jsize len;
  jbyte len_b;
  int err;
  struct sockaddr_storage addr;
  socklen_t address_len = sizeof(addr);

  socketFd = accept(fd, (struct sockaddr *)&addr, &address_len);

  if ((err = errno) != EINTR) {
    return -err;
  }

  len = addressLength(&addr);
  len_b = (jbyte)len;

  // Fill in remote address details
  (*env)->SetByteArrayRegion(env, byte_array, 0, 1, (jbyte *)&len_b);
  initInetSocketAddressArray(env, &addr, byte_array, 1, len);

  return socketFd;
}

static jlong netty_wait_cqe(JNIEnv *env, jclass clazz, jlong uring) {
  return (jlong)io_uring_wait_cqe((struct io_uring *)uring, 1);
}

static jlong netty_delete_cqe(JNIEnv *env, jclass clazz, jlong uring,
                              jlong cqe_address) {
  struct io_uring_cqe *cqe = (struct io_uring_cqe *)cqe_address;
  io_uring_cqe_seen((struct io_uring *)uring, cqe);
  return 0;
}

static jlong netty_get_event_id(JNIEnv *env, jclass classz, jlong cqe_address) {
  struct io_uring_cqe *cqe = (struct io_uring_cqe *)cqe_address;
  return (long)cqe->user_data;
}

static jint netty_get_res(JNIEnv *env, jclass classz, jlong cqe_address) {
  struct io_uring_cqe *cqe = (struct io_uring_cqe *)cqe_address;
  return (long)cqe->res;
}

static jlong netty_close(JNIEnv *env, jclass classz, jlong io_uring) {

  struct io_uring *ring = (struct io_uring *)io_uring;
  struct io_uring_sq *sq = &ring->sq;
  struct io_uring_cq *cq = &ring->cq;

  munmap(sq->sqes, *sq->kring_entries * sizeof(struct io_uring_sqe));
  io_uring_unmap_rings(sq, cq);
  close(ring->ring_fd);
}

static jlong netty_submit(JNIEnv *jenv, jclass classz, jlong uring) {
  return io_uring_submit((struct io_uring *)uring);
}

static jlong netty_create_file(JNIEnv *env, jclass class) {
  return open("io-uring-test.txt", O_RDWR | O_TRUNC | O_CREAT, 0644);
}

// end jni methods

static void netty_io_uring_native_JNI_OnUnLoad(JNIEnv *env) {
  // OnUnLoad
}

// JNI Registered Methods Begin
static jint netty_io_uring_close(JNIEnv *env, jclass clazz, jint fd) {
  return 111;
}

// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod method_table[] = {
    {"ioUringSetup", "(I)J", (void *)netty_io_uring_setup},
    {"ioUringClose", "(J)J", (void *)netty_io_uring_close},
    {"ioUringRead", "(JJJJII)I", (void *)netty_read_operation},
    {"ioUringWrite", "(JJJJII)I", (void *)netty_write_operation},
    {"ioUringAccept", "(JJ[B)I", (void *)netty_accept_operation},
    {"ioUringWaitCqe", "(J)J", (void *)netty_wait_cqe},
    {"ioUringDeleteCqe", "(JJ)J", (void *)netty_delete_cqe},
    {"ioUringGetEventId", "(J)J", (void *)netty_get_event_id},
    {"ioUringGetRes", "(J)I", (void *)netty_get_res},
    {"ioUringSubmit", "(J)J", (void *)netty_submit},
    {"createFile", "()J", (void *)netty_create_file}};
static const jint method_table_size =
    sizeof(method_table) / sizeof(method_table[0]);
// JNI Method Registration Table End

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
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
  return NETTY_JNI_VERSION;
}

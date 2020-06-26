/* SPDX-License-Identifier: MIT */
#include "barrier.h"
#include <linux/io_uring.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#ifndef LIB_TEST
#define LIB_TEST

struct io_uring_sq {
  unsigned *khead;
  unsigned *ktail;
  unsigned *kring_mask;
  unsigned *kring_entries;
  unsigned *kflags;
  unsigned *kdropped;
  unsigned *array;
  struct io_uring_sqe *sqes;

  unsigned sqe_head;
  unsigned sqe_tail;

  size_t ring_sz;
  void *ring_ptr;
};

struct io_uring_cq {
  unsigned *khead;
  unsigned *ktail;
  unsigned *kring_mask;
  unsigned *kring_entries;
  unsigned *koverflow;
  struct io_uring_cqe *cqes;

  size_t ring_sz;
  void *ring_ptr;
};

struct io_uring {
  struct io_uring_sq sq;
  struct io_uring_cq cq;
  unsigned flags;
  int ring_fd;
};

#define io_uring_for_each_cqe(ring, head, cqe)                                 \
  /*                                                                           \
   * io_uring_smp_load_acquire() enforces the order of tail                    \
   * and CQE reads.                                                            \
   */                                                                          \
  for (head = *(ring)->cq.khead;                                               \
       (cqe = (head != io_uring_smp_load_acquire((ring)->cq.ktail)             \
                   ? &(ring)->cq.cqes[head & (*(ring)->cq.kring_mask)]         \
                   : NULL));                                                   \
       head++)

/*
 * Must be called after io_uring_for_each_cqe()
 */
static inline void io_uring_cq_advance(struct io_uring *ring, unsigned nr) {
  if (nr) {
    struct io_uring_cq *cq = &ring->cq;

    /*
     * Ensure that the kernel only sees the new value of the head
     * index after the CQEs have been read.
     */
    io_uring_smp_store_release(cq->khead, *cq->khead + nr);
  }
}

/*
 * Must be called after io_uring_{peek,wait}_cqe() after the cqe has
 * been processed by the application.
 */
static void io_uring_cqe_seen(struct io_uring *ring, struct io_uring_cqe *cqe) {
  if (cqe)
    io_uring_cq_advance(ring, 1);
}

#endif
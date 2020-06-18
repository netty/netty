/* SPDX-License-Identifier: MIT */
#include "barrier.h"
#include <linux/io_uring.h>
#include <stdio.h>
#include <stddef.h>
#include <stdint.h>

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

#endif
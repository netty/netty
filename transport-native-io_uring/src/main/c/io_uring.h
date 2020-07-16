/* SPDX-License-Identifier: MIT */
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

#endif
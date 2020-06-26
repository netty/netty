/* SPDX-License-Identifier: MIT */
#ifndef LIBURING_BARRIER_H
#define LIBURING_BARRIER_H

#include <stdatomic.h>

/*
From the kernel documentation file refcount-vs-atomic.rst:
A RELEASE memory ordering guarantees that all prior loads and
stores (all po-earlier instructions) on the same CPU are completed
before the operation. It also guarantees that all po-earlier
stores on the same CPU and all propagated stores from other CPUs
must propagate to all other CPUs before the release operation
(A-cumulative property). This is implemented using
:c:func:`smp_store_release`.
An ACQUIRE memory ordering guarantees that all post loads and
stores (all po-later instructions) on the same CPU are
completed after the acquire operation. It also guarantees that all
po-later stores on the same CPU must propagate to all other CPUs
after the acquire operation executes. This is implemented using
:c:func:`smp_acquire__after_ctrl_dep`.
*/

#define IO_URING_WRITE_ONCE(var, val)                                          \
  atomic_store_explicit(&(var), (val), memory_order_relaxed)
#define IO_URING_READ_ONCE(var)                                                \
  atomic_load_explicit(&(var), memory_order_relaxed)

#define io_uring_smp_store_release(p, v)                                       \
  atomic_store_explicit((p), (v), memory_order_release)
#define io_uring_smp_load_acquire(p)                                           \
  atomic_load_explicit((p), memory_order_acquire)

#endif /* defined(LIBURING_BARRIER_H) */
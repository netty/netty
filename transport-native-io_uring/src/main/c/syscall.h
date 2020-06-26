/* SPDX-License-Identifier: MIT */
#include <linux/io_uring.h>
#include <signal.h>
#ifndef LIBURING_SYSCALL_H
#define LIBURING_SYSCALL_H

/*
 * System calls
 */
// extern int sys_io_uring_setup(unsigned entries, struct io_uring_params *p);
extern int sys_io_uring_setup(unsigned entries, struct io_uring_params *p);
extern int sys_io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                              unsigned flags, sigset_t *sig);
extern int sys_io_uring_register(int fd, unsigned int opcode, const void *arg,
                                 unsigned int nr_args);

#endif
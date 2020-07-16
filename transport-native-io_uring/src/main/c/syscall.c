/* SPDX-License-Identifier: MIT */
/*
 * Will go away once libc support is there
 */
#include "syscall.h"
#include <signal.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

int sys_io_uring_register(int fd, unsigned opcode, const void *arg,
                          unsigned nr_args) {
  return syscall(__NR_io_uring_register, fd, opcode, arg, nr_args);
}

int sys_io_uring_setup(unsigned entries, struct io_uring_params *p) {
  return syscall(__NR_io_uring_setup, entries, p);
}

int sys_io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                       unsigned flags, sigset_t *sig) {
  return syscall(__NR_io_uring_enter, fd, to_submit, min_complete, flags, sig,
                 _NSIG / 8);
}
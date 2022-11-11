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
  /* SPDX-License-Identifier: MIT */
#include "syscall.h"
#include <signal.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

// Define syscall numbers if not exists as these are "stable".
#ifndef __NR_io_uring_setup
#define __NR_io_uring_setup		425
#endif
#ifndef __NR_io_uring_enter
#define __NR_io_uring_enter		426
#endif
#ifndef __NR_io_uring_register
#define __NR_io_uring_register	427
#endif

int sys_io_uring_setup(unsigned entries, struct io_uring_params *p) {
    return syscall(__NR_io_uring_setup, entries, p);
}

int sys_io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                       unsigned flags, sigset_t *sig) {
    return syscall(__NR_io_uring_enter, fd, to_submit, min_complete, flags, sig,
                 _NSIG / 8);
}

int sys_io_uring_register(int fd, unsigned opcode, const void *arg, unsigned nr_args) {
    return syscall(__NR_io_uring_register, fd, opcode, arg, nr_args);
}

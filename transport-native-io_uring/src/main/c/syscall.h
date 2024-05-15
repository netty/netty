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
#include <signal.h>
#include "io_uring.h"
#ifndef LIBURING_SYSCALL_H
#define LIBURING_SYSCALL_H

/*
 * System calls
 */
extern int sys_io_uring_setup(unsigned entries, struct io_uring_params *p);
extern int sys_io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                              unsigned flags, sigset_t *sig);
extern int sys_io_uring_register(int fd, unsigned int opcode, const void *arg, unsigned int nr_args);
#endif

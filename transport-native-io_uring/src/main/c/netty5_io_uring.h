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
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include "io_uring.h"
#include "netty5_unix_buffer.h"
#include "netty5_unix_errors.h"
#include "netty5_unix_filedescriptor.h"
#include "netty5_unix_jni.h"
#include "netty5_unix_limits.h"
#include "netty5_unix_socket.h"
#include "netty5_unix_util.h"
#include "netty5_unix.h"

#ifndef NETTY5_IO_URING
#define NETTY5_IO_URING

struct io_uring_sq {
    unsigned *khead;
    unsigned *ktail;
    unsigned *kring_mask;
    unsigned *kring_entries;
    unsigned *kflags;
    unsigned *kdropped;
    unsigned *array;
    struct io_uring_sqe *sqes;

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

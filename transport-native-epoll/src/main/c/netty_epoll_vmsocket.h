/*
 * Copyright 2023 The Netty Project
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

#ifndef NETTY_EPOLL_VMSOCKET_H_
#define NETTY_EPOLL_VMSOCKET_H_

#include <sys/socket.h>

#ifndef AF_VSOCK
#define AF_VSOCK 40
#endif

struct sockaddr_vm {
    sa_family_t svm_family;
    unsigned short svm_reserved1;
    unsigned int svm_port;
    unsigned int svm_cid;
    unsigned char svm_zero[sizeof(struct sockaddr) -
                           sizeof(sa_family_t) -
                           sizeof(unsigned short) -
                           sizeof(unsigned int) -
                           sizeof(unsigned int)];
};

#endif /* NETTY_EPOLL_VMSOCKET_H_ */
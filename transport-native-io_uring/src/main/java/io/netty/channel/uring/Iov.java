/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.uring;

import io.netty.util.internal.PlatformDependent;

/**
 * struct iovec {
 *     void  *iov_base;    // Starting address
 *     size_t iov_len;     // Number of bytes to transfer
 * };
 */
final class Iov {

    private Iov() { }

    static void write(long iovAddress, long bufferAddress, int length) {
        if (Native.SIZEOF_SIZE_T == 4) {
            PlatformDependent.putInt(iovAddress + Native.IOVEC_OFFSETOF_IOV_BASE, (int) bufferAddress);
            PlatformDependent.putInt(iovAddress + Native.IOVEC_OFFSETOF_IOV_LEN, length);
        } else {
            assert Native.SIZEOF_SIZE_T == 8;
            PlatformDependent.putLong(iovAddress + Native.IOVEC_OFFSETOF_IOV_BASE, bufferAddress);
            PlatformDependent.putLong(iovAddress + Native.IOVEC_OFFSETOF_IOV_LEN, length);
        }
    }
}

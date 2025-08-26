/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import java.nio.ByteBuffer;

/**
 * <pre>{@code
 * struct iovec {
 *     void  *iov_base;    // Starting address
 *     size_t iov_len;     // Number of bytes to transfer
 * };
 * }</pre>
 */
final class Iov {

    private Iov() { }

    static void set(ByteBuffer buffer, long bufferAddress, int length) {
        int position = buffer.position();
        if (Native.SIZEOF_SIZE_T == 4) {
            buffer.putInt(position + Native.IOVEC_OFFSETOF_IOV_BASE, (int) bufferAddress);
            buffer.putInt(position + Native.IOVEC_OFFSETOF_IOV_LEN, length);
        } else {
            assert Native.SIZEOF_SIZE_T == 8;
            buffer.putLong(position + Native.IOVEC_OFFSETOF_IOV_BASE, bufferAddress);
            buffer.putLong(position + Native.IOVEC_OFFSETOF_IOV_LEN, length);
        }
    }

    static long getBufferAddress(ByteBuffer iov) {
        if (Native.SIZEOF_SIZE_T == 4) {
            return iov.getInt(iov.position() + Native.IOVEC_OFFSETOF_IOV_BASE);
        }
        assert Native.SIZEOF_SIZE_T == 8;
        return iov.getLong(iov.position() + Native.IOVEC_OFFSETOF_IOV_BASE);
    }

    static int getBufferLength(ByteBuffer iov) {
        if (Native.SIZEOF_SIZE_T == 4) {
            return iov.getInt(iov.position() + Native.IOVEC_OFFSETOF_IOV_LEN);
        }
        assert Native.SIZEOF_SIZE_T == 8;
        return (int) iov.getLong(iov.position() + Native.IOVEC_OFFSETOF_IOV_LEN);
    }
}

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

import io.netty.channel.unix.Buffer;

import java.nio.ByteBuffer;

/**
 * <pre>{@code
 * struct msghdr {
 *     void         *msg_name;       // optional address
 *     socklen_t    msg_namelen;     // size of address
 *     struct       iovec*msg_iov;   // scatter/gather array
 *     size_t       msg_iovlen;      // # elements in msg_iov
 *     void*        msg_control;     // ancillary data, see below
 *     size_t       msg_controllen;  // ancillary data buffer len
 *     int          msg_flags;       // flags on received message
 * };
 * }</pre>
 */
final class MsgHdr {

    private MsgHdr() { }

    static void set(ByteBuffer memory, ByteBuffer sockAddrMemory, int addressSize, ByteBuffer iovMemory, int iovLength,
                    ByteBuffer msgControl, int cmsgHdrDataOffset, short segmentSize) {
        int memoryPosition = memory.position();
        memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_NAMELEN, addressSize);

        int msgControlLen = 0;
        long msgControlAddr;
        if (segmentSize > 0) {
            msgControlLen = Native.CMSG_LEN;
            CmsgHdr.write(msgControl, cmsgHdrDataOffset, Native.CMSG_LEN, Native.SOL_UDP,
                    Native.UDP_SEGMENT, segmentSize);
            msgControlAddr = Buffer.memoryAddress(msgControl) + msgControl.position();
        } else {
            // Set to 0 if we not explicit requested GSO.
            msgControlAddr = 0;
        }
        if (Native.SIZEOF_SIZE_T == 4) {
            memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_NAME, (int) Buffer.memoryAddress(sockAddrMemory));
            memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_IOV, (int) Buffer.memoryAddress(iovMemory));
            memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_IOVLEN, iovLength);
            memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_CONTROL, (int) msgControlAddr);
            memory.putInt(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_CONTROLLEN, msgControlLen);
        } else {
            assert Native.SIZEOF_SIZE_T == 8;
            memory.putLong(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_NAME, Buffer.memoryAddress(sockAddrMemory));
            memory.putLong(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_IOV, Buffer.memoryAddress(iovMemory));
            memory.putLong(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_IOVLEN, iovLength);
            memory.putLong(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_CONTROL, msgControlAddr);
            memory.putLong(memoryPosition + Native.MSGHDR_OFFSETOF_MSG_CONTROLLEN, msgControlLen);
        }
        // No flags (we assume the memory was memset before)
    }
}

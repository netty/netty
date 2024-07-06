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
package io.netty5.channel.uring;

import io.netty5.buffer.Buffer;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

class MsgHdrMemory {
    private final long memory;
    private final long cmsgDataAddr;

    MsgHdrMemory() {
        int size = Native.SIZEOF_MSGHDR + Native.SIZEOF_SOCKADDR_STORAGE + Native.SIZEOF_IOVEC + Native.CMSG_SPACE;
        memory = PlatformDependent.allocateMemory(size);
        PlatformDependent.setMemory(memory, size, (byte) 0);

        // We retrieve the address of the data once so we can just be JNI free after construction.
        cmsgDataAddr = Native.cmsghdrData(memory + Native.SIZEOF_MSGHDR +
                Native.SIZEOF_SOCKADDR_STORAGE + Native.SIZEOF_IOVEC);
    }

    void write(LinuxSocket socket, SocketAddress address, long bufferAddress, int length, short segmentSize) {
        long sockAddress = memory + Native.SIZEOF_MSGHDR;
        long iovAddress = sockAddress + Native.SIZEOF_SOCKADDR_STORAGE;
        long cmsgAddr = iovAddress + Native.SIZEOF_IOVEC;
        int addressLength;
        if (address == null) {
            addressLength = socket.isIpv6() ? Native.SIZEOF_SOCKADDR_IN6 : Native.SIZEOF_SOCKADDR_IN;
            PlatformDependent.setMemory(sockAddress, Native.SIZEOF_SOCKADDR_STORAGE, (byte) 0);
        } else { // todo handle domain socket addresses
            addressLength = SockaddrIn.write(socket.isIpv6(), sockAddress, address);
        }
        Iov.write(iovAddress, bufferAddress, length);
        MsgHdr.write(memory, sockAddress, addressLength, iovAddress, 1, cmsgAddr, cmsgDataAddr, segmentSize);
    }

    boolean hasPort(IoUringDatagramChannel channel) {
        long sockAddress = memory + Native.SIZEOF_MSGHDR;
        if (channel.isIpv6()) {
            return SockaddrIn.hasPortIpv6(sockAddress);
        }
        return SockaddrIn.hasPortIpv4(sockAddress);
    }

    DatagramPacket read(IoUringDatagramChannel channel, Buffer buffer, int bytesRead) {
        long sockAddress = memory + Native.SIZEOF_MSGHDR;
        InetSocketAddress sender;
        if (channel.isIpv6()) { // TODO handle domain socket addresses
            byte[] ipv6Bytes = channel.inet6AddressArray();
            byte[] ipv4bytes = channel.inet4AddressArray();

            sender = SockaddrIn.readIPv6(sockAddress, ipv6Bytes, ipv4bytes);
        } else {
            byte[] bytes = channel.inet4AddressArray();
            sender = SockaddrIn.readIPv4(sockAddress, bytes);
        }
        long iovAddress = memory + Native.SIZEOF_MSGHDR + Native.SIZEOF_SOCKADDR_STORAGE;
        long bufferAddress = Iov.readBufferAddress(iovAddress);
        int bufferLength = Iov.readBufferLength(iovAddress);
        // reconstruct the reader index based on the memoryAddress of the buffer and the bufferAddress that was used
        // in the iovec.
        try (var itr = buffer.forEachComponent()) {
            var cmp = itr.first();
            assert cmp != null;
            int readerOffset = (int) (bufferAddress - cmp.baseNativeAddress());
            buffer.writerOffset(readerOffset + bytesRead);
            buffer.readerOffset(readerOffset);
        }

        return new DatagramPacket(buffer, channel.localAddress(), sender);
    }

    long address() {
        return memory;
    }

    void release() {
        PlatformDependent.freeMemory(memory);
    }
}

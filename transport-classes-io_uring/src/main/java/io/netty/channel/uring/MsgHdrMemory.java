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

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.Buffer;
import io.netty.util.internal.CleanableDirectBuffer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

final class MsgHdrMemory {
    private static final byte[] EMPTY_SOCKADDR_STORAGE = new byte[Native.SIZEOF_SOCKADDR_STORAGE];
    // It is not possible to have a zero length buffer in sendFd,
    // so we use a 1 byte buffer here.
    private static final int GLOBAL_IOV_LEN = 1;
    private static final ByteBuffer GLOBAL_IOV_BASE =  Buffer.allocateDirectWithNativeOrder(GLOBAL_IOV_LEN);
    private static final long GLOBAL_IOV_BASE_ADDRESS = Buffer.memoryAddress(GLOBAL_IOV_BASE);
    private final CleanableDirectBuffer msgHdrMemoryCleanable;
    private final CleanableDirectBuffer socketAddrMemoryCleanable;
    private final CleanableDirectBuffer iovMemoryCleanable;
    private final CleanableDirectBuffer cmsgDataMemoryCleanable;
    private final ByteBuffer msgHdrMemory;
    private final ByteBuffer socketAddrMemory;
    private final ByteBuffer iovMemory;
    private final ByteBuffer cmsgDataMemory;

    private final long msgHdrMemoryAddress;
    private final short idx;
    private final int cmsgDataOffset;

    MsgHdrMemory(short idx) {
        this.idx = idx;
        msgHdrMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.SIZEOF_MSGHDR);
        socketAddrMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.SIZEOF_SOCKADDR_STORAGE);
        iovMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.SIZEOF_IOVEC);
        cmsgDataMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.CMSG_SPACE);

        msgHdrMemory = msgHdrMemoryCleanable.buffer();
        socketAddrMemory = socketAddrMemoryCleanable.buffer();
        iovMemory = iovMemoryCleanable.buffer();
        cmsgDataMemory = cmsgDataMemoryCleanable.buffer();

        msgHdrMemoryAddress = Buffer.memoryAddress(msgHdrMemory);

        long cmsgDataMemoryAddr = Buffer.memoryAddress(cmsgDataMemory);
        long cmsgDataAddr = Native.cmsghdrData(cmsgDataMemoryAddr);
        cmsgDataOffset = (int) (cmsgDataAddr - cmsgDataMemoryAddr);
    }

    MsgHdrMemory() {
        this.idx = 0;
        // jdk will memset the memory to 0, so we don't need to do it here.
        msgHdrMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.SIZEOF_MSGHDR);
        socketAddrMemoryCleanable = null;
        iovMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.SIZEOF_IOVEC);
        cmsgDataMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(Native.CMSG_SPACE_FOR_FD);

        msgHdrMemory = msgHdrMemoryCleanable.buffer();
        socketAddrMemory = null;
        iovMemory = iovMemoryCleanable.buffer();
        cmsgDataMemory = cmsgDataMemoryCleanable.buffer();

        msgHdrMemoryAddress = Buffer.memoryAddress(msgHdrMemory);
        // These two parameters must be set to valid values and cannot be 0,
        // otherwise the fd we get in io_uring_recvmsg is 0
        Iov.set(iovMemory, GLOBAL_IOV_BASE_ADDRESS, GLOBAL_IOV_LEN);

        long cmsgDataMemoryAddr = Buffer.memoryAddress(cmsgDataMemory);
        long cmsgDataAddr = Native.cmsghdrData(cmsgDataMemoryAddr);
        cmsgDataOffset = (int) (cmsgDataAddr - cmsgDataMemoryAddr);
    }

    void set(LinuxSocket socket, InetSocketAddress address, long bufferAddress , int length, short segmentSize) {
        int addressLength;
        if (address == null) {
            addressLength = socket.isIpv6() ? Native.SIZEOF_SOCKADDR_IN6 : Native.SIZEOF_SOCKADDR_IN;
            socketAddrMemory.mark();
            try {
                socketAddrMemory.put(EMPTY_SOCKADDR_STORAGE);
            } finally {
                socketAddrMemory.reset();
            }
        } else {
            addressLength = SockaddrIn.set(socket.isIpv6(), socketAddrMemory, address);
        }
        Iov.set(iovMemory, bufferAddress, length);
        MsgHdr.set(msgHdrMemory, socketAddrMemory, addressLength, iovMemory, 1, cmsgDataMemory,
                cmsgDataOffset, segmentSize);
    }

    void setScmRightsFd(int fd) {
        MsgHdr.prepSendFd(msgHdrMemory, fd, cmsgDataMemory, cmsgDataOffset, iovMemory, 1);
    }

    int getScmRightsFd() {
        return MsgHdr.getCmsgData(msgHdrMemory, cmsgDataMemory, cmsgDataOffset);
    }

    void prepRecvReadFd() {
        MsgHdr.prepReadFd(msgHdrMemory, cmsgDataMemory, cmsgDataOffset, iovMemory, 1);
    }

    boolean hasPort(IoUringDatagramChannel channel) {
        if (channel.socket.isIpv6()) {
            return SockaddrIn.hasPortIpv6(socketAddrMemory);
        }
        return SockaddrIn.hasPortIpv4(socketAddrMemory);
    }

    DatagramPacket get(IoUringDatagramChannel channel, IoUringIoHandler handler, ByteBuf buffer, int bytesRead) {
        InetSocketAddress sender;
        if (channel.socket.isIpv6()) {
            byte[] ipv6Bytes = handler.inet6AddressArray();
            byte[] ipv4bytes = handler.inet4AddressArray();

            sender = SockaddrIn.getIPv6(socketAddrMemory, ipv6Bytes, ipv4bytes);
        } else {
            byte[] bytes = handler.inet4AddressArray();
            sender = SockaddrIn.getIPv4(socketAddrMemory, bytes);
        }
        long bufferAddress = Iov.getBufferAddress(iovMemory);
        int bufferLength = Iov.getBufferLength(iovMemory);
        // reconstruct the reader index based on the memoryAddress of the buffer and the bufferAddress that was used
        // in the iovec.
        long memoryAddress = IoUring.memoryAddress(buffer);
        int readerIndex = (int) (bufferAddress - memoryAddress);

        ByteBuf slice = buffer.slice(readerIndex, bufferLength)
                .writerIndex(bytesRead);
        return new DatagramPacket(slice.retain(), channel.localAddress(), sender);
    }

    short idx() {
        return idx;
    }

    long address() {
        return msgHdrMemoryAddress;
    }

    void release() {
        msgHdrMemoryCleanable.clean();
        if (socketAddrMemoryCleanable != null) {
            socketAddrMemoryCleanable.clean();
        }
        iovMemoryCleanable.clean();
        cmsgDataMemoryCleanable.clean();
    }
}

/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.Limits;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.netty.channel.unix.Limits.UIO_MAX_IOV;
import static io.netty.channel.unix.NativeInetAddress.copyIpv4MappedIpv6Address;

/**
 * Support <a href="https://linux.die.net//man/2/sendmmsg">sendmmsg(...)</a> on linux with GLIBC 2.14+
 */
final class NativeDatagramPacketArray {

    // Use UIO_MAX_IOV as this is the maximum number we can write with one sendmmsg(...) call.
    private final NativeDatagramPacket[] packets = new NativeDatagramPacket[UIO_MAX_IOV];

    // We share one IovArray for all NativeDatagramPackets to reduce memory overhead. This will allow us to write
    // up to IOV_MAX iovec across all messages in one sendmmsg(...) call.
    private final IovArray iovArray = new IovArray();

    // temporary array to copy the ipv4 part of ipv6-mapped-ipv4 addresses and then create a Inet4Address out of it.
    private final byte[] ipv4Bytes = new byte[4];
    private final MyMessageProcessor processor = new MyMessageProcessor();

    private int count;

    NativeDatagramPacketArray() {
        for (int i = 0; i < packets.length; i++) {
            packets[i] = new NativeDatagramPacket();
        }
    }

    boolean addWritable(ByteBuf buf, int index, int len) {
        return add0(buf, index, len, 0, null);
    }

    private boolean add0(ByteBuf buf, int index, int len, int segmentLen, InetSocketAddress recipient) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        if (len == 0) {
            return true;
        }
        int offset = iovArray.count();
        if (offset == Limits.IOV_MAX || !iovArray.add(buf, index, len)) {
            // Not enough space to hold the whole content, we will try again later.
            return false;
        }
        NativeDatagramPacket p = packets[count];
        p.init(iovArray.memoryAddress(offset), iovArray.count() - offset, segmentLen, recipient);

        count++;
        return true;
    }

    void add(ChannelOutboundBuffer buffer, boolean connected, int maxMessagesPerWrite) throws Exception {
        processor.connected = connected;
        processor.maxMessagesPerWrite = maxMessagesPerWrite;
        buffer.forEachFlushedMessage(processor);
    }

    /**
     * Returns the count
     */
    int count() {
        return count;
    }

    /**
     * Returns an array with {@link #count()} {@link NativeDatagramPacket}s filled.
     */
    NativeDatagramPacket[] packets() {
        return packets;
    }

    void clear() {
        this.count = 0;
        this.iovArray.clear();
    }

    void release() {
        iovArray.release();
    }

    private final class MyMessageProcessor implements MessageProcessor {
        private boolean connected;
        private int maxMessagesPerWrite;

        @Override
        public boolean processMessage(Object msg) {
            final boolean added;
            if (msg instanceof DatagramPacket) {
                DatagramPacket packet = (DatagramPacket) msg;
                ByteBuf buf = packet.content();
                int segmentSize = 0;
                if (packet instanceof io.netty.channel.unix.SegmentedDatagramPacket) {
                    int seg = ((io.netty.channel.unix.SegmentedDatagramPacket) packet).segmentSize();
                    // We only need to tell the kernel that we want to use UDP_SEGMENT if there are multiple
                    // segments in the packet.
                    if (buf.readableBytes() > seg) {
                        segmentSize = seg;
                    }
                }
                added = add0(buf, buf.readerIndex(), buf.readableBytes(), segmentSize, packet.recipient());
            } else if (msg instanceof ByteBuf && connected) {
                ByteBuf buf = (ByteBuf) msg;
                added = add0(buf, buf.readerIndex(), buf.readableBytes(), 0, null);
            } else {
                added = false;
            }
            if (added) {
                maxMessagesPerWrite--;
                return maxMessagesPerWrite > 0;
            }
            return false;
        }
    }

    private static InetSocketAddress newAddress(byte[] addr, int addrLen, int port, int scopeId, byte[] ipv4Bytes)
            throws UnknownHostException {
        final InetAddress address;
        if (addrLen == ipv4Bytes.length) {
            System.arraycopy(addr, 0, ipv4Bytes, 0, addrLen);
            address = InetAddress.getByAddress(ipv4Bytes);
        } else {
            address = Inet6Address.getByAddress(null, addr, scopeId);
        }
        return new InetSocketAddress(address, port);
    }

    /**
     * Used to pass needed data to JNI.
     */
    @SuppressWarnings("unused")
    final class NativeDatagramPacket {

        // This is the actual struct iovec*
        private long memoryAddress;
        private int count;

        private final byte[] senderAddr = new byte[16];
        private int senderAddrLen;
        private int senderScopeId;
        private int senderPort;

        private final byte[] recipientAddr = new byte[16];
        private int recipientAddrLen;
        private int recipientScopeId;
        private int recipientPort;

        private int segmentSize;

        private void init(long memoryAddress, int count, int segmentSize, InetSocketAddress recipient) {
            this.memoryAddress = memoryAddress;
            this.count = count;
            this.segmentSize = segmentSize;

            this.senderScopeId = 0;
            this.senderPort = 0;
            this.senderAddrLen = 0;

            if (recipient == null) {
                this.recipientScopeId = 0;
                this.recipientPort = 0;
                this.recipientAddrLen = 0;
            } else {
                InetAddress address = recipient.getAddress();
                if (address instanceof Inet6Address) {
                    System.arraycopy(address.getAddress(), 0, recipientAddr, 0, recipientAddr.length);
                    recipientScopeId = ((Inet6Address) address).getScopeId();
                } else {
                    copyIpv4MappedIpv6Address(address.getAddress(), recipientAddr);
                    recipientScopeId = 0;
                }
                recipientAddrLen = recipientAddr.length;
                recipientPort = recipient.getPort();
            }
        }

        DatagramPacket newDatagramPacket(ByteBuf buffer, InetSocketAddress recipient) throws UnknownHostException {
            InetSocketAddress sender = newAddress(senderAddr, senderAddrLen, senderPort, senderScopeId, ipv4Bytes);
            if (recipientAddrLen != 0) {
                recipient = newAddress(recipientAddr, recipientAddrLen, recipientPort, recipientScopeId, ipv4Bytes);
            }
            buffer.writerIndex(count);

            // UDP_GRO
            if (segmentSize > 0) {
                return new SegmentedDatagramPacket(buffer, segmentSize, recipient, sender);
            }
            return new DatagramPacket(buffer, recipient, sender);
        }
    }
}

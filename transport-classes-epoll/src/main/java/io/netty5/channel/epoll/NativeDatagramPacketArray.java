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
package io.netty5.channel.epoll;

import io.netty5.buffer.Buffer;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.Limits;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.util.internal.UnstableApi;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.function.Predicate;

import static io.netty5.channel.unix.Limits.UIO_MAX_IOV;
import static io.netty5.channel.unix.NativeInetAddress.copyIpv4MappedIpv6Address;

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

    boolean addWritable(Buffer buf, int segmentLen, InetSocketAddress recipient) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        if (buf.writableBytes() == 0) {
            return true;
        }
        int iovArrayStart = iovArray.count();
        if (iovArrayStart == Limits.IOV_MAX) {
            return false;
        }
        try (var iterator = buf.forEachComponent()) {
            var first = iterator.firstWritable();
            if (first == null) {
                return false;
            }
            for (var c = first; c != null; c = c.nextWritable()) {
                int writableBytes = c.writableBytes();
                int byteCount = segmentLen == 0? writableBytes : Math.min(writableBytes, segmentLen);
                if (iovArray.addWritable(c, byteCount)) {
                    NativeDatagramPacket p = packets[count];
                    p.init(iovArray.memoryAddress(iovArrayStart), iovArray.count() - iovArrayStart, segmentLen,
                           recipient);
                    count++;
                    c.skipWritableBytes(byteCount);
                }
            }
            return true;
        }
    }

    boolean addReadable(Buffer buf, int segmentLen, InetSocketAddress recipient) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        if (buf.readableBytes() == 0) {
            return true;
        }
        int iovArrayStart = iovArray.count();
        if (iovArrayStart == Limits.IOV_MAX) {
            return false;
        }
        try (var iterator = buf.forEachComponent()) {
            var first = iterator.firstReadable();
            if (first == null) {
                return false;
            }
            for (var c = first; c != null; c = c.nextReadable()) {
                if (segmentLen == 0) {
                    int readableBytes = c.readableBytes();
                    if (iovArray.addReadable(c, readableBytes)) {
                        c.skipReadableBytes(readableBytes);
                    } else {
                        break;
                    }
                } else {
                    // In the case of segments we need to add each of the segments in a loop.
                    while (c.readableBytes() > 0) {
                        int byteCount = Math.min(c.readableBytes(), segmentLen);
                        if (iovArray.addReadable(c, byteCount)) {
                            c.skipReadableBytes(byteCount);
                        } else {
                            break;
                        }
                    }
                }
            }
            // Add one packet for sendmmsg(...) now.
            NativeDatagramPacket p = packets[count];
            long packetAddr = iovArray.memoryAddress(iovArrayStart);
            p.init(packetAddr, iovArray.count() - iovArrayStart, segmentLen, recipient);
            count++;
            return true;
        }
    }

    Predicate<Object> addFunction(boolean connected, int maxMessagesPerWrite) {
        processor.connected = connected;
        processor.maxMessagesPerWrite = maxMessagesPerWrite;
        return processor;
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
        count = 0;
        iovArray.clear();
    }

    void release() {
        iovArray.release();
    }

    private final class MyMessageProcessor implements Predicate<Object> {
        private boolean connected;
        private int maxMessagesPerWrite;

        @Override
        public boolean test(Object msg) {
            final boolean added;
            if (msg instanceof DatagramPacket) {
                DatagramPacket packet = (DatagramPacket) msg;
                Buffer buf = packet.content();
                int segmentSize = 0;
                if (packet instanceof SegmentedDatagramPacket) {
                    int seg = ((SegmentedDatagramPacket) packet).segmentSize();
                    // We only need to tell the kernel that we want to use UDP_SEGMENT if there are multiple
                    // segments in the packet.
                    if (buf.readableBytes() > seg) {
                        segmentSize = seg;
                    }
                }
                boolean addedAny = false;
                while (buf.readableBytes() > 0 &&
                        addReadable(buf, segmentSize, (InetSocketAddress) packet.recipient())) {
                    addedAny = true;
                }
                added = addedAny;
            } else if (msg instanceof Buffer && connected) {
                Buffer buf = (Buffer) msg;
                boolean addedAny = false;
                while (buf.readableBytes() > 0 && addReadable(buf, 0, null)) {
                    addedAny = true;
                }
                added = addedAny;
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
    @UnstableApi
    public final class NativeDatagramPacket {

        // IMPORTANT: Most of the below variables are accessed via JNI. Be aware if you change any of these you also
        // need to change these in the related .c file!

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

            senderScopeId = 0;
            senderPort = -1;
            senderAddrLen = 0;

            if (recipient == null) {
                recipientScopeId = 0;
                recipientPort = 0;
                recipientAddrLen = 0;
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

        boolean hasSender() {
            return senderPort >= 0;
        }

        int count() {
            return count;
        }

        DatagramPacket newDatagramPacket(Buffer buffer, InetSocketAddress recipient) throws UnknownHostException {
            InetSocketAddress sender = newAddress(senderAddr, senderAddrLen, senderPort, senderScopeId, ipv4Bytes);
            if (recipientAddrLen != 0) {
                recipient = newAddress(recipientAddr, recipientAddrLen, recipientPort, recipientScopeId, ipv4Bytes);
            }

            // Slice out the buffer with the correct length.
            Buffer slice = buffer.readSplit(count);

            // UDP_GRO
            if (segmentSize > 0) {
                return new SegmentedDatagramPacket(slice, segmentSize, recipient, sender);
            }
            return new DatagramPacket(slice, recipient, sender);
        }
    }
}

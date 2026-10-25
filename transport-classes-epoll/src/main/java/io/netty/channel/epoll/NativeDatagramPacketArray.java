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
import io.netty.channel.unix.ControlMessageBuffer;
import io.netty.channel.unix.ControlMessageDatagramPacket;
import io.netty.channel.unix.ControlMessageDecoder;
import io.netty.channel.unix.ControlMessageEncoder;
import io.netty.channel.unix.ControlMessages;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.Limits;
import io.netty.channel.unix.SegmentedDatagramPacket;
import io.netty.util.internal.UnstableApi;

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

    // We share one ControlMessageBuffer for all NativeDatagramPackets, which is used to store the control messages
    // (ancillary data) that should be sent or received. The memory is allocated lazily once it is used for the first
    // time.
    private final ControlMessageBuffer controlMessageBuffer = new ControlMessageBuffer();

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
        return addWritable(buf, index, len, 0);
    }

    /**
     * Add the given writable space of the {@link ByteBuf} which is used to receive a datagram. If
     * {@code controlLen > 0} the given number of bytes will be reserved to receive the control messages.
     */
    boolean addWritable(ByteBuf buf, int index, int len, int controlLen) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        int controlOffset = -1;
        if (controlLen > 0) {
            controlOffset = controlMessageBuffer.reserve(controlLen);
            if (controlOffset == -1) {
                // Not enough space left, we will try again later.
                return false;
            }
        }
        return add0(buf, index, len, 0, null, controlOffset, controlLen);
    }

    /**
     * Ensure that the control messages of the given number of bytes can be received without the need to grow the
     * underlying memory. This must only be called while the {@link NativeDatagramPacketArray} is empty.
     */
    void ensureControlCapacity(int capacity) {
        assert count == 0;
        controlMessageBuffer.ensureCapacity(capacity);
    }

    private boolean add0(ByteBuf buf, int index, int len, int segmentLen, InetSocketAddress recipient,
                         ControlMessages controlMessages) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per
            // recvmmsg(...) / sendmmsg(...) call, we will try again later.
            return false;
        }
        if (len == 0) {
            return true;
        }
        int controlOffset = -1;
        int controlLen = 0;
        if (controlMessages != null && !controlMessages.isEmpty()) {
            controlLen = ControlMessageEncoder.encodedLength(controlMessages);
            controlOffset = controlMessageBuffer.reserve(controlLen);
            if (controlOffset == -1) {
                // Not enough space to hold the control messages, we will try again later.
                return false;
            }
            ControlMessageEncoder.encode(controlMessages, controlMessageBuffer.buffer(), controlOffset);
        }
        return add0(buf, index, len, segmentLen, recipient, controlOffset, controlLen);
    }

    private boolean add0(ByteBuf buf, int index, int len, int segmentLen, InetSocketAddress recipient,
                         int controlOffset, int controlLen) {
        if (len == 0) {
            return true;
        }
        int offset = iovArray.count();
        if (offset == Limits.IOV_MAX || !iovArray.add(buf, index, len)) {
            // Not enough space to hold the whole content, we will try again later.
            return false;
        }
        NativeDatagramPacket p = packets[count];
        p.init(iovArray.memoryAddress(offset), iovArray.count() - offset, segmentLen, recipient,
                controlOffset, controlLen);

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
        this.controlMessageBuffer.clear();
    }

    void release() {
        iovArray.release();
        controlMessageBuffer.release();
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
                ControlMessages controlMessages = null;
                if (packet instanceof io.netty.channel.unix.SegmentedDatagramPacket) {
                    int seg = ((io.netty.channel.unix.SegmentedDatagramPacket) packet).segmentSize();
                    // We only need to tell the kernel that we want to use UDP_SEGMENT if there are multiple
                    // segments in the packet.
                    if (buf.readableBytes() > seg) {
                        segmentSize = seg;
                    }
                } else if (packet instanceof ControlMessageDatagramPacket) {
                    controlMessages = ((ControlMessageDatagramPacket) packet).controlMessages();
                }
                added = add0(buf, buf.readerIndex(), buf.readableBytes(), segmentSize, packet.recipient(),
                        controlMessages);
            } else if (msg instanceof ByteBuf && connected) {
                ByteBuf buf = (ByteBuf) msg;
                added = add0(buf, buf.readerIndex(), buf.readableBytes(), 0, null, null);
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

        // The memory address and length of the control messages (msg_control and msg_controllen). If controlAddress
        // is 0 the native code uses its own buffer to receive the control messages it handles itself (like UDP_GRO).
        // When receiving, controlLen is updated to the length of the received control messages.
        private long controlAddress;
        private int controlLen;

        // Only used by java, this is the offset of the control messages in the ControlMessageBuffer.
        private int controlOffset;

        private void init(long memoryAddress, int count, int segmentSize, InetSocketAddress recipient,
                          int controlOffset, int controlLen) {
            this.memoryAddress = memoryAddress;
            this.count = count;
            this.segmentSize = segmentSize;
            if (controlOffset == -1) {
                this.controlAddress = 0;
                this.controlLen = 0;
                this.controlOffset = 0;
            } else {
                this.controlAddress = controlMessageBuffer.memoryAddress(controlOffset);
                this.controlLen = controlLen;
                this.controlOffset = controlOffset;
            }

            this.senderScopeId = 0;
            this.senderPort = -1;
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

        boolean hasSender() {
            return senderPort >= 0;
        }

        /**
         * Decode the control messages that were received.
         */
        ControlMessages decodeControlMessages(ControlMessageDecoder decoder) {
            if (controlAddress == 0) {
                return ControlMessages.EMPTY;
            }
            return decoder.decode(controlMessageBuffer.buffer(), controlOffset, controlLen);
        }

        /**
         * Create a new {@link DatagramPacket} for the received datagram. If {@code controlMessages} is not
         * {@code null} a {@link ControlMessageDatagramPacket} is returned, unless the datagram needs to be split
         * because of {@code UDP_GRO}, in which case the caller is responsible to use the control messages for each
         * segment.
         */
        DatagramPacket newDatagramPacket(ByteBuf buffer, InetSocketAddress recipient, ControlMessages controlMessages)
                throws UnknownHostException {
            InetSocketAddress sender = newAddress(senderAddr, senderAddrLen, senderPort, senderScopeId, ipv4Bytes);
            if (recipientAddrLen != 0) {
                recipient = newAddress(recipientAddr, recipientAddrLen, recipientPort, recipientScopeId, ipv4Bytes);
            }

            // Slice out the buffer with the correct length.
            ByteBuf slice = buffer.retainedSlice(buffer.readerIndex(), count);

            // UDP_GRO
            if (segmentSize > 0) {
                return new SegmentedDatagramPacket(slice, segmentSize, recipient, sender);
            }
            if (controlMessages != null) {
                return new ControlMessageDatagramPacket(slice, recipient, sender, controlMessages);
            }
            return new DatagramPacket(slice, recipient, sender);
        }
    }
}

/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.IovArray;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.netty.channel.unix.Limits.UIO_MAX_IOV;
import static io.netty.channel.unix.NativeInetAddress.ipv4MappedIpv6Address;

/**
 * Support <a href="http://linux.die.net/man/2/sendmmsg">sendmmsg(...)</a> on linux with GLIBC 2.14+
 */
final class NativeDatagramPacketArray implements ChannelOutboundBuffer.MessageProcessor {

    // Use UIO_MAX_IOV as this is the maximum number we can write with one sendmmsg(...) call.
    private final NativeDatagramPacket[] packets = new NativeDatagramPacket[UIO_MAX_IOV];

    // We share one IovArray for all NativeDatagramPackets to reduce memory overhead. This will allow us to write
    // up to IOV_MAX iovec across all messages in one sendmmsg(...) call.
    private final IovArray iovArray = new IovArray();
    private int count;

    NativeDatagramPacketArray() {
        for (int i = 0; i < packets.length; i++) {
            packets[i] = new NativeDatagramPacket();
        }
    }

    /**
     * Try to add the given {@link DatagramPacket}. Returns {@code true} on success,
     * {@code false} otherwise.
     */
    boolean add(DatagramPacket packet) {
        if (count == packets.length) {
            // We already filled up to UIO_MAX_IOV messages. This is the max allowed per sendmmsg(...) call, we will
            // try again later.
            return false;
        }
        ByteBuf content = packet.content();
        int len = content.readableBytes();
        if (len == 0) {
            return true;
        }
        NativeDatagramPacket p = packets[count];
        InetSocketAddress recipient = packet.recipient();

        int offset = iovArray.count();
        if (!iovArray.add(content)) {
            // Not enough space to hold the whole content, we will try again later.
            return false;
        }
        p.init(iovArray.memoryAddress(offset), iovArray.count() - offset, recipient);

        count++;
        return true;
    }

    @Override
    public boolean processMessage(Object msg) {
        return msg instanceof DatagramPacket && add((DatagramPacket) msg);
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

    /**
     * Used to pass needed data to JNI.
     */
    @SuppressWarnings("unused")
    static final class NativeDatagramPacket {

        // This is the actual struct iovec*
        private long memoryAddress;
        private int count;

        private byte[] addr;
        private int scopeId;
        private int port;

        private void init(long memoryAddress, int count, InetSocketAddress recipient) {
            this.memoryAddress = memoryAddress;
            this.count = count;

            InetAddress address = recipient.getAddress();
            if (address instanceof Inet6Address) {
                addr = address.getAddress();
                scopeId = ((Inet6Address) address).getScopeId();
            } else {
                addr = ipv4MappedIpv6Address(address.getAddress());
                scopeId = 0;
            }
            port = recipient.getPort();
        }
    }
}

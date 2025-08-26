/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.epoll.SegmentedDatagramPacket;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.ObjectUtil;

import java.net.InetSocketAddress;

/**
 * Class that provides utility methods to setup {@code QUIC} when using the {@code EPOLL} transport.
 */
public final class EpollQuicUtils {

    private EpollQuicUtils() { }

    /**
     * Return a new {@link SegmentedDatagramPacketAllocator} that can be used while using
     * {@link io.netty.channel.epoll.EpollDatagramChannel}.
     *
     * @param maxNumSegments the maximum number of segments that we try to send in one packet.
     * @return a allocator.
     */
    public static SegmentedDatagramPacketAllocator newSegmentedAllocator(int maxNumSegments) {
        ObjectUtil.checkInRange(maxNumSegments, 1, 64, "maxNumSegments");
        if (SegmentedDatagramPacket.isSupported()) {
            return new EpollSegmentedDatagramPacketAllocator(maxNumSegments);
        }
        return SegmentedDatagramPacketAllocator.NONE;
    }

    private static final class EpollSegmentedDatagramPacketAllocator implements SegmentedDatagramPacketAllocator {

        private final int maxNumSegments;

        EpollSegmentedDatagramPacketAllocator(int maxNumSegments) {
            this.maxNumSegments = maxNumSegments;
        }

        @Override
        public int maxNumSegments() {
            return maxNumSegments;
        }

        @Override
        public DatagramPacket newPacket(ByteBuf buffer, int segmentSize, InetSocketAddress remoteAddress) {
            return new io.netty.channel.unix.SegmentedDatagramPacket(buffer, segmentSize, remoteAddress);
        }
    }
}

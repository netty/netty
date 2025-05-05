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
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;

/**
 * Used to allocate datagram packets that use UDP_SEGMENT (GSO).
 */
@FunctionalInterface
public interface SegmentedDatagramPacketAllocator {

    /**
     * {@link SegmentedDatagramPacketAllocator} which should be used if no UDP_SEGMENT is supported and used.
     */
    SegmentedDatagramPacketAllocator NONE = new SegmentedDatagramPacketAllocator() {
        @Override
        public int maxNumSegments() {
            return 0;
        }

        @Override
        public DatagramPacket newPacket(ByteBuf buffer, int segmentSize, InetSocketAddress remoteAddress) {
            throw new UnsupportedOperationException();
        }
    };

    /**
     * The maximum number of segments to use per packet. By default this is {@code 10} but this may be overridden by
     * the implementation of the interface.
     *
     * @return  the segments.
     */
    default int maxNumSegments() {
        return 10;
    }

    /**
     * Return a new segmented {@link DatagramPacket}.
     *
     * @param buffer        the {@link ByteBuf} that is used as content.
     * @param segmentSize   the size of each segment.
     * @param remoteAddress the remote address to send to.
     * @return              the packet.
     */
    DatagramPacket newPacket(ByteBuf buffer, int segmentSize, InetSocketAddress remoteAddress);
}

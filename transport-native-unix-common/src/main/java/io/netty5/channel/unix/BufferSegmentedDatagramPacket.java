/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.unix;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.util.internal.ObjectUtil;

import java.net.InetSocketAddress;

/**
 * Allows to use <a href="https://blog.cloudflare.com/accelerating-udp-packet-transmission-for-quic/">GSO</a>
 * if the underlying OS supports it. Before using this you should ensure your system support it.
 */
public class BufferSegmentedDatagramPacket extends BufferDatagramPacket {

    private final int segmentSize;

    /**
     * Create a new segmented datagram packet.
     * The attached message may be sent in multiple segment-sized network packets.
     *
     * @param message The data to send.
     * @param segmentSize The (positive) segment size.
     * @param recipient The recipient address.
     * @param sender The sender address.
     */
    public BufferSegmentedDatagramPacket(Buffer message, int segmentSize, InetSocketAddress recipient,
                                         InetSocketAddress sender) {
        super(message, recipient, sender);
        this.segmentSize = ObjectUtil.checkPositive(segmentSize, "segmentSize");
    }

    /**
     * Create a new segmented datagram packet.
     * The attached message may be sent in multiple segment-sized network packets.
     *
     * @param message The data to send.
     * @param segmentSize The (positive) segment size.
     * @param recipient The recipient address.
     */
    public BufferSegmentedDatagramPacket(Buffer message, int segmentSize, InetSocketAddress recipient) {
        super(message, recipient);
        this.segmentSize = ObjectUtil.checkPositive(segmentSize, "segmentSize");
    }

    /**
     * Return the size of each segment (the last segment can be smaller).
     *
     * @return size of segments.
     */
    public int segmentSize() {
        return segmentSize;
    }

    @Override
    public BufferSegmentedDatagramPacket replace(Buffer content) {
        return new BufferSegmentedDatagramPacket(content, segmentSize, recipient(), sender());
    }

    @Override
    public BufferSegmentedDatagramPacket touch(Object hint) {
        super.touch(hint);
        return this;
    }
}

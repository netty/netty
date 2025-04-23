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
package io.netty.handler.codec.quic;

import io.netty.channel.ChannelOption;

/**
 * {@link ChannelOption}s specific to QUIC.
 */
public final class QuicChannelOption<T> extends ChannelOption<T> {

    /**
     * If set to {@code true} the {@link QuicStreamChannel} will read {@link QuicStreamFrame}s and fire it through
     * the pipeline, if {@code false} it will read {@link io.netty.buffer.ByteBuf} and translate the FIN flag to
     * events.
     */
    public static final ChannelOption<Boolean> READ_FRAMES =
            valueOf(QuicChannelOption.class, "READ_FRAMES");

    /**
     * Enable <a href="https://quiclog.github.io/internet-drafts/draft-marx-qlog-main-schema.html">qlog</a>
     * for a {@link QuicChannel}.
     */
    public static final ChannelOption<QLogConfiguration> QLOG = valueOf(QuicChannelOption.class, "QLOG");

    /**
     * Use <a href="https://blog.cloudflare.com/accelerating-udp-packet-transmission-for-quic/">GSO</a>
     * for QUIC packets if possible.
     */
    public static final ChannelOption<SegmentedDatagramPacketAllocator> SEGMENTED_DATAGRAM_PACKET_ALLOCATOR =
            valueOf(QuicChannelOption.class, "SEGMENTED_DATAGRAM_PACKET_ALLOCATOR");

    @SuppressWarnings({ "deprecation" })
    private QuicChannelOption() {
        super(null);
    }
}

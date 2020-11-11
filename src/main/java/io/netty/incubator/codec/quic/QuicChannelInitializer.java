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
package io.netty.incubator.codec.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import java.util.Objects;

/**
 * {@link ChannelInitializer} for {@link QuicChannel}s and it's accepted {@link QuicStreamChannel}s.
 */
public final class QuicChannelInitializer extends ChannelInitializer<QuicChannel> {

    private final ChannelHandler quicChannelHandler;
    private final ChannelHandler streamChannelHandler;

    /**
     * Create a new instance.
     *
     * @param streamChannelHandler The {@link ChannelHandler} that is added to the
     *                             {@link io.netty.channel.ChannelPipeline} of the accepted {@link QuicStreamChannel}.
     */
    public QuicChannelInitializer(ChannelHandler streamChannelHandler) {
        this(null, streamChannelHandler);
    }

    /**
     * Create a new instance.
     *
     * @param quicChannelHandler   The {@link ChannelHandler} that is added to the
     *                             {@link io.netty.channel.ChannelPipeline} of the {@link QuicChannel} or {@code null}
     *                             if none should be added.
     * @param streamChannelHandler The {@link ChannelHandler} that is added to the
     *                             {@link io.netty.channel.ChannelPipeline} of the accepted {@link QuicStreamChannel}.
     */
    public QuicChannelInitializer(ChannelHandler quicChannelHandler, ChannelHandler streamChannelHandler) {
        this.quicChannelHandler = quicChannelHandler;
        this.streamChannelHandler = Objects.requireNonNull(streamChannelHandler, "streamChannelHandler");
    }

    @Override
    protected void initChannel(QuicChannel channel) {
        if (quicChannelHandler != null) {
            channel.pipeline().addLast(quicChannelHandler);
        }
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                Channel streamChannel = (Channel) msg;
                streamChannel.pipeline().addLast(streamChannelHandler);
                channel.eventLoop().register(streamChannel);
            }
        });
    }
}

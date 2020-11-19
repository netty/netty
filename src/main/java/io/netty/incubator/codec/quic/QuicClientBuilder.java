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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

/**
 * {@link QuicBuilder} for the client side.
 */
public final class QuicClientBuilder extends QuicBuilder<QuicClientBuilder> {

    /**
     * Build a QUIC codec for the client side.
     */
    private ChannelHandler buildCodec() {
        return new QuicheQuicClientCodec(createConfig());
    }

    /**
     * Build a new {@link Bootstrap} which can be used for {@link QuicChannel}s and is using the given
     * {@link Channel} as underlying transport.
     *
     * <strong>Each {@link Channel} can only be used to build one QUIC bootstrap.</strong>
     */
    public Bootstrap buildBootstrap(Channel channel) {
        ChannelHandler codec = buildCodec();
        channel.pipeline().addLast(codec);
        return new Bootstrap().group(channel.eventLoop())
                .channelFactory(new QuicheQuicChannelFactory(channel));
    }
}

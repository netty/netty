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

import io.netty.channel.ChannelFactory;

/**
 * {@link QuicCodecBuilder} which allows to build {@link QuicClientCodec}s.
 */
public final class QuicClientCodecBuilder extends QuicCodecBuilder<QuicClientCodecBuilder> {

    /**
     * Build a new {@link QuicClientCodec}.
     */
    public QuicClientCodec buildClientCodec() {
        // TODO: Think about the correct token length.
        return new QuicClientCodec(createConfig(), 25);
    }

    /**
     * Returns {@link ChannelFactory} that can be used to bootstrap {@link QuicChannel}s.
     */
    public static ChannelFactory<QuicChannel> newChannelFactory(QuicClientCodec codec) {
        return codec.newChannelFactory();
    }
}

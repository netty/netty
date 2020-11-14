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

import io.netty.channel.ChannelHandler;

import java.util.Objects;

/**
 * {@link QuicBuilder} for the server side.
 */
public final class QuicServerBuilder extends QuicBuilder<QuicServerBuilder> {
    public QuicServerBuilder() { }

    private QuicConnectionIdAddressGenerator connectionIdAddressGenerator;

    /**
     * Sets the QuicConnectionIdAddressGenerator to use.
     */
    public QuicServerBuilder connectionIdAddressGenerator(
            QuicConnectionIdAddressGenerator connectionIdAddressGenerator) {
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        return this;
    }

    /**
     * Build a new {@link QuicheQuicServerCodec}.
     *
     * @param tokenHandler the {@link QuicTokenHandler} that is used to generate and validate tokens.
     * @param quicChannelInitializer the {@link QuicChannelInitializer} that is used to initalize accepted
     *                               {@link QuicChannel}s and remote-initiated {@link QuicStreamChannel}s.
     * @return a new codec.
     */
    public ChannelHandler buildCodec(QuicTokenHandler tokenHandler,
                                     QuicChannelInitializer quicChannelInitializer) {
        Objects.requireNonNull(tokenHandler, "tokenHandler");
        Objects.requireNonNull(quicChannelInitializer, "quicChannelHandler");
        QuicConnectionIdAddressGenerator generator = connectionIdAddressGenerator;
        if (generator == null) {
            generator = QuicConnectionIdAddress.randomGenerator();
        }

        return new QuicheQuicServerCodec(createConfig(), tokenHandler, generator, quicChannelInitializer);
    }
}

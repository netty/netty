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

import io.netty.channel.ChannelHandler;

import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * {@link QuicCodecBuilder} that configures and builds a {@link ChannelHandler} that should be added to the
 * {@link io.netty.channel.ChannelPipeline} of a {@code QUIC} client.
 */
public final class QuicClientCodecBuilder extends QuicCodecBuilder<QuicClientCodecBuilder> {

    /**
     * Creates a new instance.
     */
    public QuicClientCodecBuilder() {
        super(false);
    }

    private QuicClientCodecBuilder(QuicCodecBuilder<QuicClientCodecBuilder> builder) {
        super(builder);
    }

    @Override
    public QuicClientCodecBuilder clone() {
        return new QuicClientCodecBuilder(this);
    }

    @Override
    ChannelHandler build(QuicheConfig config,
                                   Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                                   Executor sslTaskExecutor,
                                   int localConnIdLength, FlushStrategy flushStrategy) {
        return new QuicheQuicClientCodec(config, sslEngineProvider, sslTaskExecutor, localConnIdLength, flushStrategy);
    }
}

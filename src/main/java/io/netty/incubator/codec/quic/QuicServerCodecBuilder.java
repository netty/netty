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
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ObjectUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link QuicCodecBuilder} that configures and builds a {@link ChannelHandler} that should be added to the
 * {@link io.netty.channel.ChannelPipeline} of a {@code QUIC} server.
 */
public final class QuicServerCodecBuilder extends QuicCodecBuilder<QuicServerCodecBuilder> {
    // The order in which ChannelOptions are applied is important they may depend on each other for validation
    // purposes.
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> attrs = new HashMap<>();
    private final Map<ChannelOption<?>, Object> streamOptions = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> streamAttrs = new HashMap<>();
    private ChannelHandler handler;
    private ChannelHandler streamHandler;
    private QuicConnectionIdGenerator connectionIdAddressGenerator;
    private QuicTokenHandler tokenHandler;

    public QuicServerCodecBuilder() { }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> QuicServerCodecBuilder option(ChannelOption<T> option, T value) {
        Quic.updateOptions(options, option, value);
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicChannel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> QuicServerCodecBuilder attr(AttributeKey<T> key, T value) {
        Quic.updateAttributes(attrs, key, value);
        return self();
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicChannel} once created.
     */
    public QuicServerCodecBuilder handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicStreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> QuicServerCodecBuilder streamOption(ChannelOption<T> option, T value) {
        Quic.updateOptions(streamOptions, option, value);
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicStreamChannel}. If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> QuicServerCodecBuilder streamAttr(AttributeKey<T> key, T value) {
        Quic.updateAttributes(streamAttrs, key, value);
        return self();
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} once created.
     */
    public QuicServerCodecBuilder streamHandler(ChannelHandler streamHandler) {
        this.streamHandler = ObjectUtil.checkNotNull(streamHandler, "streamHandler");
        return self();
    }

    /**
     * Sets the {@link QuicConnectionIdGenerator} to use.
     */
    public QuicServerCodecBuilder connectionIdAddressGenerator(
            QuicConnectionIdGenerator connectionIdAddressGenerator) {
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        return this;
    }

    /**
     * Set the {@link QuicTokenHandler} that is used to generate and validate tokens.
     */
    public QuicServerCodecBuilder tokenHandler(QuicTokenHandler tokenHandler) {
        this.tokenHandler = ObjectUtil.checkNotNull(tokenHandler, "tokenHandler");
        return self();
    }

    @Override
    protected void validate() {
        super.validate();
        if (handler == null && streamHandler == null) {
            throw new IllegalStateException("handler and streamHandler not set");
        }
        if (tokenHandler == null) {
            throw new IllegalStateException("tokenHandler not set");
        }
    }

    @Override
    protected ChannelHandler build(QuicheConfig config, int localConnIdLength) {
        validate();
        QuicTokenHandler tokenHandler = this.tokenHandler;
        QuicConnectionIdGenerator generator = connectionIdAddressGenerator;
        if (generator == null) {
            generator = QuicConnectionIdGenerator.randomGenerator();
        }
        ChannelHandler handler = this.handler;
        ChannelHandler streamHandler = this.streamHandler;
        return new QuicheQuicServerCodec(config, localConnIdLength, tokenHandler, generator,
                handler, Quic.optionsArray(options), Quic.attributesArray(attrs),
                streamHandler, Quic.optionsArray(streamOptions), Quic.attributesArray(streamAttrs));
    }
}

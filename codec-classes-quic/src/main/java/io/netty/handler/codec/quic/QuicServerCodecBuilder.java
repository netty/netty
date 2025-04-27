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
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ObjectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

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
    private QuicResetTokenGenerator resetTokenGenerator;

    /**
     * Creates a new instance.
     */
    public QuicServerCodecBuilder() {
        super(true);
    }

    private QuicServerCodecBuilder(QuicServerCodecBuilder builder) {
        super(builder);
        options.putAll(builder.options);
        attrs.putAll(builder.attrs);
        streamOptions.putAll(builder.streamOptions);
        streamAttrs.putAll(builder.streamAttrs);
        handler = builder.handler;
        streamHandler = builder.streamHandler;
        connectionIdAddressGenerator = builder.connectionIdAddressGenerator;
        tokenHandler = builder.tokenHandler;
        resetTokenGenerator = builder.resetTokenGenerator;
    }

    @Override
    public QuicServerCodecBuilder clone() {
        return new QuicServerCodecBuilder(this);
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     *
     * @param option    the {@link ChannelOption} to apply to the {@link QuicChannel}.
     * @param value     the value of the option.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicServerCodecBuilder option(ChannelOption<T> option, @Nullable T value) {
        Quic.updateOptions(options, option, value);
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicChannel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     *
     * @param key       the {@link AttributeKey} to apply to the {@link QuicChannel}.
     * @param value     the value of the attribute.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicServerCodecBuilder attr(AttributeKey<T> key, @Nullable T value) {
        Quic.updateAttributes(attrs, key, value);
        return self();
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicChannel} once created.
     *
     * @param handler   the {@link ChannelHandler} that is added to the {@link QuicChannel}s
     *                  {@link io.netty.channel.ChannelPipeline}.
     * @return          this instance.
     */
    public QuicServerCodecBuilder handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicStreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     *
     * @param option    the {@link ChannelOption} to apply to the {@link QuicStreamChannel}s.
     * @param value     the value of the option.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicServerCodecBuilder streamOption(ChannelOption<T> option, @Nullable T value) {
        Quic.updateOptions(streamOptions, option, value);
        return self();
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicStreamChannel}. If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     *
     * @param key       the {@link AttributeKey} to apply to the {@link QuicStreamChannel}s.
     * @param value     the value of the attribute.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicServerCodecBuilder streamAttr(AttributeKey<T> key, @Nullable T value) {
        Quic.updateAttributes(streamAttrs, key, value);
        return self();
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} once created.
     *
     * @param streamHandler     the {@link ChannelHandler} that is added to the {@link QuicStreamChannel}s
     *                          {@link io.netty.channel.ChannelPipeline}.
     * @return                  this instance.
     */
    public QuicServerCodecBuilder streamHandler(ChannelHandler streamHandler) {
        this.streamHandler = ObjectUtil.checkNotNull(streamHandler, "streamHandler");
        return self();
    }

    /**
     * Sets the {@link QuicConnectionIdGenerator} to use.
     *
     * @param connectionIdAddressGenerator  the {@link QuicConnectionIdGenerator} to use.
     * @return                              this instance.
     */
    public QuicServerCodecBuilder connectionIdAddressGenerator(
            QuicConnectionIdGenerator connectionIdAddressGenerator) {
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        return this;
    }

    /**
     * Set the {@link QuicTokenHandler} that is used to generate and validate tokens or
     * {@code null} if no tokens should be used at all.
     *
     * @param tokenHandler  the {@link QuicTokenHandler} to use.
     * @return              this instance.
     */
    public QuicServerCodecBuilder tokenHandler(@Nullable QuicTokenHandler tokenHandler) {
        this.tokenHandler = tokenHandler;
        return self();
    }

    /**
     * Set the {@link QuicResetTokenGenerator} that is used to generate stateless reset tokens or
     * {@code null} if the default should be used.
     *
     * @param resetTokenGenerator  the {@link QuicResetTokenGenerator} to use.
     * @return                     this instance.
     */
    public QuicServerCodecBuilder resetTokenGenerator(@Nullable QuicResetTokenGenerator resetTokenGenerator) {
        this.resetTokenGenerator = resetTokenGenerator;
        return self();
    }

    @Override
    protected void validate() {
        super.validate();
        if (handler == null && streamHandler == null) {
            throw new IllegalStateException("handler and streamHandler not set");
        }
    }

    @Override
    ChannelHandler build(QuicheConfig config,
                                   Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                                   Executor sslTaskExecutor,
                                   int localConnIdLength, FlushStrategy flushStrategy) {
        validate();
        QuicTokenHandler tokenHandler = this.tokenHandler;
        if (tokenHandler == null) {
            tokenHandler = NoQuicTokenHandler.INSTANCE;
        }
        QuicConnectionIdGenerator generator = connectionIdAddressGenerator;
        if (generator == null) {
            generator = QuicConnectionIdGenerator.signGenerator();
        }
        QuicResetTokenGenerator resetTokenGenerator = this.resetTokenGenerator;
        if (resetTokenGenerator == null) {
            resetTokenGenerator = QuicResetTokenGenerator.signGenerator();
        }
        ChannelHandler handler = this.handler;
        ChannelHandler streamHandler = this.streamHandler;
        return new QuicheQuicServerCodec(config, localConnIdLength, tokenHandler, generator, resetTokenGenerator,
                flushStrategy, sslEngineProvider, sslTaskExecutor, handler,
                Quic.toOptionsArray(options), Quic.toAttributesArray(attrs),
                streamHandler, Quic.toOptionsArray(streamOptions), Quic.toAttributesArray(streamAttrs));
    }
}

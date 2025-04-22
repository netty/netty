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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Allows to bootstrap outgoing {@link QuicStreamChannel}s.
 */
public final class QuicStreamChannelBootstrap {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicStreamChannelBootstrap.class);

    private final QuicChannel parent;
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> attrs = new HashMap<>();
    private ChannelHandler handler;
    private QuicStreamType type = QuicStreamType.BIDIRECTIONAL;

    /**
     * Creates a new instance which uses the given {@link QuicChannel} to bootstrap {@link QuicStreamChannel}s.
     *
     * @param parent    the {@link QuicChannel} that is used.

     */
    QuicStreamChannelBootstrap(QuicChannel parent) {
        this.parent = ObjectUtil.checkNotNull(parent, "parent");
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicStreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     *
     * @param option    the {@link ChannelOption} to apply to the {@link QuicStreamChannel}.
     * @param value     the value of the option.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicStreamChannelBootstrap option(ChannelOption<T> option, @Nullable T value) {
        Quic.updateOptions(options, option, value);
        return this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicStreamChannel}. If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     *
     * @param key       the {@link AttributeKey} to apply to the {@link QuicChannel}.
     * @param value     the value of the attribute.
     * @param <T>       the type of the value.
     * @return          this instance.
     */
    public <T> QuicStreamChannelBootstrap attr(AttributeKey<T> key, @Nullable T value) {
        Quic.updateAttributes(attrs, key, value);
        return this;
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} once created.
     *
     * @param streamHandler     the {@link ChannelHandler} that is added to the {@link QuicStreamChannel}s
     *                          {@link io.netty.channel.ChannelPipeline}.
     * @return                  this instance.
     */
    public QuicStreamChannelBootstrap handler(ChannelHandler streamHandler) {
        this.handler = ObjectUtil.checkNotNull(streamHandler, "streamHandler");
        return this;
    }

    /**
     * Set the {@link QuicStreamType} to use for the {@link QuicStreamChannel}, default is
     * {@link QuicStreamType#BIDIRECTIONAL}.
     *
     * @param type     the {@link QuicStreamType} of the  {@link QuicStreamChannel}.
     * @return         this instance.
     */
    public QuicStreamChannelBootstrap type(QuicStreamType type) {
        this.type = ObjectUtil.checkNotNull(type, "type");
        return this;
    }

    /**
     * Creates a new {@link QuicStreamChannel} and notifies the {@link Future}.
     *
     * @return  the {@link Future} that is notified once the operation completes.
     */
    public Future<QuicStreamChannel> create() {
        return create(parent.eventLoop().newPromise());
    }

    /**
     * Creates a new {@link QuicStreamChannel} and notifies the {@link Future}.
     *
     * @param promise   the {@link Promise} that is notified once the operation completes.
     * @return          the {@link Future} that is notified once the operation completes.
     */
    public Future<QuicStreamChannel> create(Promise<QuicStreamChannel> promise) {
        if (handler == null) {
            throw new IllegalStateException("streamHandler not set");
        }

        return parent.createStream(type, new QuicStreamChannelBootstrapHandler(handler,
                Quic.toOptionsArray(options), Quic.toAttributesArray(attrs)), promise);
    }

    private static final class QuicStreamChannelBootstrapHandler extends ChannelInitializer<QuicStreamChannel> {
        private final ChannelHandler streamHandler;
        private final Map.Entry<ChannelOption<?>, Object>[] streamOptions;
        private final Map.Entry<AttributeKey<?>, Object>[] streamAttrs;

        QuicStreamChannelBootstrapHandler(ChannelHandler streamHandler,
                                          Map.Entry<ChannelOption<?>, Object>[] streamOptions,
                                          Map.Entry<AttributeKey<?>, Object>[] streamAttrs) {
            this.streamHandler = streamHandler;
            this.streamOptions = streamOptions;
            this.streamAttrs = streamAttrs;
        }
        @Override
        protected void initChannel(QuicStreamChannel ch) {
            Quic.setupChannel(ch, streamOptions, streamAttrs, streamHandler, logger);
        }
    }
}

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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap that helps to bootstrap {@link QuicChannel}s and connecting these to remote peers.
 */
public final class QuicChannelBootstrap {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicChannelBootstrap.class);

    private final Channel parent;
    // The order in which ChannelOptions are applied is important they may depend on each other for validation
    // purposes.
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> attrs = new HashMap<>();
    private final Map<ChannelOption<?>, Object> streamOptions = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> streamAttrs = new HashMap<>();
    private ChannelHandler handler;
    private ChannelHandler streamHandler;

    /**
     * Creates a new instance which uses the given {@link Channel} to bootstrap the {@link QuicChannel}.
     * This {@link io.netty.channel.ChannelPipeline} of the {@link Channel} needs to have the quic codec in the
     * pipeline.
     */
    public QuicChannelBootstrap(Channel parent) {
        this.parent = ObjectUtil.checkNotNull(parent, "parent");
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> QuicChannelBootstrap option(ChannelOption<T> option, T value) {
        Quic.updateOptions(options, option, value);
        return this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicChannel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> QuicChannelBootstrap attr(AttributeKey<T> key, T value) {
        Quic.updateAttributes(attrs, key, value);
        return this;
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicChannel} once created.
     */
    public QuicChannelBootstrap handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return this;
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicStreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    public <T> QuicChannelBootstrap streamOption(ChannelOption<T> option, T value) {
        Quic.updateOptions(streamOptions, option, value);
        return this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicStreamChannel}. If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> QuicChannelBootstrap streamAttr(AttributeKey<T> key, T value) {
        Quic.updateAttributes(streamAttrs, key, value);
        return this;
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} once created.
     */
    public QuicChannelBootstrap streamHandler(ChannelHandler streamHandler) {
        this.streamHandler = ObjectUtil.checkNotNull(streamHandler, "streamHandler");
        return this;
    }

    /**
     * Connects a {@link QuicChannel} to the {@link QuicConnectionAddress} and notifies the future once done.
     */
    public ChannelFuture connect(QuicConnectionAddress address) {
        if (streamHandler == null) {
            throw new IllegalStateException("streamHandler not set");
        }
        QuicChannel channel = QuicheQuicChannel.forClient(parent,
                streamHandler, Quic.optionsArray(streamOptions), Quic.attributesArray(streamAttrs));
        Quic.setChannelOptions(channel, Quic.optionsArray(options), logger);
        Quic.setAttributes(channel, Quic.attributesArray(attrs));
        if (handler != null) {
            channel.pipeline().addLast(handler);
        }
        ChannelPromise promise = new DefaultChannelPromise(channel, parent.eventLoop());
        parent.eventLoop().register(channel).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                Throwable cause = future.cause();
                if (cause != null) {
                    promise.setFailure(cause);
                } else {
                    channel.connect(address).addListener(new ChannelPromiseNotifier(promise));
                }
            }
        });
        return promise;
    }
}

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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
    private SocketAddress remote;
    private QuicConnectionAddress connectionAddress;
    private ChannelHandler handler;
    private ChannelHandler streamHandler;

    /**
     * Creates a new instance which uses the given {@link Channel} to bootstrap the {@link QuicChannel}.
     * This {@link io.netty.channel.ChannelPipeline} of the {@link Channel} needs to have the quic codec in the
     * pipeline.
     */
    QuicChannelBootstrap(Channel parent) {
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
     * Set the remote address of the host to talk to.
     */
    public QuicChannelBootstrap remoteAddress(SocketAddress remote) {
        this.remote = ObjectUtil.checkNotNull(remote, "remote");
        return this;
    }

    /**
     * Set the {@link QuicConnectionAddress} to use. If none is specified a random address is generated on your
     * behalf.
     */
    public QuicChannelBootstrap connectionAddress(QuicConnectionAddress connectionAddress) {
        this.connectionAddress = ObjectUtil.checkNotNull(connectionAddress, "connectionAddress");
        return this;
    }

    /**
     * Connects a {@link QuicChannel} to the remote peer and notifies the future once done.
     */
    public Future<QuicChannel> connect() {
        return connect(parent.eventLoop().newPromise());
    }

    /**
     * Connects a {@link QuicChannel} to the remote peer and notifies the promise once done.
     */
    public Future<QuicChannel> connect(Promise<QuicChannel> promise) {
        if (streamHandler == null) {
            throw new IllegalStateException("streamHandler not set");
        }
        SocketAddress remote = this.remote;
        if (remote == null) {
            remote = parent.remoteAddress();
        }
        if (remote == null) {
            throw new IllegalStateException("remote not set");
        }
        final QuicConnectionAddress address = connectionAddress == null ?
                QuicConnectionAddress.random() : connectionAddress;

        QuicChannel channel = QuicheQuicChannel.forClient(parent, (InetSocketAddress) remote,
                streamHandler, Quic.optionsArray(streamOptions), Quic.attributesArray(streamAttrs));

        Quic.setupChannel(channel, Quic.optionsArray(options), Quic.attributesArray(attrs), handler, logger);
        EventLoop eventLoop = parent.eventLoop();
        eventLoop.register(channel).addListener((ChannelFuture future) -> {
            Throwable cause = future.cause();
            if (cause != null) {
                promise.setFailure(cause);
            } else {
                channel.connect(address).addListener(f -> {
                    Throwable error = f.cause();
                    if (error != null) {
                        promise.setFailure(error);
                    } else {
                        promise.setSuccess(channel);
                    }
                });
            }
        });
        return promise;
    }
}

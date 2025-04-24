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
import org.jetbrains.annotations.Nullable;

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

    private SocketAddress local;
    private SocketAddress remote;
    private QuicConnectionAddress connectionAddress = QuicConnectionAddress.EPHEMERAL;
    private ChannelHandler handler;
    private ChannelHandler streamHandler;

    /**
     * Creates a new instance which uses the given {@link Channel} to bootstrap the {@link QuicChannel}.
     * This {@link io.netty.channel.ChannelPipeline} of the {@link Channel} needs to have the quic codec in the
     * pipeline.
     *
     * @param parent    the {@link Channel} that is used as the transport layer.
     */
    QuicChannelBootstrap(Channel parent) {
        Quic.ensureAvailability();
        this.parent = ObjectUtil.checkNotNull(parent, "parent");
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
    public <T> QuicChannelBootstrap option(ChannelOption<T> option, @Nullable T value) {
        Quic.updateOptions(options, option, value);
        return this;
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
    public <T> QuicChannelBootstrap attr(AttributeKey<T> key, @Nullable T value) {
        Quic.updateAttributes(attrs, key, value);
        return this;
    }

    /**
     * Set the {@link ChannelHandler} that is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicChannel} once created.
     *
     * @param handler   the {@link ChannelHandler} that is added to the {@link QuicChannel}s
     *                  {@link io.netty.channel.ChannelPipeline}.
     * @return          this instance.
     */
    public QuicChannelBootstrap handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return this;
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
    public <T> QuicChannelBootstrap streamOption(ChannelOption<T> option, @Nullable T value) {
        Quic.updateOptions(streamOptions, option, value);
        return this;
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
    public <T> QuicChannelBootstrap streamAttr(AttributeKey<T> key, @Nullable T value) {
        Quic.updateAttributes(streamAttrs, key, value);
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
    public QuicChannelBootstrap streamHandler(ChannelHandler streamHandler) {
        this.streamHandler = ObjectUtil.checkNotNull(streamHandler, "streamHandler");
        return this;
    }

    /**
     * Set the local address.
     *
     * @param local    the {@link SocketAddress} of the local peer.
     * @return          this instance.
     */
    public QuicChannelBootstrap localAddress(SocketAddress local) {
        this.local = ObjectUtil.checkNotNull(local, "local");
        return this;
    }

    /**
     * Set the remote address of the host to talk to.
     *
     * @param remote    the {@link SocketAddress} of the remote peer.
     * @return          this instance.
     */
    public QuicChannelBootstrap remoteAddress(SocketAddress remote) {
        this.remote = ObjectUtil.checkNotNull(remote, "remote");
        return this;
    }

    /**
     * Set the {@link QuicConnectionAddress} to use. If none is specified a random address is generated on your
     * behalf.
     *
     * @param connectionAddress     the {@link QuicConnectionAddress} to use.
     * @return                      this instance.
     */
    public QuicChannelBootstrap connectionAddress(QuicConnectionAddress connectionAddress) {
        this.connectionAddress = ObjectUtil.checkNotNull(connectionAddress, "connectionAddress");
        return this;
    }

    /**
     * Connects a {@link QuicChannel} to the remote peer and notifies the future once done.
     *
     * @return {@link Future} which is notified once the operation completes.
     */
    public Future<QuicChannel> connect() {
        return connect(parent.eventLoop().newPromise());
    }

    /**
     * Connects a {@link QuicChannel} to the remote peer and notifies the promise once done.
     *
     * @param promise   the {@link Promise} which is notified once the operations completes.
     * @return          {@link Future} which is notified once the operation completes.

     */
    public Future<QuicChannel> connect(Promise<QuicChannel> promise) {
        if (handler == null && streamHandler == null) {
            throw new IllegalStateException("handler and streamHandler not set");
        }
        SocketAddress local = this.local;
        if (local == null) {
            local = parent.localAddress();
        }
        if (local == null) {
            local = new InetSocketAddress(0);
        }

        SocketAddress remote = this.remote;
        if (remote == null) {
            remote = parent.remoteAddress();
        }
        if (remote == null) {
            throw new IllegalStateException("remote not set");
        }

        final QuicConnectionAddress address = connectionAddress;
        QuicChannel channel = QuicheQuicChannel.forClient(parent, (InetSocketAddress)  local,
                (InetSocketAddress) remote,
                streamHandler, Quic.toOptionsArray(streamOptions), Quic.toAttributesArray(streamAttrs));

        Quic.setupChannel(channel, Quic.toOptionsArray(options), Quic.toAttributesArray(attrs), handler, logger);
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

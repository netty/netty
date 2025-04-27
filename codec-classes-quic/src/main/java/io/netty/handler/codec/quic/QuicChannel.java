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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLEngine;
import java.net.SocketAddress;

/**
 * A QUIC {@link Channel}.
 */
public interface QuicChannel extends Channel {

    @Override
    default ChannelFuture bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    default ChannelFuture disconnect() {
        return pipeline().disconnect();
    }

    @Override
    default ChannelFuture close() {
        return pipeline().close();
    }

    @Override
    default ChannelFuture deregister() {
        return pipeline().deregister();
    }

    @Override
    default ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().bind(localAddress, promise);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, promise);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, localAddress, promise);
    }

    @Override
    default ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline().disconnect(promise);
    }

    @Override
    default ChannelFuture close(ChannelPromise promise) {
        return pipeline().close(promise);
    }

    @Override
    default ChannelFuture deregister(ChannelPromise promise) {
        return pipeline().deregister(promise);
    }

    @Override
    default ChannelFuture write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    default ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline().write(msg, promise);
    }

    @Override
    default ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline().writeAndFlush(msg, promise);
    }

    @Override
    default ChannelFuture writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    default ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    default ChannelProgressivePromise newProgressivePromise() {
        return pipeline().newProgressivePromise();
    }

    @Override
    default ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    default ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    @Override
    default ChannelPromise voidPromise() {
        return pipeline().voidPromise();
    }

    @Override
    QuicChannel read();

    @Override
    QuicChannel flush();

    /**
     * Returns the configuration of this channel.
     */
    @Override
    QuicChannelConfig config();

    /**
     * Returns the used {@link SSLEngine} or {@code null} if none is used (yet).
     *
     * @return the engine.
     */
    @Nullable
    SSLEngine sslEngine();

    /**
     * Returns the number of streams that can be created before stream creation will fail
     * with {@link QuicTransportError#STREAM_LIMIT_ERROR} error.
     *
     * @param type the stream type.
     * @return the number of streams left.
     */
    long peerAllowedStreams(QuicStreamType type);

    /**
     * Returns {@code true} if the connection was closed because of idle timeout.
     *
     * @return {@code true} if the connection was closed because of idle timeout, {@code false}.
     */
    boolean isTimedOut();

    /**
     * Returns the {@link QuicTransportParameters} of the peer once received, or {@code null} if not known yet.
     *
     * @return peerTransportParams.
     */
    @Nullable
    QuicTransportParameters peerTransportParameters();

    /**
     * Returns the local {@link QuicConnectionAddress}. This address might change over the life-time of the
     * channel.
     *
     * @return  local   the local {@link QuicConnectionAddress} or {@code null} if none is assigned yet,
     *                  or assigned anymore.
     */
    @Override
    @Nullable
    QuicConnectionAddress localAddress();

    /**
     * Returns the remote {@link QuicConnectionAddress}. This address might change over the life-time of the
     * channel.
     *
     * @return  remote   the remote {@link QuicConnectionAddress} or {@code null} if none is assigned yet,
     *                   or assigned anymore.
     */
    @Override
    @Nullable
    QuicConnectionAddress remoteAddress();

    /**
     * Returns the local {@link SocketAddress} of the underlying transport that received the data.
     * This address might change over the life-time of the channel.
     *
     * @return  local   the local {@link SocketAddress} of the underlying transport or {@code null} if none is assigned
     *                  yet, or assigned anymore.
     */
    @Nullable
    SocketAddress localSocketAddress();

    /**
     * Returns the remote {@link SocketAddress} of the underlying transport to which the data is sent.
     * This address might change over the life-time of the channel.
     *
     * @return  local   the remote {@link SocketAddress} of the underlying transport or {@code null} if none is assigned
     *                  yet, or assigned anymore.
     */
    @Nullable
    SocketAddress remoteSocketAddress();

    /**
     * Creates a stream that is using this {@link QuicChannel} and notifies the {@link Future} once done.
     * The {@link ChannelHandler} (if not {@code null}) is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} automatically.
     *
     * @param type      the {@link QuicStreamType} of the {@link QuicStreamChannel}.
     * @param handler   the {@link ChannelHandler} that will be added to the {@link QuicStreamChannel}s
     *                  {@link io.netty.channel.ChannelPipeline} during the stream creation.
     * @return          the {@link Future} that will be notified once the operation completes.
     */
    default Future<QuicStreamChannel> createStream(QuicStreamType type, @Nullable ChannelHandler handler) {
        return createStream(type, handler, eventLoop().newPromise());
    }

    /**
     * Creates a stream that is using this {@link QuicChannel} and notifies the {@link Promise} once done.
     * The {@link ChannelHandler} (if not {@code null}) is added to the {@link io.netty.channel.ChannelPipeline} of the
     * {@link QuicStreamChannel} automatically.
     *
     * @param type      the {@link QuicStreamType} of the {@link QuicStreamChannel}.
     * @param handler   the {@link ChannelHandler} that will be added to the {@link QuicStreamChannel}s
     *                  {@link io.netty.channel.ChannelPipeline} during the stream creation.
     * @param promise   the {@link ChannelPromise} that will be notified once the operation completes.
     * @return          the {@link Future} that will be notified once the operation completes.
     */
    Future<QuicStreamChannel> createStream(QuicStreamType type, @Nullable ChannelHandler handler,
                                           Promise<QuicStreamChannel> promise);

    /**
     * Returns a new {@link QuicStreamChannelBootstrap} which makes it easy to bootstrap new {@link QuicStreamChannel}s
     * with custom options and attributes. For simpler use-cases you may want to consider using
     * {@link #createStream(QuicStreamType, ChannelHandler)} or
     * {@link #createStream(QuicStreamType, ChannelHandler, Promise)} directly.
     *
     * @return {@link QuicStreamChannelBootstrap} that can be used to bootstrap a {@link QuicStreamChannel}.
     */
    default QuicStreamChannelBootstrap newStreamBootstrap() {
        return new QuicStreamChannelBootstrap(this);
    }

    /**
     * Close the {@link QuicChannel}
     *
     * @param applicationClose  {@code true} if an application close should be used,
     *                          {@code false} if a normal close should be used.
     * @param error             the application error number, or {@code 0} if no special error should be signaled.
     * @param reason            the reason for the closure (which may be an empty {@link ByteBuf}.
     * @return                  the future that is notified.
     */
    default ChannelFuture close(boolean applicationClose, int error, ByteBuf reason) {
        return close(applicationClose, error, reason, newPromise());
    }

    /**
     * Close the {@link QuicChannel}
     *
     * @param applicationClose  {@code true} if an application close should be used,
     *                          {@code false} if a normal close should be used.
     * @param error             the application error number, or {@code 0} if no special error should be signaled.
     * @param reason            the reason for the closure (which may be an empty {@link ByteBuf}.
     * @param promise           the {@link ChannelPromise} that will be notified.
     * @return                  the future that is notified.
     */
    ChannelFuture close(boolean applicationClose, int error, ByteBuf reason, ChannelPromise promise);

    /**
     * Collects statistics about the connection and notifies the {@link Future} once done.
     *
     * @return the {@link Future} that is notified once the stats were collected.
     */
    default Future<QuicConnectionStats> collectStats() {
        return collectStats(eventLoop().newPromise());
    }

    /**
     * Collects statistics about the connection and notifies the {@link Promise} once done.
     *
     * @param   promise the {@link ChannelPromise} that is notified once the stats were collected.
     * @return          the {@link Future} that is notified once the stats were collected.
     */
    Future<QuicConnectionStats> collectStats(Promise<QuicConnectionStats> promise);

    /**
     * Collects statistics about the path of the connection and notifies the {@link Future} once done.
     *
     * @return the {@link Future} that is notified once the stats were collected.
     */
    default Future<QuicConnectionPathStats> collectPathStats(int pathIdx) {
        return collectPathStats(pathIdx, eventLoop().newPromise());
    }

    /**
     * Collects statistics about the path of the connection and notifies the {@link Promise} once done.
     *
     * @param   promise the {@link ChannelPromise} that is notified once the stats were collected.
     * @return          the {@link Future} that is notified once the stats were collected.
     */
    Future<QuicConnectionPathStats> collectPathStats(int pathIdx, Promise<QuicConnectionPathStats> promise);

    /**
     * Creates a new {@link QuicChannelBootstrap} that can be used to create and connect new {@link QuicChannel}s to
     * endpoints using the given {@link Channel} as transport layer.
     *
     * @param channel   the {@link Channel} that is used as transport layer.
     * @return          {@link QuicChannelBootstrap} that can be used to bootstrap a client side {@link QuicChannel}.
     */
    static QuicChannelBootstrap newBootstrap(Channel channel) {
        return new QuicChannelBootstrap(channel);
    }
}

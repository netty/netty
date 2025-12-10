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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelShutdownType;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;

/**
 * A QUIC stream {@link Channel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options supported by {@link Channel},
 * {@link QuicStreamChannel} allows the following options in the
 * option map via {@link io.netty.channel.ChannelOption}:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>ChannelOption</th>
 * </tr><tr>
 * <td>{@link QuicChannelOption#READ_FRAMES}</td>
 * </tr>
 * </table>
 */
public interface QuicStreamChannel extends Channel {

    /**
     * Should be added to a {@link ChannelFuture} when the output should be cleanly shutdown via a {@code FIN}. No more
     * writes will be allowed after this point.
     */
    ChannelFutureListener SHUTDOWN_OUTPUT = f ->
            f.channel().shutdown(ChannelShutdownType.newOutbound());

    @Override
    default ChannelFuture bind(SocketAddress socketAddress) {
        return pipeline().bind(socketAddress);
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
    default ChannelFuture bind(SocketAddress localAddress, ChannelPromise channelPromise) {
        return pipeline().bind(localAddress, channelPromise);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise channelPromise) {
        return pipeline().connect(remoteAddress, channelPromise);
    }

    @Override
    default ChannelFuture connect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise channelPromise) {
        return pipeline().connect(remoteAddress, localAddress, channelPromise);
    }

    @Override
    default ChannelFuture disconnect(ChannelPromise channelPromise) {
        return pipeline().disconnect(channelPromise);
    }

    @Override
    default ChannelFuture close(ChannelPromise channelPromise) {
        return pipeline().close(channelPromise);
    }

    @Override
    default ChannelFuture deregister(ChannelPromise channelPromise) {
        return pipeline().deregister(channelPromise);
    }

    @Override
    default ChannelFuture write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    default ChannelFuture write(Object msg, ChannelPromise channelPromise) {
        return pipeline().write(msg, channelPromise);
    }

    @Override
    default ChannelFuture writeAndFlush(Object msg, ChannelPromise channelPromise) {
        return pipeline().writeAndFlush(msg, channelPromise);
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
    default ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    default ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    @Override
    QuicStreamAddress localAddress();

    @Override
    QuicStreamAddress remoteAddress();

    /**
     * Returns {@code true} if the stream was created locally.
     *
     * @return {@code true} if created locally, {@code false} otherwise.
     */
    boolean isLocalCreated();

    /**
     * Returns the {@link QuicStreamType} of the stream.
     *
     * @return {@link QuicStreamType} of this stream.
     */
    QuicStreamType type();

    /**
     * The id of the stream.
     *
     * @return the stream id of this {@link QuicStreamChannel}.
     */
    long streamId();

    /**
     * The {@link QuicStreamPriority} if explicit set for the stream via {@link #updatePriority(QuicStreamPriority)} or
     * {@link #updatePriority(QuicStreamPriority, ChannelPromise)}. Otherwise {@code null}.
     *
     * @return the priority if any was set.
     */
    @Nullable
    QuicStreamPriority priority();

    /**
     * Update the priority of the stream. A stream's priority determines the order in which stream data is sent
     * on the wire (streams with lower priority are sent first).
     *
     * @param priority  the priority.
     * @return          future that is notified once the operation completes.
     */
    default ChannelFuture updatePriority(QuicStreamPriority priority) {
        return updatePriority(priority, newPromise());
    }

    /**
     * Update the priority of the stream. A stream's priority determines the order in which stream data is sent
     * on the wire (streams with lower priority are sent first).
     *
     * @param priority  the priority.
     * @param promise   notified once operations completes.
     * @return          future that is notified once the operation completes.
     */
    ChannelFuture updatePriority(QuicStreamPriority priority, ChannelPromise promise);

    @Override
    QuicChannel parent();

    @Override
    QuicStreamChannel read();

    @Override
    QuicStreamChannel flush();
}

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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;

/**
 * A QUIC stream.
 */
public interface QuicStreamChannel extends DuplexChannel {

    /**
     * Should be added to a {@link ChannelFuture} when the output should be cleanly shutdown via a {@code FIN}. No more
     * writes will be allowed after this point.
     */
    ChannelFutureListener SHUTDOWN_OUTPUT = f -> ((QuicStreamChannel) f.channel()).shutdownOutput();

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
    default ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    default ChannelFuture shutdownInput(ChannelPromise promise) {
        return shutdownInput(0, promise);
    }

    @Override
    default ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    default ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    /**
     * Shortcut for calling {@link #shutdownInput(int)} and {@link #shutdownInput(int)}.
     *
     * @param error the error to send.
     * @return the future that is notified on completion.
     */
    default ChannelFuture shutdown(int error) {
        return shutdown(error, newPromise());
    }

    /**
     * Shortcut for calling {@link #shutdownInput(int, ChannelPromise)} and {@link #shutdownInput(int, ChannelPromise)}.
     *
     * @param error the error to send.
     * @param promise will be notified on completion.
     * @return the future that is notified on completion.
     */
    ChannelFuture shutdown(int error, ChannelPromise promise);

    /**
     * Shutdown the input of the stream with the given error code. This means a {@code STOP_SENDING} frame will
     * be send to the remote peer and all data received will be discarded.
     *
     * @param error the error to send as part of the {@code STOP_SENDING} frame.
     * @return the future that is notified on completion.
     */
    default ChannelFuture shutdownInput(int error) {
        return shutdownInput(error, newPromise());
    }

    /**
     * Shutdown the input of the stream with the given error code. This means a {@code STOP_SENDING} frame will
     * be send to the remote peer and all data received will be discarded.
     *
     * @param error the error to send as part of the {@code STOP_SENDING} frame.
     * @param promise will be notified on completion.
     * @return the future that is notified on completion.
     */
    ChannelFuture shutdownInput(int error, ChannelPromise promise);

    /**
     * Shutdown the output of the stream with the given error code. This means a {@code RESET_STREAM} frame will
     * be send to the remote peer and all data that is not sent yet will be discarded.
     *
     * <strong>Important:</strong>If you want to shutdown the output without sending a {@code RESET_STREAM} frame you
     * should use {@link #shutdownOutput()} which will shutdown the output by sending a {@code FIN} and so signal
     * a clean shutdown.
     *
     * @param error the error to send as part of the {@code RESET_STREAM} frame.
     * @return the future that is notified on completion.
     */
    default ChannelFuture shutdownOutput(int error) {
        return shutdownOutput(error, newPromise());
    }

    /**
     * Shutdown the output of the stream with the given error code. This means a {@code RESET_STREAM} frame will
     * be send to the remote peer and all data that is not sent yet will be discarded.
     *
     * <strong>Important:</strong>If you want to shutdown the output without sending a {@code RESET_STREAM} frame you
     * should use {@link #shutdownOutput(ChannelPromise)} which will shutdown the output by sending a {@code FIN}
     * and so signal a clean shutdown.
     *
     * @param error the error to send as part of the {@code RESET_STREAM} frame.
     * @param promise will be notified on completion.
     * @return the future that is notified on completion.
     */
    ChannelFuture shutdownOutput(int error, ChannelPromise promise);

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

    @Override
    QuicStreamChannelConfig config();
}

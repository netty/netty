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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;

/**
 * A QUIC stream.
 */
public interface QuicStreamChannel extends DuplexChannel {

    /**
     * Should be added to a {@link ChannelFuture} when the {@code FIN} should be sent to the remote peer and no more
     * writes will happen.
     *
     * <strong>Important:</strong> The FIN will not be propagated through the {@link io.netty.channel.ChannelPipeline}
     * to make it easier to reuse this {@link ChannelFutureListener} with all kind of different handlers that sit on top
     * of {@code QUIC}. If you want to ensure the {@code FIN} is propagated through the
     * {@link io.netty.channel.ChannelPipeline} as a write just write a {@link QuicStreamFrame} directly with the
     * {@code FIN} set.
     */
    ChannelFutureListener WRITE_FIN = f -> {
        Unsafe unsafe = f.channel().unsafe();
        unsafe.write(QuicStreamFrame.EMPTY_FIN, unsafe.voidPromise());
        unsafe.flush();
    };

    /**
     * @deprecated use {@link #WRITE_FIN}
     */
    @Deprecated
    ChannelFutureListener SHUTDOWN_OUTPUT = WRITE_FIN;

    @Override
    default ChannelFuture shutdownInput() {
        return shutdownInput(0);
    }

    @Override
    default ChannelFuture shutdownInput(ChannelPromise promise) {
        return shutdownInput(0, promise);
    }

    @Override
    default ChannelFuture shutdownOutput() {
        return shutdownOutput(0);
    }

    @Override
    default ChannelFuture shutdownOutput(ChannelPromise promise) {
        return shutdownOutput(0, promise);
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
     * @param error the error to send.
     * @return the future that is notified on completion.
     */
    default ChannelFuture shutdownInput(int error) {
        return shutdownInput(error, newPromise());
    }

    /**
     * Shutdown the input of the stream with the given error code. This means a {@code STOP_SENDING} frame will
     * be send to the remote peer and all data received will be discarded.
     *
     * @param error the error to send.
     * @param promise will be notified on completion.
     * @return the future that is notified on completion.
     */
    ChannelFuture shutdownInput(int error, ChannelPromise promise);

    /**
     * Shutdown the output of the stream with the given error code. This means a {@code RESET_STREAM} frame will
     * be send to the remote peer and all data that is not sent yet will be discarded.
     *
     * @param error the error to send.
     * @return the future that is notified on completion.
     */
    default ChannelFuture shutdownOutput(int error) {
        return shutdownOutput(error, newPromise());
    }

    /**
     * Shutdown the output of the stream with the given error code. This means a {@code RESET_STREAM} frame will
     * be send to the remote peer and all data that is not sent yet will be discarded.
     *
     * @param error the error to send.
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

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
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;

/**
 * A QUIC stream.
 */
public interface QuicStreamChannel extends DuplexChannel {

    /**
     * Should be added to a {@link ChannelFuture} when the FIN should be sent to the remote peer and no more
     * writes will happen.
     */
    ChannelFutureListener SHUTDOWN_OUTPUT = f -> ((QuicStreamChannel) f.channel()).shutdownOutput();

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

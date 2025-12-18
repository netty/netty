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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.jetbrains.annotations.Nullable;

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
     * {@link #updatePriority(QuicStreamPriority, Promise)}. Otherwise {@code null}.
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
    default Future<Void> updatePriority(QuicStreamPriority priority) {
        Promise<Void> promise = newPromise();
        updatePriority(priority, promise);
        return promise;
    }

    /**
     * Update the priority of the stream. A stream's priority determines the order in which stream data is sent
     * on the wire (streams with lower priority are sent first).
     *
     * @param priority the priority.
     * @param promise  notified once operations completes.
     */
    void updatePriority(QuicStreamPriority priority, Promise<Void> promise);

    @Override
    QuicChannel parent();
}

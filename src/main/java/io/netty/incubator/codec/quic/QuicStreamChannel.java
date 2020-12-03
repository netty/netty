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
     */
    boolean isLocalCreated();

    /**
     * Returns the {@link QuicStreamType} of the stream.
     */
    QuicStreamType type();

    /**
     * The id of the stream.
     */
    long streamId();

    @Override
    QuicChannel parent();

    @Override
    QuicStreamChannelConfig config();
}

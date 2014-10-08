/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Manager for the life cycle of the HTTP/2 connection. Handles graceful shutdown of the channel,
 * closing only after all of the streams have closed.
 */
public interface Http2LifecycleManager {

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
     * hook to close the channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel.
     */
    void closeLocalSide(Http2Stream stream, ChannelFuture future);

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
     * hook to close the channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel.
     */
    void closeRemoteSide(Http2Stream stream, ChannelFuture future);

    /**
     * Closes the given stream and adds a hook to close the channel after the given future
     * completes.
     *
     * @param stream the stream to be closed.
     * @param future the future after which to close the channel.
     */
    void closeStream(Http2Stream stream, ChannelFuture future);

    /**
     * Writes a RST_STREAM frame to the remote endpoint and updates the connection state
     * appropriately.
     */
    ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise);

    /**
     * Sends a {@code GO_AWAY} frame to the remote endpoint and updates the connection state
     * appropriately.
     */
    ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData, ChannelPromise promise);

    /**
     * Processes the given exception.
     */
    void onException(ChannelHandlerContext ctx, Throwable cause);
}

/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
     * Closes the local side of the {@code stream}. Depending on the {@code stream} state this may result in
     * {@code stream} being closed. See {@link #closeStream(Http2Stream, ChannelFuture)}.
     * @param stream the stream to be half closed.
     * @param future See {@link #closeStream(Http2Stream, ChannelFuture)}.
     */
    void closeStreamLocal(Http2Stream stream, ChannelFuture future);

    /**
     * Closes the remote side of the {@code stream}. Depending on the {@code stream} state this may result in
     * {@code stream} being closed. See {@link #closeStream(Http2Stream, ChannelFuture)}.
     * @param stream the stream to be half closed.
     * @param future See {@link #closeStream(Http2Stream, ChannelFuture)}.
     */
    void closeStreamRemote(Http2Stream stream, ChannelFuture future);

    /**
     * Closes and deactivates the given {@code stream}. A listener is also attached to {@code future} and upon
     * completion the underlying channel will be closed if {@link Http2Connection#numActiveStreams()} is 0.
     * @param stream the stream to be closed and deactivated.
     * @param future when completed if {@link Http2Connection#numActiveStreams()} is 0 then the underlying channel
     * will be closed.
     */
    void closeStream(Http2Stream stream, ChannelFuture future);

    /**
     * Ensure the stream identified by {@code streamId} is reset. If our local state does not indicate the stream has
     * been reset yet then a {@code RST_STREAM} will be sent to the peer. If our local state indicates the stream
     * has already been reset then the return status will indicate success without sending anything to the peer.
     * @param ctx The context used for communication and buffer allocation if necessary.
     * @param streamId The identifier of the stream to reset.
     * @param errorCode Justification as to why this stream is being reset. See {@link Http2Error}.
     * @param promise Used to indicate the return status of this operation.
     * @return Will be considered successful when the connection and stream state has been updated, and a
     * {@code RST_STREAM} frame has been sent to the peer. If the stream state has already been updated and a
     * {@code RST_STREAM} frame has been sent then the return status may indicate success immediately.
     */
    ChannelFuture resetStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise);

    /**
     * Prevents the peer from creating streams and close the connection if {@code errorCode} is not
     * {@link Http2Error#NO_ERROR}. After this call the peer is not allowed to create any new streams and the local
     * endpoint will be limited to creating streams with {@code stream identifier <= lastStreamId}. This may result in
     * sending a {@code GO_AWAY} frame (assuming we have not already sent one with
     * {@code Last-Stream-ID <= lastStreamId}), or may just return success if a {@code GO_AWAY} has previously been
     * sent.
     * @param ctx The context used for communication and buffer allocation if necessary.
     * @param lastStreamId The last stream that the local endpoint is claiming it will accept.
     * @param errorCode The rational as to why the connection is being closed. See {@link Http2Error}.
     * @param debugData For diagnostic purposes (carries no semantic value).
     * @param promise Used to indicate the return status of this operation.
     * @return Will be considered successful when the connection and stream state has been updated, and a
     * {@code GO_AWAY} frame has been sent to the peer. If the stream state has already been updated and a
     * {@code GO_AWAY} frame has been sent then the return status may indicate success immediately.
     */
    ChannelFuture goAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData, ChannelPromise promise);

    /**
     * Processes the given error.
     *
     * @param ctx The context used for communication and buffer allocation if necessary.
     * @param outbound {@code true} if the error was caused by an outbound operation and so the corresponding
     * {@link ChannelPromise} was failed as well.
     * @param cause the error.
     */
    void onError(ChannelHandlerContext ctx, boolean outbound, Throwable cause);
}

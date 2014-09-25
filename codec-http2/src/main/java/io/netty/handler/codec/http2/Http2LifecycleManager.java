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

import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Manager for the life cycle of the HTTP/2 connection. Handles graceful shutdown of the channel,
 * closing only after all of the streams have closed.
 */
public class Http2LifecycleManager {

    private final Http2Connection connection;
    private final Http2FrameWriter frameWriter;
    private ChannelFutureListener closeListener;

    public Http2LifecycleManager(Http2Connection connection, Http2FrameWriter frameWriter) {
        this.connection = connection;
        this.frameWriter = frameWriter;
    }

    /**
     * Handles the close processing on behalf of the {@link DelegatingHttp2ConnectionHandler}.
     */
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        ChannelFuture future = writeGoAway(ctx, null);

        // If there are no active streams, close immediately after the send is complete.
        // Otherwise wait until all streams are inactive.
        if (connection.numActiveStreams() == 0) {
            future.addListener(new ClosingChannelFutureListener(ctx, promise));
        } else {
            closeListener = new ClosingChannelFutureListener(ctx, promise);
        }
    }

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
     * hook to close the channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel.
     */
    public void closeLocalSide(Http2Stream stream, ChannelFuture future) {
        switch (stream.state()) {
            case HALF_CLOSED_LOCAL:
            case OPEN:
                stream.closeLocalSide();
                break;
            default:
                closeStream(stream, future);
                break;
        }
    }

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
     * hook to close the channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel.
     */
    public void closeRemoteSide(Http2Stream stream, ChannelFuture future) {
        switch (stream.state()) {
            case HALF_CLOSED_REMOTE:
            case OPEN:
                stream.closeRemoteSide();
                break;
            default:
                closeStream(stream, future);
                break;
        }
    }

    /**
     * Closes the given stream and adds a hook to close the channel after the given future
     * completes.
     *
     * @param stream the stream to be closed.
     * @param future the future after which to close the channel.
     */
    public void closeStream(Http2Stream stream, ChannelFuture future) {
        stream.close();

        // If this connection is closing and there are no longer any
        // active streams, close after the current operation completes.
        if (closeListener != null && connection.numActiveStreams() == 0) {
            future.addListener(closeListener);
        }
    }

    /**
     * Processes the given exception. Depending on the type of exception, delegates to either
     * {@link #onConnectionError(ChannelHandlerContext, Http2Exception)} or
     * {@link #onStreamError(ChannelHandlerContext, Http2StreamException)}.
     */
    public void onHttp2Exception(ChannelHandlerContext ctx, Http2Exception e) {
        if (e instanceof Http2StreamException) {
            onStreamError(ctx, (Http2StreamException) e);
        } else {
            onConnectionError(ctx, e);
        }
    }

    /**
     * Handler for a connection error. Sends a GO_AWAY frame to the remote endpoint and waits until
     * all streams are closed before shutting down the connection.
     */
    private void onConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        writeGoAway(ctx, cause).addListener(new ClosingChannelFutureListener(ctx, ctx.newPromise()));
    }

    /**
     * Handler for a stream error. Sends a RST_STREAM frame to the remote endpoint and closes the stream.
     */
    private void onStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        writeRstStream(ctx, cause.streamId(), cause.error().code(), ctx.newPromise());
    }

    /**
     * Sends a GO_AWAY frame to the remote endpoint.
     */
    private ChannelFuture writeGoAway(ChannelHandlerContext ctx, Http2Exception cause) {
        if (connection.isGoAway()) {
            return ctx.newSucceededFuture();
        }

        // The connection isn't alredy going away, send the GO_AWAY frame now to start
        // the process.
        int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
        ByteBuf debugData = Http2CodecUtil.toByteBuf(ctx, cause);
        int lastKnownStream = connection.remote().lastStreamCreated();
        ChannelFuture sendFuture =
                frameWriter.writeGoAway(ctx, lastKnownStream, errorCode, debugData,
                        ctx.newPromise());
        ctx.flush();

        // Update the connection state and notify any listeners that this connection is going away.
        connection.remote().goAwayReceived(lastKnownStream);

        return sendFuture;
    }

    /**
     * Writes a RST_STREAM frame to the remote endpoint.
     */
    private ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        Http2Stream stream = connection.stream(streamId);
        ChannelFuture future = frameWriter.writeRstStream(ctx, streamId, errorCode, promise);
        ctx.flush();

        if (stream != null) {
            stream.terminateSent();
            closeStream(stream, promise);
        }

        return future;
    }
    /**
     * Closes the channel when the future completes.
     */
    private static final class ClosingChannelFutureListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;
        private final ChannelPromise promise;

        ClosingChannelFutureListener(ChannelHandlerContext ctx, ChannelPromise promise) {
            this.ctx = ctx;
            this.promise = promise;
        }

        @Override
        public void operationComplete(ChannelFuture sentGoAwayFuture) throws Exception {
            ctx.close(promise);
        }
    }
}

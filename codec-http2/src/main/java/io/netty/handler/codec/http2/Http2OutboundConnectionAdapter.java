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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.toByteBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.io.Closeable;
import java.util.ArrayDeque;

/**
 * Provides the ability to write HTTP/2 frames
 * <p>
 * This class provides write methods which turn java calls into HTTP/2 frames
 * <p>
 * This interface enforces outbound flow control functionality through {@link Http2OutboundFlowController}
 */
public class Http2OutboundConnectionAdapter implements Http2FrameWriter, Http2OutboundFlowController, Closeable {
    private final Http2FrameWriter frameWriter;
    private final Http2Connection connection;
    private final Http2OutboundFlowController outboundFlow;
    // We prefer ArrayDeque to LinkedList because later will produce more GC.
    // This initial capacity is plenty for SETTINGS traffic.
    private final ArrayDeque<Http2Settings> outstandingLocalSettingsQueue = new ArrayDeque<Http2Settings>(4);
    private ChannelFutureListener closeListener;

    public Http2OutboundConnectionAdapter(Http2Connection connection, Http2FrameWriter frameWriter) {
        this(connection, frameWriter, new DefaultHttp2OutboundFlowController(connection, frameWriter));
    }

    public Http2OutboundConnectionAdapter(Http2Connection connection, Http2FrameWriter frameWriter,
            Http2OutboundFlowController outboundFlow) {
        this.frameWriter = frameWriter;
        this.connection = connection;
        this.outboundFlow = outboundFlow;
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, ByteBuf data, int padding,
            final boolean endStream, ChannelPromise promise) {
        boolean release = true;
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending data after connection going away.");
            }

            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

            // Hand control of the frame to the flow controller.
            ChannelFuture future = outboundFlow.writeData(ctx, streamId, data, padding, endStream, promise);
            release = false;
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        // The write failed, handle the error.
                        onHttp2Exception(ctx, toHttp2Exception(future.cause()));
                    } else if (endStream) {
                        // Close the local side of the stream if this is the last frame
                        Http2Stream stream = connection.stream(streamId);
                        closeLocalSide(stream, ctx.newPromise());
                    }
                }
            });

            return future;
        } catch (Http2Exception e) {
            if (release) {
                data.release();
            }
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        return writeHeaders(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
            ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending headers after connection going away.");
            }

            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                // Create a new locally-initiated stream.
                stream = connection.createLocalStream(streamId, endStream);
            } else {
                // An existing stream...
                if (stream.state() == RESERVED_LOCAL) {
                    // Sending headers on a reserved push stream ... open it for push to the remote
                    // endpoint.
                    stream.openForPush();
                } else {
                    // The stream already exists, make sure it's in an allowed state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

                    // Update the priority for this stream only if we'll be sending more data.
                    if (!endStream) {
                        stream.setPriority(streamDependency, weight, exclusive);
                    }
                }
            }

            ChannelFuture future = frameWriter.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endStream, promise);
            ctx.flush();

            // If the headers are the end of the stream, close it now.
            if (endStream) {
                closeLocalSide(stream, promise);
            }

            return future;
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive, ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending priority after connection going away.");
            }

            // Update the priority on this stream.
            connection.requireStream(streamId).setPriority(streamDependency, weight, exclusive);

            ChannelFuture future = frameWriter.writePriority(ctx, streamId, streamDependency, weight, exclusive,
                    promise);
            ctx.flush();
            return future;
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        return writeRstStream(ctx, streamId, errorCode, promise, false);
    }

    /**
     * Writes a RST_STREAM frame to the remote endpoint.
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param errorCode the error code indicating the nature of the failure.
     * @param promise the promise for the write.
     * @param writeIfNoStream
     * <ul>
     * <li>{@code true} will force a write of a RST_STREAM even if the stream object does not exist locally.</li>
     * <li>{@code false} will only send a RST_STREAM only if the stream is known about locally</li>
     * </ul>
     * @return the future for the write.
     */
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise, boolean writeIfNoStream) {
        Http2Stream stream = connection.stream(streamId);
        if (stream == null && !writeIfNoStream) {
            // The stream may already have been closed ... ignore.
            promise.setSuccess();
            return promise;
        }

        ChannelFuture future = frameWriter.writeRstStream(ctx, streamId, errorCode, promise);
        ctx.flush();

        if (stream != null) {
            stream.terminateSent();
            connection.close(stream, promise, closeListener);
        }

        return future;
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings, ChannelPromise promise) {
        outstandingLocalSettingsQueue.add(settings);
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending settings after connection going away.");
            }

            Boolean pushEnabled = settings.pushEnabled();
            if (pushEnabled != null && connection.isServer()) {
                throw protocolError("Server sending SETTINGS frame with ENABLE_PUSH specified");
            }

            ChannelFuture future = frameWriter.writeSettings(ctx, settings, promise);
            ctx.flush();
            return future;
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        return frameWriter.writeSettingsAck(ctx, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, ByteBuf data, ChannelPromise promise) {
        boolean release = true;
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending ping after connection going away.");
            }

            frameWriter.writePing(ctx, ack, data, promise);
            release = false;
            ctx.flush();
            return promise;
        } catch (Http2Exception e) {
            if (release) {
                data.release();
            }
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding, ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending push promise after connection going away.");
            }

            // Reserve the promised stream.
            Http2Stream stream = connection.requireStream(streamId);
            connection.local().reservePushStream(promisedStreamId, stream);

            // Write the frame.
            frameWriter.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
            ctx.flush();
            return promise;
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    /**
     * Sends a GO_AWAY frame to the remote endpoint. Waits until all streams are closed before shutting down the
     * connection.
     * @param ctx the handler context
     * @param promise the promise used to create the close listener.
     * @param cause connection error that caused this GO_AWAY, or {@code null} if normal termination.
     */
    public void sendGoAway(ChannelHandlerContext ctx, ChannelPromise promise, Http2Exception cause) {
        ChannelFuture future = null;
        ChannelPromise closePromise = promise;
        if (!connection.isGoAway()) {
            int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
            ByteBuf debugData = toByteBuf(ctx, cause);

            future = writeGoAway(ctx, connection.remote().lastStreamCreated(), errorCode, debugData, promise);
            ctx.flush();
            closePromise = null;
        }

        closeListener = getOrCreateCloseListener(ctx, closePromise);

        // If there are no active streams, close immediately after the send is complete.
        // Otherwise wait until all streams are inactive.
        if (cause != null || connection.numActiveStreams() == 0) {
            if (future == null) {
                future = ctx.newSucceededFuture();
            }
            future.addListener(closeListener);
        }
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
            ChannelPromise promise) {
        connection.remote().goAwayReceived(lastStreamId);
        return frameWriter.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement,
            ChannelPromise promise) {
        return frameWriter.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload, ChannelPromise promise) {
        return frameWriter.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    /**
     * Processes the given exception. Depending on the type of exception, delegates to either
     * {@link #onConnectionError(ChannelHandlerContext, Http2Exception)} or
     * {@link #onStreamError(ChannelHandlerContext, Http2StreamException)}.
     */
    protected final void onHttp2Exception(ChannelHandlerContext ctx, Http2Exception e) {
        if (e instanceof Http2StreamException) {
            onStreamError(ctx, (Http2StreamException) e);
        } else {
            onConnectionError(ctx, e);
        }
    }

    /**
     * Handler for a stream error. Sends a RST_STREAM frame to the remote endpoint and closes the stream.
     */
    protected void onStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        writeRstStream(ctx, cause.streamId(), cause.error().code(), ctx.newPromise(), true);
    }

    /**
     * Handler for a connection error. Sends a GO_AWAY frame to the remote endpoint and waits until all streams are
     * closed before shutting down the connection.
     */
    protected void onConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        sendGoAway(ctx, ctx.newPromise(), cause);
    }

    /**
     * If not already created, creates a new listener for the given promise which, when complete, closes the connection
     * and frees any resources.
     */
    private ChannelFutureListener getOrCreateCloseListener(final ChannelHandlerContext ctx, ChannelPromise promise) {
        final ChannelPromise closePromise = promise == null ? ctx.newPromise() : promise;
        if (closeListener == null) {
            // If no promise was provided, create a new one.
            closeListener = new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    ctx.close(closePromise);
                    close();
                }
            };
        } else {
            closePromise.setSuccess();
        }

        return closeListener;
    }

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a hook to close the
     * channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel. If {@code null}, ignored.
     */
    private void closeLocalSide(Http2Stream stream, ChannelFuture future) {
        switch (stream.state()) {
        case HALF_CLOSED_LOCAL:
        case OPEN:
            stream.closeLocalSide();
            break;
        default:
            connection.close(stream, future, closeListener);
            break;
        }
    }

    @Override
    public void close() {
        frameWriter.close();
    }

    /**
     * Get the {@link Http2Settings} object on the top of the queue that has been sent but not ACKed.
     * This may return {@code null}.
     */
    public Http2Settings pollSettings() {
        return outstandingLocalSettingsQueue.poll();
    }

    @Override
    public Configuration configuration() {
        return frameWriter.configuration();
    }

    /**
     * Get the close listener associated with this object
     * @return
     */
    public ChannelFutureListener closeListener() {
        return closeListener;
    }

    @Override
    public void initialOutboundWindowSize(int newWindowSize) throws Http2Exception {
        outboundFlow.initialOutboundWindowSize(newWindowSize);
    }

    @Override
    public int initialOutboundWindowSize() {
        return outboundFlow.initialOutboundWindowSize();
    }

    @Override
    public void updateOutboundWindowSize(int streamId, int deltaWindowSize) throws Http2Exception {
        outboundFlow.updateOutboundWindowSize(streamId, deltaWindowSize);
    }
}

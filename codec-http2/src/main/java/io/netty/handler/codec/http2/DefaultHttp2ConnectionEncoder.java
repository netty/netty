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
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.ArrayDeque;

/**
 * Default implementation of {@link Http2ConnectionEncoder}.
 */
public class DefaultHttp2ConnectionEncoder implements Http2ConnectionEncoder {
    private final Http2FrameWriter frameWriter;
    private final Http2Connection connection;
    private final Http2OutboundFlowController outboundFlow;
    private final Http2LifecycleManager lifecycleManager;
    // We prefer ArrayDeque to LinkedList because later will produce more GC.
    // This initial capacity is plenty for SETTINGS traffic.
    private final ArrayDeque<Http2Settings> outstandingLocalSettingsQueue = new ArrayDeque<Http2Settings>(4);

    /**
     * Builder for new instances of {@link DefaultHttp2ConnectionEncoder}.
     */
    public static class Builder implements Http2ConnectionEncoder.Builder {
        protected Http2FrameWriter frameWriter;
        protected Http2Connection connection;
        protected Http2OutboundFlowController outboundFlow;
        protected Http2LifecycleManager lifecycleManager;

        @Override
        public Builder connection(
                Http2Connection connection) {
            this.connection = connection;
            return this;
        }

        @Override
        public Builder lifecycleManager(
                Http2LifecycleManager lifecycleManager) {
            this.lifecycleManager = lifecycleManager;
            return this;
        }

        @Override
        public Http2LifecycleManager lifecycleManager() {
            return lifecycleManager;
        }

        @Override
        public Builder frameWriter(
                Http2FrameWriter frameWriter) {
            this.frameWriter = frameWriter;
            return this;
        }

        @Override
        public Builder outboundFlow(
                Http2OutboundFlowController outboundFlow) {
            this.outboundFlow = outboundFlow;
            return this;
        }

        @Override
        public Http2ConnectionEncoder build() {
            return new DefaultHttp2ConnectionEncoder(this);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    protected DefaultHttp2ConnectionEncoder(Builder builder) {
        frameWriter = checkNotNull(builder.frameWriter, "frameWriter");
        connection = checkNotNull(builder.connection, "connection");
        outboundFlow = checkNotNull(builder.outboundFlow, "outboundFlow");
        lifecycleManager = checkNotNull(builder.lifecycleManager, "lifecycleManager");
    }

    @Override
    public Http2FrameWriter frameWriter() {
        return frameWriter;
    }

    @Override
    public Http2Connection connection() {
        return connection;
    }

    @Override
    public void remoteSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        Http2FrameWriter.Configuration config = configuration();
        Http2HeaderTable outboundHeaderTable = config.headerTable();
        Http2FrameSizePolicy outboundFrameSizePolicy = config.frameSizePolicy();
        if (pushEnabled != null) {
            if (!connection.isServer()) {
                throw connectionError(PROTOCOL_ERROR, "Client received SETTINGS frame with ENABLE_PUSH specified");
            }
            connection.remote().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            connection.local().maxStreams((int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE));
        }

        Long headerTableSize = settings.headerTableSize();
        if (headerTableSize != null) {
            outboundHeaderTable.maxHeaderTableSize((int) Math.min(headerTableSize, Integer.MAX_VALUE));
        }

        Integer maxHeaderListSize = settings.maxHeaderListSize();
        if (maxHeaderListSize != null) {
            outboundHeaderTable.maxHeaderListSize(maxHeaderListSize);
        }

        Integer maxFrameSize = settings.maxFrameSize();
        if (maxFrameSize != null) {
            outboundFrameSizePolicy.maxFrameSize(maxFrameSize);
        }

        Integer initialWindowSize = settings.initialWindowSize();
        if (initialWindowSize != null) {
            initialOutboundWindowSize(initialWindowSize);
        }
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, ByteBuf data, int padding,
            final boolean endOfStream, ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw new IllegalStateException("Sending data after connection going away.");
            }

            Http2Stream stream = connection.requireStream(streamId);
            if (stream.isResetSent()) {
                throw new IllegalStateException("Sending data after sending RST_STREAM.");
            }
            if (stream.isEndOfStreamSent()) {
                throw new IllegalStateException("Sending data after sending END_STREAM.");
            }

            // Verify that the stream is in the appropriate state for sending DATA frames.
            switch (stream.state()) {
                case OPEN:
                case HALF_CLOSED_REMOTE:
                    // Allowed sending DATA frames in these states.
                    break;
                default:
                    throw new IllegalStateException(String.format(
                            "Stream %d in unexpected state: %s", stream.id(), stream.state()));
            }

            if (endOfStream) {
                // Indicate that we have sent END_STREAM.
                stream.endOfStreamSent();
            }
        } catch (Throwable e) {
            data.release();
            return promise.setFailure(e);
        }

        // Hand control of the frame to the flow controller.
        ChannelFuture future =
                outboundFlow.writeData(ctx, streamId, data, padding, endOfStream, promise);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    // The write failed, handle the error.
                    lifecycleManager.onException(ctx, future.cause());
                } else if (endOfStream) {
                    // Close the local side of the stream if this is the last frame
                    Http2Stream stream = connection.stream(streamId);
                    lifecycleManager.closeLocalSide(stream, ctx.newPromise());
                }
            }
        });

        return future;
    }

    @Override
    public ChannelFuture lastWriteForStream(int streamId) {
        return outboundFlow.lastWriteForStream(streamId);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        return writeHeaders(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId,
            final Http2Headers headers, final int streamDependency, final short weight,
            final boolean exclusive, final int padding, final boolean endOfStream,
            final ChannelPromise promise) {
        Http2Stream stream = connection.stream(streamId);
        ChannelFuture lastDataWrite = lastWriteForStream(streamId);
        try {
            if (connection.isGoAway()) {
                throw connectionError(PROTOCOL_ERROR, "Sending headers after connection going away.");
            }

            if (stream == null) {
                // Create a new locally-initiated stream.
                stream = connection.createLocalStream(streamId, endOfStream);
            } else {
                if (stream.isResetSent()) {
                    throw new IllegalStateException("Sending headers after sending RST_STREAM.");
                }
                if (stream.isEndOfStreamSent()) {
                    throw new IllegalStateException("Sending headers after sending END_STREAM.");
                }

                // An existing stream...
                switch (stream.state()) {
                    case RESERVED_LOCAL:
                        // Sending headers on a reserved push stream ... open it for push to the remote endpoint.
                        stream.openForPush();
                        break;
                    case OPEN:
                    case HALF_CLOSED_REMOTE:
                        // Allowed sending headers in these states.
                        break;
                    default:
                        throw new IllegalStateException(String.format(
                                "Stream %d in unexpected state: %s", stream.id(), stream.state()));
                }
            }

            if (lastDataWrite != null && !endOfStream) {
                throw new IllegalStateException(
                        "Sending non-trailing headers after data has been sent for stream: "
                                + streamId);
            }
        } catch (Http2NoMoreStreamIdsException e) {
            lifecycleManager.onException(ctx, e);
            return promise.setFailure(e);
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        if (lastDataWrite == null) {
            // No previous DATA frames to keep in sync with, just send it now.
            return writeHeaders(ctx, stream, headers, streamDependency, weight, exclusive, padding,
                    endOfStream, promise);
        }

        // There were previous DATA frames sent.  We need to send the HEADERS only after the most
        // recent DATA frame to keep them in sync...

        // Only write the HEADERS frame after the previous DATA frame has been written.
        final Http2Stream theStream = stream;
        lastDataWrite.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    // The DATA write failed, also fail this write.
                    promise.setFailure(future.cause());
                    return;
                }

                // Perform the write.
                writeHeaders(ctx, theStream, headers, streamDependency, weight, exclusive, padding,
                        endOfStream, promise);
            }
        });

        return promise;
    }

    /**
     * Writes the given {@link Http2Headers} to the remote endpoint and updates stream state if appropriate.
     */
    private ChannelFuture writeHeaders(ChannelHandlerContext ctx, Http2Stream stream,
            Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endOfStream, ChannelPromise promise) {
        ChannelFuture future =
                frameWriter.writeHeaders(ctx, stream.id(), headers, streamDependency, weight,
                        exclusive, padding, endOfStream, promise);
        ctx.flush();

        // If the headers are the end of the stream, close it now.
        if (endOfStream) {
            stream.endOfStreamSent();
            lifecycleManager.closeLocalSide(stream, promise);
        }

        return future;
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive, ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw connectionError(PROTOCOL_ERROR, "Sending priority after connection going away.");
            }

            // Update the priority on this stream.
            connection.requireStream(streamId).setPriority(streamDependency, weight, exclusive);
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        ChannelFuture future =
                frameWriter.writePriority(ctx, streamId, streamDependency, weight, exclusive,
                        promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        // Delegate to the lifecycle manager for proper updating of connection state.
        return lifecycleManager.writeRstStream(ctx, streamId, errorCode, promise);
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
            stream.resetSent();
            lifecycleManager.closeStream(stream, promise);
        }

        return future;
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings,
            ChannelPromise promise) {
        outstandingLocalSettingsQueue.add(settings);
        try {
            if (connection.isGoAway()) {
                throw connectionError(PROTOCOL_ERROR, "Sending settings after connection going away.");
            }

            Boolean pushEnabled = settings.pushEnabled();
            if (pushEnabled != null && connection.isServer()) {
                throw connectionError(PROTOCOL_ERROR, "Server sending SETTINGS frame with ENABLE_PUSH specified");
            }
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        ChannelFuture future = frameWriter.writeSettings(ctx, settings, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        return frameWriter.writeSettingsAck(ctx, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, ByteBuf data,
            ChannelPromise promise) {
        if (connection.isGoAway()) {
            data.release();
            return promise.setFailure(connectionError(PROTOCOL_ERROR, "Sending ping after connection going away."));
        }

        ChannelFuture future = frameWriter.writePing(ctx, ack, data, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding, ChannelPromise promise) {
        try {
            if (connection.isGoAway()) {
                throw connectionError(PROTOCOL_ERROR, "Sending push promise after connection going away.");
            }

            // Reserve the promised stream.
            Http2Stream stream = connection.requireStream(streamId);
            connection.local().reservePushStream(promisedStreamId, stream);
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        // Write the frame.
        ChannelFuture future =
                frameWriter.writePushPromise(ctx, streamId, promisedStreamId, headers, padding,
                        promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
            ChannelPromise promise) {
        return lifecycleManager.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement,
            ChannelPromise promise) {
        if (streamId > 0) {
            Http2Stream stream = connection().stream(streamId);
            if (stream != null && stream.isResetSent()) {
                throw new IllegalStateException("Sending data after sending RST_STREAM.");
            }
        }
        return frameWriter.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload, ChannelPromise promise) {
        return frameWriter.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    @Override
    public void close() {
        frameWriter.close();
    }

    @Override
    public Http2Settings pollSentSettings() {
        return outstandingLocalSettingsQueue.poll();
    }

    @Override
    public Configuration configuration() {
        return frameWriter.configuration();
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

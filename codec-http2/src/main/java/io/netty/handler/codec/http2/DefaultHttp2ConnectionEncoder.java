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
import static io.netty.handler.codec.http2.Http2CodecUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
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

    public DefaultHttp2ConnectionEncoder(Http2Connection connection, Http2FrameWriter frameWriter,
            Http2OutboundFlowController outboundFlow, Http2LifecycleManager lifecycleManager) {
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        this.connection = checkNotNull(connection, "connection");
        this.outboundFlow = checkNotNull(outboundFlow, "outboundFlow");
        this.lifecycleManager = checkNotNull(lifecycleManager, "lifecycleManager");
    }

    @Override
    public void remoteSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        Http2FrameWriter.Configuration config = configuration();
        Http2HeaderTable outboundHeaderTable = config.headerTable();
        Http2FrameSizePolicy outboundFrameSizePolicy = config.frameSizePolicy();
        if (pushEnabled != null) {
            if (!connection.isServer()) {
                throw protocolError("Client received SETTINGS frame with ENABLE_PUSH specified");
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
                        lifecycleManager.onHttp2Exception(ctx, toHttp2Exception(future.cause()));
                    } else if (endStream) {
                        // Close the local side of the stream if this is the last frame
                        Http2Stream stream = connection.stream(streamId);
                        lifecycleManager.closeLocalSide(stream, ctx.newPromise());
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
                lifecycleManager.closeLocalSide(stream, promise);
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
        Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            // The stream may already have been closed ... ignore.
            promise.setSuccess();
            return promise;
        }

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
            stream.terminateSent();
            lifecycleManager.closeStream(stream, promise);
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

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
            ChannelPromise promise) {
        return lifecycleManager.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
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

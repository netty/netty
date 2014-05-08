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
import static io.netty.handler.codec.http2.Http2CodecUtil.failAndThrow;
import static io.netty.handler.codec.http2.Http2CodecUtil.toByteBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Abstract base class for a handler of HTTP/2 frames. Handles reading and writing of HTTP/2
 * frames as well as management of connection state and flow control for both inbound and outbound
 * data frames.
 * <p>
 * Subclasses need to implement the methods defined by the {@link Http2FrameObserver} interface for
 * receiving inbound frames. Outbound frames are sent via one of the {@code writeXXX} methods.
 * <p>
 * It should be noted that the initial SETTINGS frame is sent upon either activation or addition of
 * this handler to the pipeline. Subclasses overriding {@link #channelActive} or
 * {@link #handlerAdded} must call this class to write the initial SETTINGS frame to the remote
 * endpoint.
 */
public abstract class AbstractHttp2ConnectionHandler extends ByteToMessageDecoder implements
        Http2FrameObserver {

    private final Http2FrameObserver internalFrameObserver = new FrameReadObserver();
    private final Http2FrameReader frameReader;
    private final Http2FrameWriter frameWriter;
    private final Http2Connection connection;
    private final Http2InboundFlowController inboundFlow;
    private final Http2OutboundFlowController outboundFlow;
    private boolean initialSettingsSent;
    private boolean initialSettingsReceived;
    private ChannelHandlerContext ctx;
    private ChannelFutureListener closeListener;

    protected AbstractHttp2ConnectionHandler(boolean server) {
        this(server, false);
    }

    protected AbstractHttp2ConnectionHandler(boolean server, boolean allowCompression) {
        this(new DefaultHttp2Connection(server, allowCompression));
    }

    protected AbstractHttp2ConnectionHandler(Http2Connection connection) {
        this(connection, new DefaultHttp2FrameReader(connection.isServer()),
                new DefaultHttp2FrameWriter(connection.isServer()),
                new DefaultHttp2InboundFlowController(), new DefaultHttp2OutboundFlowController());
    }

    protected AbstractHttp2ConnectionHandler(Http2Connection connection,
            Http2FrameReader frameReader, Http2FrameWriter frameWriter,
            Http2InboundFlowController inboundFlow, Http2OutboundFlowController outboundFlow) {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (frameReader == null) {
            throw new NullPointerException("frameReader");
        }
        if (frameWriter == null) {
            throw new NullPointerException("frameWriter");
        }
        if (inboundFlow == null) {
            throw new NullPointerException("inboundFlow");
        }
        if (outboundFlow == null) {
            throw new NullPointerException("outboundFlow");
        }
        this.connection = connection;
        this.frameReader = frameReader;
        this.frameWriter = frameWriter;
        this.inboundFlow = inboundFlow;
        this.outboundFlow = outboundFlow;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // The channel just became active - send the initial settings frame to the remote
        // endpoint.
        sendInitialSettings(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // This handler was just added to the context. In case it was handled after
        // the connection became active, send the initial settings frame now.
        this.ctx = ctx;
        sendInitialSettings(ctx);
    }

    protected final ChannelHandlerContext ctx() {
        return ctx;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        sendGoAway(ctx, promise, null);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelFuture future = ctx.newSucceededFuture();
        for (Http2Stream stream : connection.activeStreams().toArray(new Http2Stream[0])) {
            close(stream, ctx, future);
        }
        super.channelInactive(ctx);
    }

    /**
     * Handles {@link Http2Exception} objects that were thrown from other handlers. Ignores all
     * other exceptions.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof Http2Exception) {
            processHttp2Exception(ctx, (Http2Exception) cause);
        }

        super.exceptionCaught(ctx, cause);
    }

    /**
     * Gets the local settings for this endpoint of the HTTP/2 connection.
     */
    public final Http2Settings settings() {
        Http2Settings settings = new Http2Settings();
        settings.allowCompressedData(connection.local().allowCompressedData());
        settings.initialWindowSize(inboundFlow.initialInboundWindowSize());
        settings.pushEnabled(connection.local().allowPushTo());
        settings.maxConcurrentStreams(connection.remote().maxStreams());
        settings.maxHeaderTableSize(frameReader.maxHeaderTableSize());
        return settings;
    }

    protected ChannelFuture writeData(final ChannelHandlerContext ctx,
            final ChannelPromise promise, int streamId, final ByteBuf data, int padding,
            boolean endStream, boolean endSegment, boolean compressed) throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending data after connection going away.");
            }

            if (!connection.remote().allowCompressedData() && compressed) {
                throw protocolError("compression is disallowed for remote endpoint.");
            }

            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

            // Hand control of the frame to the flow controller.
            outboundFlow.sendFlowControlled(streamId, data, padding, endStream, endSegment,
                    compressed, new FlowControlWriter(ctx, data, promise));

            return promise;
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        return writeHeaders(ctx, promise, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false,
                padding, endStream, endSegment);
    }

    protected ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int streamDependency, short weight,
            boolean exclusive, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending headers after connection going away.");
            }

            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                // Creates a new locally-initiated stream.
                stream = connection.local().createStream(streamId, endStream);

                // Allow bi-directional traffic.
                inboundFlow.addStream(streamId);
                if (!endStream) {
                    outboundFlow.addStream(streamId, streamDependency, weight, exclusive);
                }
            } else {
                // An existing stream...
                if (stream.state() == RESERVED_LOCAL) {
                    // Sending headers on a reserved push stream ... open it for push to the remote
                    // endpoint.
                    stream.openForPush();

                    // Allow outbound traffic only.
                    if (!endStream) {
                        outboundFlow.addStream(streamId, streamDependency, weight, exclusive);
                    }
                } else {
                    // The stream already exists, make sure it's in an allowed state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

                    // Update the priority for this stream only if we'll be sending more data.
                    if (!endStream) {
                        outboundFlow.updateStream(stream.id(), streamDependency, weight, exclusive);
                    }
                }

                // If the headers are the end of the stream, close it now.
                if (endStream) {
                    closeLocalSide(stream, ctx, promise);
                }
            }

            return frameWriter.writeHeaders(ctx, promise, streamId, headers, streamDependency,
                    weight, exclusive, padding, endStream, endSegment);
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int streamDependency, short weight, boolean exclusive)
            throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending priority after connection going away.");
            }

            // Update the priority on this stream.
            outboundFlow.updateStream(streamId, streamDependency, weight, exclusive);

            return frameWriter.writePriority(ctx, promise, streamId, streamDependency, weight,
                    exclusive);
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writeRstStream(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long errorCode) {
        Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            // The stream may already have been closed ... ignore.
            promise.setSuccess();
            return promise;
        }

        close(stream, ctx, promise);

        return frameWriter.writeRstStream(ctx, promise, streamId, errorCode);
    }

    protected ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings) throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending settings after connection going away.");
            }

            if (settings.hasAllowCompressedData()) {
                connection.local().allowCompressedData(settings.allowCompressedData());
            }

            if (settings.hasPushEnabled()) {
                connection.local().allowPushTo(settings.pushEnabled());
            }

            if (settings.hasMaxConcurrentStreams()) {
                connection.remote().maxStreams(settings.maxConcurrentStreams());
            }

            if (settings.hasMaxHeaderTableSize()) {
                frameReader.maxHeaderTableSize(settings.maxHeaderTableSize());
            }

            if (settings.hasInitialWindowSize()) {
                inboundFlow.initialInboundWindowSize(settings.initialWindowSize());
            }

            return frameWriter.writeSettings(ctx, promise, settings);
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise,
            ByteBuf data) throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending ping after connection going away.");
            }

            // Just pass the frame through.
            return frameWriter.writePing(ctx, promise, false, data);
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int promisedStreamId, Http2Headers headers, int padding)
            throws Http2Exception {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending push promise after connection going away.");
            }

            // Reserve the promised stream.
            Http2Stream stream = connection.requireStream(streamId);
            connection.local().reservePushStream(promisedStreamId, stream);

            // Write the frame.
            return frameWriter.writePushPromise(ctx, promise, streamId, promisedStreamId, headers,
                    padding);
        } catch (Http2Exception e) {
            throw failAndThrow(promise, e);
        }
    }

    protected ChannelFuture writeAltSvc(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long maxAge, int port, ByteBuf protocolId, String host, String origin)
            throws Http2Exception {
        return frameWriter.writeAltSvc(ctx, promise, streamId, maxAge, port, protocolId, host,
                        origin);
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        try {
            frameReader.readFrame(ctx, in, internalFrameObserver);
        } catch (Http2Exception e) {
            processHttp2Exception(ctx, e);
        }
    }

    /**
     * Processes the given exception. Depending on the type of exception, delegates to either
     * {@link #processConnectionError} or {@link #processStreamError}.
     */
    private void processHttp2Exception(ChannelHandlerContext ctx, Http2Exception e) {
        if (e instanceof Http2StreamException) {
            processStreamError(ctx, (Http2StreamException) e);
        } else {
            processConnectionError(ctx, e);
        }
    }

    private void processConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        sendGoAway(ctx, ctx.newPromise(), cause);
    }

    private void processStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        // Close the stream if it was open.
        int streamId = cause.streamId();
        Http2Stream stream = connection.stream(streamId);
        if (stream != null) {
            close(stream, ctx, null);
        }

        // Send the Rst frame to the remote endpoint.
        frameWriter.writeRstStream(ctx, ctx.newPromise(), streamId, cause.error().code());
    }

    private void sendGoAway(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Exception cause) {
        ChannelFuture future = null;
        ChannelPromise closePromise = promise;
        if (!connection.isGoAway()) {
            connection.goAwaySent();

            int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
            ByteBuf debugData = toByteBuf(ctx, cause);

            future = frameWriter.writeGoAway(ctx, promise, connection.remote().lastStreamCreated(),
                            errorCode, debugData);
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

    private ChannelFutureListener getOrCreateCloseListener(final ChannelHandlerContext ctx,
            ChannelPromise promise) {
        final ChannelPromise closePromise = promise == null? ctx.newPromise() : promise;
        if (closeListener == null) {
            // If no promise was provided, create a new one.
            closeListener = new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    ctx.close(closePromise);
                    freeResources();
                }
            };
        } else {
            closePromise.setSuccess();
        }

        return closeListener;
    }

    private void freeResources() {
        frameReader.close();
        frameWriter.close();
    }

    private void closeLocalSide(Http2Stream stream, ChannelHandlerContext ctx, ChannelFuture future) {
        switch (stream.state()) {
            case HALF_CLOSED_LOCAL:
            case OPEN:
                stream.closeLocalSide();
                outboundFlow.removeStream(stream.id());
                break;
            default:
                close(stream, ctx, future);
                break;
        }
    }

    private void closeRemoteSide(Http2Stream stream, ChannelHandlerContext ctx, ChannelFuture future) {
        switch (stream.state()) {
            case HALF_CLOSED_REMOTE:
            case OPEN:
                stream.closeRemoteSide();
                inboundFlow.removeStream(stream.id());
                break;
            default:
                close(stream, ctx, future);
                break;
        }
    }

    private void close(Http2Stream stream, ChannelHandlerContext ctx, ChannelFuture future) {
        stream.close();

        // Notify the flow controllers.
        inboundFlow.removeStream(stream.id());
        outboundFlow.removeStream(stream.id());

        // If this connection is closing and there are no longer any
        // active streams, close after the current operation completes.
        if (closeListener != null && connection.numActiveStreams() == 0) {
            future.addListener(closeListener);
        }
    }

    private void verifyInitialSettingsReceived() throws Http2Exception {
        if (!initialSettingsReceived) {
            throw protocolError("Received non-SETTINGS as first frame.");
        }
    }

    /**
     * Sends the initial settings frame upon establishment of the connection, if not already sent.
     */
    private void sendInitialSettings(final ChannelHandlerContext ctx) throws Http2Exception {
        if (!initialSettingsSent && ctx.channel().isActive()) {
            initialSettingsSent = true;
            frameWriter.writeSettings(ctx, ctx.newPromise(), settings()).addListener(
                    ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    /**
     * Handles all inbound frames from the network.
     */
    private final class FrameReadObserver implements Http2FrameObserver {

        @Override
        public void onDataRead(final ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream, boolean endOfSegment, boolean compressed) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (!connection.local().allowCompressedData() && compressed) {
                throw protocolError("compression is disallowed.");
            }

            // Check if we received a data frame for a stream which is half-closed
            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(STREAM_CLOSED, OPEN, HALF_CLOSED_LOCAL);

            // Apply flow control.
            inboundFlow.applyInboundFlowControl(streamId, data, padding, endOfStream, endOfSegment,
                    compressed, new Http2InboundFlowController.FrameWriter() {

                        @Override
                        public void writeFrame(int streamId, int windowSizeIncrement)
                                throws Http2Exception {
                            frameWriter.writeWindowUpdate(ctx, ctx.newPromise(), streamId,
                                    windowSizeIncrement);
                        }
                    });

            if (isInboundStreamAfterGoAway(streamId)) {
                return;
            }

            if (endOfStream) {
                closeRemoteSide(stream, ctx, ctx.newSucceededFuture());
            }

            AbstractHttp2ConnectionHandler.this.onDataRead(ctx, streamId, data, padding, endOfStream,
                    endOfSegment, compressed);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                int padding, boolean endStream, boolean endSegment) throws Http2Exception {
            onHeadersRead(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding,
                    endStream, endSegment);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                int streamDependency, short weight, boolean exclusive, int padding,
                boolean endStream, boolean endSegment) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                return;
            }

            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                // Create the new stream.
                connection.remote().createStream(streamId, endStream);

                // Allow bi-directional traffic.
                outboundFlow.addStream(streamId, streamDependency, weight, exclusive);
                if (!endStream) {
                    inboundFlow.addStream(streamId);
                }
            } else {
                if (stream.state() == RESERVED_REMOTE) {
                    // Received headers for a reserved push stream ... open it for push to the
                    // local endpoint.
                    stream.verifyState(PROTOCOL_ERROR, RESERVED_REMOTE);
                    stream.openForPush();

                    // Allow inbound traffic only.
                    if (!endStream) {
                        inboundFlow.addStream(streamId);
                    }
                } else {
                    // Receiving headers on an existing stream. Make sure the stream is in an
                    // allowed
                    // state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_LOCAL);

                    // Update the outbound priority if outbound traffic is allowed.
                    if (stream.state() == OPEN) {
                        outboundFlow.updateStream(streamId, streamDependency, weight, exclusive);
                    }
                }

                // If the headers completes this stream, close it.
                if (endStream) {
                    closeRemoteSide(stream, ctx, ctx.newSucceededFuture());
                }
            }

            AbstractHttp2ConnectionHandler.this.onHeadersRead(ctx, streamId, headers, streamDependency,
                    weight, exclusive, padding, endStream, endSegment);
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                short weight, boolean exclusive) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Set the priority for this stream on the flow controller.
            outboundFlow.updateStream(streamId, streamDependency, weight, exclusive);

            AbstractHttp2ConnectionHandler.this.onPriorityRead(ctx, streamId, streamDependency,
                    weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                // RstStream frames must be ignored for closed streams.
                return;
            }

            close(stream, ctx, ctx.newSucceededFuture());

            AbstractHttp2ConnectionHandler.this.onRstStreamRead(ctx, streamId, errorCode);
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            verifyInitialSettingsReceived();

            AbstractHttp2ConnectionHandler.this.onSettingsAckRead(ctx);
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                throws Http2Exception {
            if (settings.hasAllowCompressedData()) {
                connection.remote().allowCompressedData(settings.allowCompressedData());
            }

            if (settings.hasMaxConcurrentStreams()) {
                connection.local().maxStreams(settings.maxConcurrentStreams());
            }

            if (settings.hasPushEnabled()) {
                connection.remote().allowPushTo(settings.pushEnabled());
            }

            if (settings.hasMaxHeaderTableSize()) {
                frameWriter.maxHeaderTableSize(settings.maxHeaderTableSize());
            }

            if (settings.hasInitialWindowSize()) {
                outboundFlow.initialOutboundWindowSize(settings.initialWindowSize());
            }

            // Acknowledge receipt of the settings.
            frameWriter.writeSettingsAck(ctx, ctx.newPromise());

            // We've received at least one non-ack settings frame from the remote endpoint.
            initialSettingsReceived = true;

            AbstractHttp2ConnectionHandler.this.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyInitialSettingsReceived();

            // Send an ack back to the remote client.
            frameWriter.writePing(ctx, ctx.newPromise(), true, data);

            AbstractHttp2ConnectionHandler.this.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyInitialSettingsReceived();

            AbstractHttp2ConnectionHandler.this.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Reserve the push stream based with a priority based on the current stream's priority.
            Http2Stream parentStream = connection.requireStream(streamId);
            connection.remote().reservePushStream(promisedStreamId, parentStream);

            AbstractHttp2ConnectionHandler.this.onPushPromiseRead(ctx, streamId, promisedStreamId,
                    headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            // Don't allow any more connections to be created.
            connection.goAwayReceived();

            AbstractHttp2ConnectionHandler.this.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                int windowSizeIncrement) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            if (streamId > 0) {
                Http2Stream stream = connection.stream(streamId);
                if (stream == null) {
                    // Window Update frames must be ignored for closed streams.
                    return;
                }
                stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);
            }

            // Update the outbound flow controller.
            outboundFlow.updateOutboundWindowSize(streamId, windowSizeIncrement);

            AbstractHttp2ConnectionHandler.this.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onAltSvcRead(ChannelHandlerContext ctx, int streamId, long maxAge, int port,
                ByteBuf protocolId, String host, String origin) throws Http2Exception {
            AbstractHttp2ConnectionHandler.this.onAltSvcRead(ctx, streamId, maxAge, port,
                    protocolId, host, origin);
        }

        @Override
        public void onBlockedRead(ChannelHandlerContext ctx, int streamId) throws Http2Exception {
            verifyInitialSettingsReceived();

            if (isInboundStreamAfterGoAway(streamId)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            if (streamId > 0) {
                Http2Stream stream = connection.stream(streamId);
                if (stream == null) {
                    // Window Update frames must be ignored for closed streams.
                    return;
                }
                stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);
            }

            // Update the outbound flow controller.
            outboundFlow.setBlocked(streamId);

            AbstractHttp2ConnectionHandler.this.onBlockedRead(ctx, streamId);
        }

        /**
         * Determines whether or not the stream was created after we sent a go-away frame. Frames
         * from streams created after we sent a go-away should be ignored. Frames for the connection
         * stream ID (i.e. 0) will always be allowed.
         */
        private boolean isInboundStreamAfterGoAway(int streamId) {
            return connection.isGoAwaySent() && connection.remote().lastStreamCreated() <= streamId;
        }
    }

    /**
     * Controls the write for a single outbound DATA frame. This writer is passed to the outbound flow
     * controller, which may break the frame into chunks as dictated by the flow control window. If
     * the write of any chunk fails, the original promise fails as well. Success occurs after the last
     * chunk is written successfully.
     */
    private final class FlowControlWriter implements Http2OutboundFlowController.FrameWriter {
        private final ChannelHandlerContext ctx;
        private final ChannelPromise promise;
        private final List<ChannelPromise> promises;
        private int remaining;

        FlowControlWriter(ChannelHandlerContext ctx, ByteBuf data, ChannelPromise promise) {
            this.ctx = ctx;
            this.promise = promise;
            promises = new ArrayList<ChannelPromise>(
                    Arrays.asList(promise));
            remaining = data.readableBytes();
        }

        @Override
        public void writeFrame(int streamId, ByteBuf data, int padding,
                boolean endStream, boolean endSegment, boolean compressed) {
            try {
                if (promise.isDone()) {
                    // Most likely the write already failed. Just release the
                    // buffer.
                    data.release();
                    return;
                }

                remaining -= data.readableBytes();

                // The flow controller may split the write into chunks. Use a new
                // promise for intermediate writes.
                final ChannelPromise chunkPromise =
                        remaining == 0 ? promise : ctx.newPromise();

                // The original promise is already in the list, so don't add again.
                if (chunkPromise != promise) {
                    promises.add(chunkPromise);
                }

                // TODO: consider adding a flush() method to this interface. The
                // frameWriter flushes on each write which isn't optimal
                // for the case of the outbound flow controller, which sends a batch
                // of frames when the flow control window changes. We should let
                // the flow controller manually flush after all writes are.
                // complete.

                // Write the frame.
                ChannelFuture future =
                        frameWriter.writeData(ctx, chunkPromise, streamId, data,
                                padding, endStream, endSegment, compressed);

                // Close the connection on write failures that leave the outbound
                // flow
                // control window in a corrupt state.
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future)
                            throws Exception {
                        if (!future.isSuccess()) {
                            // If any of the chunk writes fail, also fail the
                            // original
                            // future that was returned to the caller.
                            failAllPromises(future.cause());
                            processHttp2Exception(ctx,
                                    toHttp2Exception(future.cause()));
                        }
                    }
                });
            } catch (Http2Exception e) {
                processHttp2Exception(ctx, e);
            }

            // Close the local side of the stream if this is the last frame
            if (endStream) {
                Http2Stream stream = connection.stream(streamId);
                closeLocalSide(stream, ctx, ctx.newPromise());
            }
        }

        @Override
        public void setFailure(Throwable cause) {
            failAllPromises(cause);
        }

        /**
         * Called when the write for any chunk fails. Fails all promises including
         * the one returned to the caller.
         */
        private void failAllPromises(Throwable cause) {
            for (ChannelPromise chunkPromise : promises) {
                if (!chunkPromise.isDone()) {
                    chunkPromise.setFailure(cause);
                }
            }
        }
    }
}

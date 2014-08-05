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
import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.toByteBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abstract base class for a handler of HTTP/2 frames. Handles reading and writing of HTTP/2 frames
 * as well as management of connection state and flow control for both inbound and outbound data
 * frames.
 * <p>
 * Subclasses need to implement the methods defined by the {@link Http2FrameObserver} interface for
 * receiving inbound frames. Outbound frames are sent via one of the {@code writeXXX} methods.
 * <p>
 * It should be noted that the connection preface is sent upon either activation or addition of this
 * handler to the pipeline. Subclasses overriding {@link #channelActive} or {@link #handlerAdded}
 * must call this class to write the preface to the remote endpoint.
 */
public abstract class AbstractHttp2ConnectionHandler extends ByteToMessageDecoder implements
        Http2FrameObserver {

    private final Http2FrameObserver internalFrameObserver = new FrameReadObserver();
    private final Http2FrameReader frameReader;
    private final Http2FrameWriter frameWriter;
    private final Http2Connection connection;
    private final Http2InboundFlowController inboundFlow;
    private final Http2OutboundFlowController outboundFlow;
    // We prefer ArrayDeque to LinkedList because later will produce more GC.
    // This initial capacity is plenty for SETTINGS traffic.
    private final ArrayDeque<Http2Settings> outstandingLocalSettingsQueue = new ArrayDeque<Http2Settings>(4);
    private ByteBuf clientPrefaceString;
    private boolean prefaceSent;
    private boolean prefaceReceived;
    private ChannelHandlerContext ctx;
    private ChannelFutureListener closeListener;

    protected AbstractHttp2ConnectionHandler(boolean server) {
        this(new DefaultHttp2Connection(server));
    }

    protected AbstractHttp2ConnectionHandler(Http2Connection connection) {
        this(connection, new DefaultHttp2FrameReader(), new DefaultHttp2FrameWriter(),
                new DefaultHttp2InboundFlowController(connection), new DefaultHttp2OutboundFlowController(connection));
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

        // Set the expected client preface string. Only servers should receive this.
        clientPrefaceString = connection.isServer()? connectionPrefaceBuf() : null;
    }

    /**
     * Handles the client-side (cleartext) upgrade from HTTP to HTTP/2. Reserves local stream 1 for
     * the HTTP/2 response.
     */
    public final void onHttpClientUpgrade() throws Http2Exception {
        if (connection.isServer()) {
            throw protocolError("Client-side HTTP upgrade requested for a server");
        }
        if (prefaceSent || prefaceReceived) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Create a local stream used for the HTTP cleartext upgrade.
        createLocalStream(HTTP_UPGRADE_STREAM_ID, true);
    }

    /**
     * Handles the server-side (cleartext) upgrade from HTTP to HTTP/2.
     *
     * @param settings the settings for the remote endpoint.
     */
    public final void onHttpServerUpgrade(Http2Settings settings)
            throws Http2Exception {
        if (!connection.isServer()) {
            throw protocolError("Server-side HTTP upgrade requested for a client");
        }
        if (prefaceSent || prefaceReceived) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Apply the settings but no ACK is necessary.
        applyRemoteSettings(settings);

        // Create a stream in the half-closed state.
        createRemoteStream(HTTP_UPGRADE_STREAM_ID, true);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // The channel just became active - send the connection preface to the remote
        // endpoint.
        sendPreface(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // This handler was just added to the context. In case it was handled after
        // the connection became active, send the connection preface now.
        this.ctx = ctx;
        sendPreface(ctx);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        // Free any resources associated with this handler.
        freeResources();
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
        final Collection<Http2Stream> streams = connection.activeStreams();
        for (Http2Stream s : streams.toArray(new Http2Stream[streams.size()])) {
            close(s, future);
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
            onHttp2Exception(ctx, (Http2Exception) cause);
        }

        super.exceptionCaught(ctx, cause);
    }

    /**
     * Gets the local settings for this endpoint of the HTTP/2 connection.
     */
    public final Http2Settings settings() {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(inboundFlow.initialInboundWindowSize());
        settings.maxConcurrentStreams(connection.remote().maxStreams());
        settings.headerTableSize(frameReader.maxHeaderTableSize());
        if (!connection.isServer()) {
            // Only set the pushEnabled flag if this is a client endpoint.
            settings.pushEnabled(connection.local().allowPushTo());
        }
        return settings;
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream, boolean endOfSegment) throws Http2Exception {
    }

    /**
     * This will never actually be called, so marked as final. All received headers frames will be
     * handled by
     * {@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean, boolean)}.
     */
    @Override
    public final void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream, boolean endSegment) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
            boolean endSegment) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
            throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
            throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData) throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
    }

    /**
     * Default implementation. Does nothing.
     */
    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload) {
    }

    protected final ChannelHandlerContext ctx() {
        return ctx;
    }

    protected final Http2Connection connection() {
        return connection;
    }

    /**
     * Gets the next stream ID that can be created by the local endpoint.
     */
    protected final int nextStreamId() {
        return connection.local().nextStreamId();
    }

    protected ChannelFuture writeData(final ChannelHandlerContext ctx,
            final ChannelPromise promise, int streamId, final ByteBuf data, int padding,
            boolean endStream, boolean endSegment) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending data after connection going away.");
            }

            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_REMOTE);

            // Hand control of the frame to the flow controller.
            outboundFlow.sendFlowControlled(streamId, data, padding, endStream, endSegment,
                    new FlowControlWriter(ctx, data, promise));

            return promise;
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    protected ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream, boolean endSegment) {
        return writeHeaders(ctx, promise, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false,
                padding, endStream, endSegment);
    }

    protected ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int streamDependency, short weight,
            boolean exclusive, int padding, boolean endStream, boolean endSegment) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending headers after connection going away.");
            }

            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                // Create a new locally-initiated stream.
                createLocalStream(streamId, endStream);
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

                // If the headers are the end of the stream, close it now.
                if (endStream) {
                    closeLocalSide(stream, promise);
                }
            }

            return frameWriter.writeHeaders(ctx, promise, streamId, headers, streamDependency,
                    weight, exclusive, padding, endStream, endSegment);
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    protected ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int streamDependency, short weight, boolean exclusive) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending priority after connection going away.");
            }

            // Update the priority on this stream.
            connection.requireStream(streamId).setPriority(streamDependency, weight, exclusive);

            return frameWriter.writePriority(ctx, promise, streamId, streamDependency, weight,
                    exclusive);
        } catch (Http2Exception e) {
            return promise.setFailure(e);
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

        stream.terminateSent();
        close(stream, promise);

        return frameWriter.writeRstStream(ctx, promise, streamId, errorCode);
    }

    protected ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings) {
        outstandingLocalSettingsQueue.add(settings);
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending settings after connection going away.");
            }

            Boolean pushEnabled = settings.pushEnabled();
            if (pushEnabled != null && connection.isServer()) {
                throw protocolError("Server sending SETTINGS frame with ENABLE_PUSH specified");
            }

            return frameWriter.writeSettings(ctx, promise, settings);
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    protected ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise,
            ByteBuf data) {
        try {
            if (connection.isGoAway()) {
                throw protocolError("Sending ping after connection going away.");
            }

            // Just pass the frame through.
            return frameWriter.writePing(ctx, promise, false, data);
        } catch (Http2Exception e) {
            return promise.setFailure(e);
        }
    }

    protected ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int promisedStreamId, Http2Headers headers, int padding) {
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
            return promise.setFailure(e);
        }
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        try {
            // Read the remaining of the client preface string if we haven't already.
            // If this is a client endpoint, always returns true.
            if (!readClientPrefaceString(ctx, in)) {
                // Still processing the client preface.
                return;
            }

            frameReader.readFrame(ctx, in, internalFrameObserver);
        } catch (Http2Exception e) {
            onHttp2Exception(ctx, e);
        }
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
     * Handler for a connection error. Sends a GO_AWAY frame to the remote endpoint and waits until
     * all streams are closed before shutting down the connection.
     */
    protected void onConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        sendGoAway(ctx, ctx.newPromise(), cause);
    }

    /**
     * Handler for a stream error. Sends a RST_STREAM frame to the remote endpoint and closes the stream.
     */
    protected void onStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        // Send the RST_STREAM frame to the remote endpoint.
        int streamId = cause.streamId();
        frameWriter.writeRstStream(ctx, ctx.newPromise(), streamId, cause.error().code());

        // Mark the stream as terminated and close it.
        Http2Stream stream = connection.stream(streamId);
        if (stream != null) {
            stream.terminateSent();
            close(stream, null);
        }
    }

    /**
     * Sends a GO_AWAY frame to the remote endpoint. Waits until all streams are closed before
     * shutting down the connection.
     *
     * @param ctx the handler context
     * @param promise the promise used to create the close listener.
     * @param cause connection error that caused this GO_AWAY, or {@code null} if normal
     *            termination.
     */
    protected final void sendGoAway(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Exception cause) {
        ChannelFuture future = null;
        ChannelPromise closePromise = promise;
        if (!connection.isGoAway()) {
            int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
            ByteBuf debugData = toByteBuf(ctx, cause);

            int lastKnownStream = connection.remote().lastStreamCreated();
            future = frameWriter.writeGoAway(ctx, promise, lastKnownStream, errorCode, debugData);
            closePromise = null;
            connection.remote().goAwayReceived(lastKnownStream);
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

    /**
     * If not already created, creates a new listener for the given promise which, when complete,
     * closes the connection and frees any resources.
     */
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

    /**
     * Frees any resources maintained by this handler.
     */
    private void freeResources() {
        frameReader.close();
        frameWriter.close();
        if (clientPrefaceString != null) {
            clientPrefaceString.release();
            clientPrefaceString = null;
        }
    }

    /**
     * Decodes the client connection preface string from the input buffer.
     *
     * @return {@code true} if processing of the client preface string is complete. Since client
     *         preface strings can only be received by servers, returns true immediately for client
     *         endpoints.
     */
    private boolean readClientPrefaceString(ChannelHandlerContext ctx, ByteBuf in) {
        if (clientPrefaceString == null) {
            return true;
        }

        int prefaceRemaining = clientPrefaceString.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceRemaining);

        // Read the portion of the input up to the length of the preface, if reached.
        ByteBuf sourceSlice = in.readSlice(bytesRead);

        // Read the same number of bytes from the preface buffer.
        ByteBuf prefaceSlice = clientPrefaceString.readSlice(bytesRead);

        // If the input so far doesn't match the preface, break the connection.
        if (bytesRead == 0 || !prefaceSlice.equals(sourceSlice)) {
            ctx.close();
            return false;
        }

        if (!clientPrefaceString.isReadable()) {
            // Entire preface has been read.
            clientPrefaceString.release();
            clientPrefaceString = null;
            return true;
        }
        return false;
    }

    /**
     * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
     * hook to close the channel after the given future completes.
     *
     * @param stream the stream to be half closed.
     * @param future If closing, the future after which to close the channel. If {@code null},
     *            ignored.
     */
    private void closeLocalSide(Http2Stream stream, ChannelFuture future) {
        switch (stream.state()) {
            case HALF_CLOSED_LOCAL:
            case OPEN:
                stream.closeLocalSide();
                break;
            default:
                close(stream, future);
                break;
        }
    }

    /**
     * Closes the given stream and adds a hook to close the channel after the given future completes.
     *
     * @param stream the stream to be closed.
     * @param future the future after which to close the channel. If {@code null}, ignored.
     */
    private void close(Http2Stream stream, ChannelFuture future) {
        stream.close();

        // If this connection is closing and there are no longer any
        // active streams, close after the current operation completes.
        if (closeListener != null && connection.numActiveStreams() == 0) {
            future.addListener(closeListener);
        }
    }

    /**
     * Sends the HTTP/2 connection preface upon establishment of the connection, if not already sent.
     */
    private void sendPreface(final ChannelHandlerContext ctx) {
        if (prefaceSent || !ctx.channel().isActive()) {
            return;
        }

        prefaceSent = true;

        if (!connection.isServer()) {
            // Clients must send the preface string as the first bytes on the connection.
            ctx.write(connectionPrefaceBuf()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        // Both client and server must send their initial settings.
        Http2Settings settings = settings();
        outstandingLocalSettingsQueue.add(settings);
        frameWriter.writeSettings(ctx, ctx.newPromise(), settings).addListener(
                ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    /**
     * Applies settings received from the remote endpoint.
     */
    private void applyRemoteSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        if (pushEnabled != null) {
            if (!connection.isServer()) {
                throw protocolError("Client received SETTINGS frame with ENABLE_PUSH specified");
            }
            connection.remote().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
            connection.local().maxStreams(value);
        }

        Long headerTableSize = settings.headerTableSize();
        if (headerTableSize != null) {
            frameWriter.maxHeaderTableSize(headerTableSize);
        }

        Integer initialWindowSize = settings.initialWindowSize();
        if (initialWindowSize != null) {
            outboundFlow.initialOutboundWindowSize(initialWindowSize);
        }
    }

    /**
     * Creates a new stream initiated by the local endpoint.
     */
    private Http2Stream createLocalStream(int streamId, boolean halfClosed) throws Http2Exception {
        return connection.local().createStream(streamId, halfClosed);
    }

    /**
     * Creates a new stream initiated by the remote endpoint.
     */
    private Http2Stream createRemoteStream(int streamId, boolean halfClosed) throws Http2Exception {
        return connection.remote().createStream(streamId, halfClosed);
    }

    /**
     * Handles all inbound frames from the network.
     */
    private final class FrameReadObserver implements Http2FrameObserver {

        @Override
        public void onDataRead(final ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream, boolean endOfSegment) throws Http2Exception {
            verifyPrefaceReceived();

            // Check if we received a data frame for a stream which is half-closed
            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(STREAM_CLOSED, OPEN, HALF_CLOSED_LOCAL);

            // Apply flow control.
            inboundFlow.applyInboundFlowControl(streamId, data, padding, endOfStream, endOfSegment,
                    new Http2InboundFlowController.FrameWriter() {
                        @Override
                        public void writeFrame(int streamId, int windowSizeIncrement)
                                throws Http2Exception {
                            frameWriter.writeWindowUpdate(ctx, ctx.newPromise(), streamId,
                                    windowSizeIncrement);
                        }
                    });

            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            if (endOfStream) {
                closeRemoteSide(stream, ctx.newSucceededFuture());
            }

            AbstractHttp2ConnectionHandler.this.onDataRead(ctx, streamId, data, padding, endOfStream,
                    endOfSegment);
        }

        /**
         * Verifies that the HTTP/2 connection preface has been received from the remote endpoint.
         */
        private void verifyPrefaceReceived() throws Http2Exception {
            if (!prefaceReceived) {
                throw protocolError("Received non-SETTINGS as first frame.");
            }
        }

        /**
         * Closes the remote side of the given stream. If this causes the stream to be closed, adds a
         * hook to close the channel after the given future completes.
         *
         * @param stream the stream to be half closed.
         * @param future If closing, the future after which to close the channel. If {@code null},
         *            ignored.
         */
        private void closeRemoteSide(Http2Stream stream, ChannelFuture future) {
            switch (stream.state()) {
                case HALF_CLOSED_REMOTE:
                case OPEN:
                    stream.closeRemoteSide();
                    break;
                default:
                    close(stream, future);
                    break;
            }
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
            verifyPrefaceReceived();

            Http2Stream stream = connection.stream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (connection.remote().isGoAwayReceived() || stream != null && shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            if (stream == null) {
                createRemoteStream(streamId, endStream);
            } else {
                if (stream.state() == RESERVED_REMOTE) {
                    // Received headers for a reserved push stream ... open it for push to the
                    // local endpoint.
                    stream.verifyState(PROTOCOL_ERROR, RESERVED_REMOTE);
                    stream.openForPush();
                } else {
                    // Receiving headers on an existing stream. Make sure the stream is in an
                    // allowed
                    // state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_LOCAL);

                    // Update the outbound priority if outbound traffic is allowed.
                    if (stream.state() == OPEN) {
                        stream.setPriority(streamDependency, weight, exclusive);
                    }
                }

                // If the headers completes this stream, close it.
                if (endStream) {
                    closeRemoteSide(stream, ctx.newSucceededFuture());
                }
            }

            AbstractHttp2ConnectionHandler.this.onHeadersRead(ctx, streamId, headers, streamDependency,
                    weight, exclusive, padding, endStream, endSegment);
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                short weight, boolean exclusive) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            stream.setPriority(streamDependency, weight, exclusive);

            AbstractHttp2ConnectionHandler.this.onPriorityRead(ctx, streamId, streamDependency,
                    weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED) {
                // RstStream frames must be ignored for closed streams.
                return;
            }

            stream.terminateReceived();
            close(stream, ctx.newSucceededFuture());

            AbstractHttp2ConnectionHandler.this.onRstStreamRead(ctx, streamId, errorCode);
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            verifyPrefaceReceived();
            // Apply oldest outstanding local settings here. This is a synchronization point
            // between endpoints.
            Http2Settings settings = outstandingLocalSettingsQueue.poll();

            if (settings != null) {
                applyLocalSettings(settings);
            }

            AbstractHttp2ConnectionHandler.this.onSettingsAckRead(ctx);
        }

        /**
         * Applies settings sent from the local endpoint.
         */
        private void applyLocalSettings(Http2Settings settings) throws Http2Exception {
            Boolean pushEnabled = settings.pushEnabled();
            if (pushEnabled != null) {
                if (connection.isServer()) {
                    throw protocolError("Server sending SETTINGS frame with ENABLE_PUSH specified");
                }
                connection.local().allowPushTo(pushEnabled);
            }

            Long maxConcurrentStreams = settings.maxConcurrentStreams();
            if (maxConcurrentStreams != null) {
                int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
                connection.remote().maxStreams(value);
            }

            Long headerTableSize = settings.headerTableSize();
            if (headerTableSize != null) {
                frameReader.maxHeaderTableSize(headerTableSize);
            }

            Integer initialWindowSize = settings.initialWindowSize();
            if (initialWindowSize != null) {
                inboundFlow.initialInboundWindowSize(initialWindowSize);
            }
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                throws Http2Exception {
            applyRemoteSettings(settings);

            // Acknowledge receipt of the settings.
            frameWriter.writeSettingsAck(ctx, ctx.newPromise());

            // We've received at least one non-ack settings frame from the remote endpoint.
            prefaceReceived = true;

            AbstractHttp2ConnectionHandler.this.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            // Send an ack back to the remote client.
            // Need to retain the buffer here since it will be released after the write completes.
            frameWriter.writePing(ctx, ctx.newPromise(), true, data.retain());

            AbstractHttp2ConnectionHandler.this.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            AbstractHttp2ConnectionHandler.this.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream parentStream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(parentStream);
            if (shouldIgnoreFrame(parentStream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Reserve the push stream based with a priority based on the current stream's priority.
            connection.remote().reservePushStream(promisedStreamId, parentStream);

            AbstractHttp2ConnectionHandler.this.onPushPromiseRead(ctx, streamId, promisedStreamId,
                    headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            // Don't allow any more connections to be created.
            connection.local().goAwayReceived(lastStreamId);

            AbstractHttp2ConnectionHandler.this.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                int windowSizeIncrement) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Update the outbound flow controller.
            outboundFlow.updateOutboundWindowSize(streamId, windowSizeIncrement);

            AbstractHttp2ConnectionHandler.this.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) {
            AbstractHttp2ConnectionHandler.this.onUnknownFrame(ctx, frameType, streamId, flags, payload);
        }

        /**
         * Indicates whether or not frames for the given stream should be ignored based on the state
         * of the stream/connection.
         */
        private boolean shouldIgnoreFrame(Http2Stream stream) {
            if (connection.remote().isGoAwayReceived() && connection.remote().lastStreamCreated() <= stream.id()) {
                // Frames from streams created after we sent a go-away should be ignored.
                // Frames for the connection stream ID (i.e. 0) will always be allowed.
                return true;
            }

            // Also ignore inbound frames after we sent a RST_STREAM frame.
            return stream.isTerminateSent();
        }

        /**
         * Verifies that a GO_AWAY frame was not previously received from the remote endpoint. If it
         * was, throws an exception.
         */
        private void verifyGoAwayNotReceived() throws Http2Exception {
            if (connection.local().isGoAwayReceived()) {
                throw protocolError("Received frames after receiving GO_AWAY");
            }
        }

        /**
         * Verifies that a RST_STREAM frame was not previously received for the given stream. If it
         * was, throws an exception.
         */
        private void verifyRstStreamNotReceived(Http2Stream stream) throws Http2Exception {
            if (stream != null && stream.isTerminateReceived()) {
                throw new Http2StreamException(stream.id(), STREAM_CLOSED,
                        "Frame received after receiving RST_STREAM for stream: " + stream.id());
            }
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
            promises = new ArrayList<ChannelPromise>(4);
            promises.add(promise);
            remaining = data.readableBytes();
        }

        @Override
        public void writeFrame(int streamId, ByteBuf data, int padding,
                boolean endStream, boolean endSegment) {
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
                    frameWriter.writeData(ctx, chunkPromise, streamId, data, padding, endStream,
                            endSegment);

            // Close the connection on write failures that leave the outbound
            // flow control window in a corrupt state.
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future)
                        throws Exception {
                    if (!future.isSuccess()) {
                        // If any of the chunk writes fail, also fail the
                        // original
                        // future that was returned to the caller.
                        failAllPromises(future.cause());
                        onHttp2Exception(ctx,
                                toHttp2Exception(future.cause()));
                    }
                }
            });

            // Close the local side of the stream if this is the last frame
            if (endStream) {
                Http2Stream stream = connection.stream(streamId);
                closeLocalSide(stream, ctx.newPromise());
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

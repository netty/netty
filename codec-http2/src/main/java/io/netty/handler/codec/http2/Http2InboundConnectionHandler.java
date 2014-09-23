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
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.Collection;
import java.util.List;

/**
 * Provides the default implementation for processing inbound frame events
 * and delegates to a {@link Http2FrameListener}
 * <p>
 * This class will read HTTP/2 frames and delegate the events to a {@link Http2FrameListener}
 * <p>
 * This interface enforces inbound flow control functionality through {@link Http2InboundFlowController}
 */
public class Http2InboundConnectionHandler extends ByteToMessageDecoder {
    private final Http2FrameListener internalFrameListener = new FrameReadListener();
    protected final Http2OutboundConnectionAdapter outbound;
    private final Http2FrameListener listener;
    private final Http2FrameReader frameReader;
    protected final Http2Connection connection;
    private final Http2InboundFlowController inboundFlow;
    private ByteBuf clientPrefaceString;
    private boolean prefaceSent;
    private boolean prefaceReceived;

    public Http2InboundConnectionHandler(Http2Connection connection, Http2FrameListener listener,
            Http2FrameReader frameReader, Http2InboundFlowController inboundFlow,
            Http2OutboundConnectionAdapter outbound) {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (frameReader == null) {
            throw new NullPointerException("frameReader");
        }
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        if (inboundFlow == null) {
            throw new NullPointerException("inboundFlow");
        }
        if (outbound == null) {
            throw new NullPointerException("outbound");
        }

        this.connection = connection;
        this.frameReader = frameReader;
        this.listener = listener;
        this.outbound = outbound;
        this.inboundFlow = inboundFlow;

        // Set the expected client preface string. Only servers should receive this.
        clientPrefaceString = connection.isServer() ? connectionPrefaceBuf() : null;
    }

    /**
     * Handles the client-side (cleartext) upgrade from HTTP to HTTP/2.
     * Reserves local stream 1 for the HTTP/2 response.
     */
    public void onHttpClientUpgrade() throws Http2Exception {
        if (connection.isServer()) {
            throw protocolError("Client-side HTTP upgrade requested for a server");
        }
        if (prefaceSent || prefaceReceived) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Create a local stream used for the HTTP cleartext upgrade.
        connection.createLocalStream(HTTP_UPGRADE_STREAM_ID, true);
    }

    /**
     * Handles the server-side (cleartext) upgrade from HTTP to HTTP/2.
     * @param settings the settings for the remote endpoint.
     */
    public void onHttpServerUpgrade(Http2Settings settings) throws Http2Exception {
        if (!connection.isServer()) {
            throw protocolError("Server-side HTTP upgrade requested for a client");
        }
        if (prefaceSent || prefaceReceived) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Apply the settings but no ACK is necessary.
        applyRemoteSettings(settings);

        // Create a stream in the half-closed state.
        connection.createRemoteStream(HTTP_UPGRADE_STREAM_ID, true);
    }

    /**
     * Gets the local settings for this endpoint of the HTTP/2 connection.
     */
    public Http2Settings settings() {
        Http2Settings settings = new Http2Settings();
        final Http2FrameReader.Configuration config = frameReader.configuration();
        final Http2HeaderTable headerTable = config.headerTable();
        final Http2FrameSizePolicy frameSizePolicy = config.frameSizePolicy();
        settings.initialWindowSize(inboundFlow.initialInboundWindowSize());
        settings.maxConcurrentStreams(connection.remote().maxStreams());
        settings.headerTableSize(headerTable.maxHeaderTableSize());
        settings.maxFrameSize(frameSizePolicy.maxFrameSize());
        settings.maxHeaderListSize(headerTable.maxHeaderListSize());
        if (!connection.isServer()) {
            // Only set the pushEnabled flag if this is a client endpoint.
            settings.pushEnabled(connection.local().allowPushTo());
        }
        return settings;
    }

    /**
     * Closes all closable resources and frees any allocated resources.
     * <p>
     * This does NOT close the {@link Http2OutboundConnectionAdapter} reference in this class
     */
    public void close() {
        frameReader.close();
        if (clientPrefaceString != null) {
            clientPrefaceString.release();
            clientPrefaceString = null;
        }
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
        sendPreface(ctx);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        close();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        outbound.sendGoAway(ctx, promise, null);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelFuture future = ctx.newSucceededFuture();
        final Collection<Http2Stream> streams = connection.activeStreams();
        for (Http2Stream s : streams.toArray(new Http2Stream[streams.size()])) {
            connection.close(s, future, outbound.closeListener());
        }
        super.channelInactive(ctx);
    }

    /**
     * Handles {@link Http2Exception} objects that were thrown from other handlers. Ignores all other exceptions.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof Http2Exception) {
            onHttp2Exception(ctx, (Http2Exception) cause);
        }

        super.exceptionCaught(ctx, cause);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            // Read the remaining of the client preface string if we haven't already.
            // If this is a client endpoint, always returns true.
            if (!readClientPrefaceString(ctx, in)) {
                // Still processing the client preface.
                return;
            }

            frameReader.readFrame(ctx, in, internalFrameListener);
        } catch (Http2Exception e) {
            onHttp2Exception(ctx, e);
        } catch (Throwable e) {
            onHttp2Exception(ctx, new Http2Exception(Http2Error.INTERNAL_ERROR, e.getMessage(), e));
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
     * Handler for a connection error. Sends a GO_AWAY frame to the remote endpoint and waits until all streams are
     * closed before shutting down the connection.
     */
    protected void onConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        outbound.sendGoAway(ctx, ctx.newPromise(), cause);
    }

    /**
     * Handler for a stream error. Sends a RST_STREAM frame to the remote endpoint and closes the stream.
     */
    protected void onStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        outbound.writeRstStream(ctx, cause.streamId(), cause.error().code(), ctx.newPromise(), true);
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
        outbound.writeSettings(ctx, settings(), ctx.newPromise()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    /**
     * Applies settings received from the remote endpoint.
     */
    private void applyRemoteSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        final Http2FrameWriter.Configuration config = outbound.configuration();
        final Http2HeaderTable headerTable = config.headerTable();
        final Http2FrameSizePolicy frameSizePolicy = config.frameSizePolicy();
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
            headerTable.maxHeaderTableSize((int) Math.min(headerTableSize.intValue(), Integer.MAX_VALUE));
        }

        Integer maxHeaderListSize = settings.maxHeaderListSize();
        if (maxHeaderListSize != null) {
            headerTable.maxHeaderListSize(maxHeaderListSize);
        }

        Integer maxFrameSize = settings.maxFrameSize();
        if (maxFrameSize != null) {
            frameSizePolicy.maxFrameSize(maxFrameSize);
        }

        Integer initialWindowSize = settings.initialWindowSize();
        if (initialWindowSize != null) {
            outbound.initialOutboundWindowSize(initialWindowSize);
        }
    }

    /**
     * Decodes the client connection preface string from the input buffer.
     *
     * @return {@code true} if processing of the client preface string is complete. Since client preface strings can
     *         only be received by servers, returns true immediately for client endpoints.
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
     * Handles all inbound frames from the network.
     */
    private final class FrameReadListener implements Http2FrameListener {

        @Override
        public void onDataRead(final ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream) throws Http2Exception {
            verifyPrefaceReceived();

            // Check if we received a data frame for a stream which is half-closed
            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(STREAM_CLOSED, OPEN, HALF_CLOSED_LOCAL);

            // Apply flow control.
            inboundFlow.onDataRead(ctx, streamId, data, padding, endOfStream);

            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            listener.onDataRead(ctx, streamId, data, padding, endOfStream);

            if (endOfStream) {
                closeRemoteSide(stream, ctx.newSucceededFuture());
            }
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
         * Closes the remote side of the given stream. If this causes the stream to be closed, adds a hook to close the
         * channel after the given future completes.
         *
         * @param stream the stream to be half closed.
         * @param future If closing, the future after which to close the channel. If {@code null}, ignored.
         */
        private void closeRemoteSide(Http2Stream stream, ChannelFuture future) {
            switch (stream.state()) {
            case HALF_CLOSED_REMOTE:
            case OPEN:
                stream.closeRemoteSide();
                break;
            default:
                connection.close(stream, future, outbound.closeListener());
                break;
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endStream) throws Http2Exception {
            onHeadersRead(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.stream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (connection.remote().isGoAwayReceived() || stream != null && shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            if (stream == null) {
                stream = connection.createRemoteStream(streamId, endStream);
            } else {
                if (stream.state() == RESERVED_REMOTE) {
                    // Received headers for a reserved push stream ... open it for push to the local endpoint.
                    stream.verifyState(PROTOCOL_ERROR, RESERVED_REMOTE);
                    stream.openForPush();
                } else {
                    // Receiving headers on an existing stream. Make sure the stream is in an allowed state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_LOCAL);
                }
            }

            listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);

            stream.setPriority(streamDependency, weight, exclusive);

            // If the headers completes this stream, close it.
            if (endStream) {
                closeRemoteSide(stream, ctx.newSucceededFuture());
            }
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                boolean exclusive) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);

            stream.setPriority(streamDependency, weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED) {
                // RstStream frames must be ignored for closed streams.
                return;
            }

            stream.terminateReceived();

            listener.onRstStreamRead(ctx, streamId, errorCode);

            connection.close(stream, ctx.newSucceededFuture(), outbound.closeListener());
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            verifyPrefaceReceived();
            // Apply oldest outstanding local settings here. This is a synchronization point
            // between endpoints.
            Http2Settings settings = outbound.pollSettings();

            if (settings != null) {
                applyLocalSettings(settings);
            }

            listener.onSettingsAckRead(ctx);
        }

        /**
         * Applies settings sent from the local endpoint.
         */
        private void applyLocalSettings(Http2Settings settings) throws Http2Exception {
            Boolean pushEnabled = settings.pushEnabled();
            final Http2FrameReader.Configuration config = frameReader.configuration();
            final Http2HeaderTable headerTable = config.headerTable();
            final Http2FrameSizePolicy frameSizePolicy = config.frameSizePolicy();
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
                headerTable.maxHeaderTableSize((int) Math.min(headerTableSize, Integer.MAX_VALUE));
            }

            Integer maxHeaderListSize = settings.maxHeaderListSize();
            if (maxHeaderListSize != null) {
                headerTable.maxHeaderListSize(maxHeaderListSize);
            }

            Integer maxFrameSize = settings.maxFrameSize();
            if (maxFrameSize != null) {
                frameSizePolicy.maxFrameSize(maxFrameSize);
            }

            Integer initialWindowSize = settings.initialWindowSize();
            if (initialWindowSize != null) {
                inboundFlow.initialInboundWindowSize(initialWindowSize);
            }
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            applyRemoteSettings(settings);

            // Acknowledge receipt of the settings.
            outbound.writeSettingsAck(ctx, ctx.newPromise());
            ctx.flush();

            // We've received at least one non-ack settings frame from the remote endpoint.
            prefaceReceived = true;

            listener.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            // Send an ack back to the remote client.
            // Need to retain the buffer here since it will be released after the write completes.
            outbound.writePing(ctx, true, data.retain(), ctx.newPromise());
            ctx.flush();

            listener.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            listener.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                Http2Headers headers, int padding) throws Http2Exception {
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

            listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            // Don't allow any more connections to be created.
            connection.local().goAwayReceived(lastStreamId);

            listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Update the outbound flow controller.
            outbound.updateOutboundWindowSize(streamId, windowSizeIncrement);

            listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) {
            listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
        }

        /**
         * Indicates whether or not frames for the given stream should be ignored based on the state of the
         * stream/connection.
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
         * Verifies that a GO_AWAY frame was not previously received from the remote endpoint. If it was, throws an
         * exception.
         */
        private void verifyGoAwayNotReceived() throws Http2Exception {
            if (connection.local().isGoAwayReceived()) {
                throw protocolError("Received frames after receiving GO_AWAY");
            }
        }

        /**
         * Verifies that a RST_STREAM frame was not previously received for the given stream. If it was, throws an
         * exception.
         */
        private void verifyRstStreamNotReceived(Http2Stream stream) throws Http2Exception {
            if (stream != null && stream.isTerminateReceived()) {
                throw new Http2StreamException(stream.id(), STREAM_CLOSED,
                        "Frame received after receiving RST_STREAM for stream: " + stream.id());
            }
        }
    }
}

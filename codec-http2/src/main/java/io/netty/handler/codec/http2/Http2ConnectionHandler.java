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

import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
import static io.netty.handler.codec.http2.Http2CodecUtil.toHttp2Exception;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
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
public class Http2ConnectionHandler extends ByteToMessageDecoder implements Http2LifecycleManager {
    private final Http2ConnectionDecoder decoder;
    private final Http2ConnectionEncoder encoder;
    private ByteBuf clientPrefaceString;
    private boolean prefaceSent;
    private ChannelFutureListener closeListener;

    public Http2ConnectionHandler(boolean server, Http2FrameListener listener) {
        this(new DefaultHttp2Connection(server), listener);
    }

    public Http2ConnectionHandler(Http2Connection connection, Http2FrameListener listener) {
        this(connection, new DefaultHttp2FrameReader(), new DefaultHttp2FrameWriter(), listener);
    }

    public Http2ConnectionHandler(Http2Connection connection, Http2FrameReader frameReader,
            Http2FrameWriter frameWriter, Http2FrameListener listener) {
        this(connection, frameReader, frameWriter, new DefaultHttp2InboundFlowController(
                connection, frameWriter), new DefaultHttp2OutboundFlowController(connection,
                frameWriter), listener);
    }

    public Http2ConnectionHandler(Http2Connection connection, Http2FrameReader frameReader,
            Http2FrameWriter frameWriter, Http2InboundFlowController inboundFlow,
            Http2OutboundFlowController outboundFlow, Http2FrameListener listener) {
        this.encoder =
                DefaultHttp2ConnectionEncoder.newBuilder().connection(connection)
                        .frameWriter(frameWriter).outboundFlow(outboundFlow).lifecycleManager(this)
                        .build();
        this.decoder =
                DefaultHttp2ConnectionDecoder.newBuilder().connection(connection)
                        .frameReader(frameReader).inboundFlow(inboundFlow).encoder(encoder)
                        .listener(listener).lifecycleManager(this).build();
        clientPrefaceString = clientPrefaceString(connection);
    }

    /**
     * Constructor for pre-configured encoder and decoder builders. Just sets the {@code this} as the
     * {@link Http2LifecycleManager} and builds them.
     */
    public Http2ConnectionHandler(Http2ConnectionDecoder.Builder decoderBuilder,
            Http2ConnectionEncoder.Builder encoderBuilder) {
        checkNotNull(decoderBuilder, "decoderBuilder");
        checkNotNull(encoderBuilder, "encoderBuilder");
        decoderBuilder.lifecycleManager(this);
        encoderBuilder.lifecycleManager(this);

        this.encoder = checkNotNull(encoderBuilder.build(), "encoder");
        this.decoder = checkNotNull(decoderBuilder.build(), "decoder");
        checkNotNull(encoder.connection(), "encoder.connection");
        checkNotNull(decoder.connection(), "decoder.connection");
        if (encoder.connection() != decoder.connection()) {
            throw new IllegalArgumentException("Encoder and Decoder do not share the same connection object");
        }

        clientPrefaceString = clientPrefaceString(encoder.connection());
    }

    public Http2Connection connection() {
        return encoder.connection();
    }

    public Http2ConnectionDecoder decoder() {
        return decoder;
    }

    public Http2ConnectionEncoder encoder() {
        return encoder;
    }

    /**
     * Handles the client-side (cleartext) upgrade from HTTP to HTTP/2.
     * Reserves local stream 1 for the HTTP/2 response.
     */
    public void onHttpClientUpgrade() throws Http2Exception {
        if (connection().isServer()) {
            throw protocolError("Client-side HTTP upgrade requested for a server");
        }
        if (prefaceSent || decoder.prefaceReceived()) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Create a local stream used for the HTTP cleartext upgrade.
        connection().createLocalStream(HTTP_UPGRADE_STREAM_ID, true);
    }

    /**
     * Handles the server-side (cleartext) upgrade from HTTP to HTTP/2.
     * @param settings the settings for the remote endpoint.
     */
    public void onHttpServerUpgrade(Http2Settings settings) throws Http2Exception {
        if (!connection().isServer()) {
            throw protocolError("Server-side HTTP upgrade requested for a client");
        }
        if (prefaceSent || decoder.prefaceReceived()) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Apply the settings but no ACK is necessary.
        encoder.remoteSettings(settings);

        // Create a stream in the half-closed state.
        connection().createRemoteStream(HTTP_UPGRADE_STREAM_ID, true);
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
        dispose();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
     // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        ChannelFuture future = writeGoAway(ctx, null);

        // If there are no active streams, close immediately after the send is complete.
        // Otherwise wait until all streams are inactive.
        if (connection().numActiveStreams() == 0) {
            future.addListener(new ClosingChannelFutureListener(ctx, promise));
        } else {
            closeListener = new ClosingChannelFutureListener(ctx, promise);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelFuture future = ctx.newSucceededFuture();
        final Collection<Http2Stream> streams = connection().activeStreams();
        for (Http2Stream s : streams.toArray(new Http2Stream[streams.size()])) {
            closeStream(s, future);
        }
        super.channelInactive(ctx);
    }

    /**
     * Handles {@link Http2Exception} objects that were thrown from other handlers. Ignores all other exceptions.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (getEmbeddedHttp2Exception(cause) != null) {
            // Some exception in the causality chain is an Http2Exception - handle it.
            onException(ctx, cause);
        } else {
            super.exceptionCaught(ctx, cause);
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
    @Override
    public void closeStream(Http2Stream stream, ChannelFuture future) {
        stream.close();

        // If this connection is closing and there are no longer any
        // active streams, close after the current operation completes.
        if (closeListener != null && connection().numActiveStreams() == 0) {
            future.addListener(closeListener);
        }
    }

    /**
     * Central handler for all exceptions caught during HTTP/2 processing.
     */
    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        Http2Exception hCause = toHttp2Exception(cause);
        if (hCause instanceof Http2StreamException) {
            onStreamError(ctx, (Http2StreamException) hCause);
        } else {
            onConnectionError(ctx, hCause);
        }
    }

    /**
     * Handler for a connection error. Sends a GO_AWAY frame to the remote endpoint and waits until
     * all streams are closed before shutting down the connection().
     */
    protected void onConnectionError(ChannelHandlerContext ctx, Http2Exception cause) {
        writeGoAway(ctx, cause).addListener(new ClosingChannelFutureListener(ctx, ctx.newPromise()));
    }

    /**
     * Handler for a stream error. Sends a RST_STREAM frame to the remote endpoint and closes the stream.
     */
    protected void onStreamError(ChannelHandlerContext ctx, Http2StreamException cause) {
        writeRstStream(ctx, cause.streamId(), cause.error().code(), ctx.newPromise());
    }

    protected Http2FrameWriter frameWriter() {
        return encoder().frameWriter();
    }

    /**
     * Writes a RST_STREAM frame to the remote endpoint and updates the connection state appropriately.
     */
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        Http2Stream stream = connection().stream(streamId);
        ChannelFuture future = frameWriter().writeRstStream(ctx, streamId, errorCode, promise);
        ctx.flush();

        if (stream != null) {
            stream.terminateSent();
            closeStream(stream, promise);
        }

        return future;
    }

    /**
     * Sends a {@code GO_AWAY} frame to the remote endpoint and updates the connection state appropriately.
     */
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
            ChannelPromise promise) {
        if (connection().isGoAway()) {
            debugData.release();
            return ctx.newSucceededFuture();
        }

        ChannelFuture future = frameWriter().writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
        ctx.flush();

        connection().remote().goAwayReceived(lastStreamId);
        return future;
    }

    /**
     * Sends a GO_AWAY frame appropriate for the given exception.
     */
    private ChannelFuture writeGoAway(ChannelHandlerContext ctx, Http2Exception cause) {
        if (connection().isGoAway()) {
            return ctx.newSucceededFuture();
        }

        // The connection isn't alredy going away, send the GO_AWAY frame now to start
        // the process.
        int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
        ByteBuf debugData = Http2CodecUtil.toByteBuf(ctx, cause);
        int lastKnownStream = connection().remote().lastStreamCreated();
        return writeGoAway(ctx, lastKnownStream, errorCode, debugData, ctx.newPromise());
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

            decoder.decodeFrame(ctx, in, out);
        } catch (Throwable e) {
            onException(ctx, e);
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

        if (!connection().isServer()) {
            // Clients must send the preface string as the first bytes on the connection.
            ctx.write(connectionPrefaceBuf()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        // Both client and server must send their initial settings.
        encoder.writeSettings(ctx, decoder.localSettings(), ctx.newPromise()).addListener(
                ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    /**
     * Disposes of all resources.
     */
    private void dispose() {
        encoder.close();
        decoder.close();
        if (clientPrefaceString != null) {
            clientPrefaceString.release();
            clientPrefaceString = null;
        }
    }

    /**
     * Decodes the client connection preface string from the input buffer.
     *
     * @return {@code true} if processing of the client preface string is complete. Since client preface strings can
     *         only be received by servers, returns true immediately for client endpoints.
     */
    private boolean readClientPrefaceString(ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
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
            throw protocolError("HTTP/2 client preface string missing or corrupt.");
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
     * Returns the client preface string if this is a client connection, otherwise returns {@code null}.
     */
    private static ByteBuf clientPrefaceString(Http2Connection connection) {
        return connection.isServer() ? connectionPrefaceBuf() : null;
    }

    /**
     * Manager for the life cycle of the HTTP/2 connection. Handles graceful shutdown of the channel,
     * closing only after all of the streams have closed.
     */
    public class DefaultHttp2LifecycleManager {
        private ChannelFutureListener closeListener;

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
            if (connection().numActiveStreams() == 0) {
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
            if (closeListener != null && connection().numActiveStreams() == 0) {
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
         * Writes a RST_STREAM frame to the remote endpoint and updates the connection state appropriately.
         */
        public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
                ChannelPromise promise) {
            Http2Stream stream = connection().stream(streamId);
            ChannelFuture future = frameWriter().writeRstStream(ctx, streamId, errorCode, promise);
            ctx.flush();

            if (stream != null) {
                stream.terminateSent();
                closeStream(stream, promise);
            }

            return future;
        }

        /**
         * Sends a {@code GO_AWAY} frame to the remote endpoint and updates the connection state appropriately.
         */
        public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
                ChannelPromise promise) {
            if (connection().isGoAway()) {
                debugData.release();
                return ctx.newSucceededFuture();
            }

            ChannelFuture future = frameWriter().writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
            ctx.flush();

            connection().remote().goAwayReceived(lastStreamId);
            return future;
        }

        /**
         * Sends a GO_AWAY frame appropriate for the given exception.
         */
        private ChannelFuture writeGoAway(ChannelHandlerContext ctx, Http2Exception cause) {
            if (connection().isGoAway()) {
                return ctx.newSucceededFuture();
            }

            // The connection isn't alredy going away, send the GO_AWAY frame now to start
            // the process.
            int errorCode = cause != null ? cause.error().code() : NO_ERROR.code();
            ByteBuf debugData = Http2CodecUtil.toByteBuf(ctx, cause);
            int lastKnownStream = connection().remote().lastStreamCreated();
            return writeGoAway(ctx, lastKnownStream, errorCode, debugData, ctx.newPromise());
        }
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

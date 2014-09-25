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
import static io.netty.handler.codec.http2.Http2CodecUtil.checkNotNull;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
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
public class Http2ConnectionHandler extends ByteToMessageDecoder {
    private final Http2LifecycleManager lifecycleManager;
    private final Http2ConnectionDecoder decoder;
    private final Http2ConnectionEncoder encoder;
    private final Http2Connection connection;
    private ByteBuf clientPrefaceString;
    private boolean prefaceSent;

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
        checkNotNull(frameWriter, "frameWriter");
        checkNotNull(inboundFlow, "inboundFlow");
        checkNotNull(outboundFlow, "outboundFlow");
        checkNotNull(listener, "listener");
        this.connection = checkNotNull(connection, "connection");
        this.lifecycleManager = new Http2LifecycleManager(connection, frameWriter);
        this.encoder =
                new DefaultHttp2ConnectionEncoder(connection, frameWriter, outboundFlow,
                        lifecycleManager);
        this.decoder =
                new DefaultHttp2ConnectionDecoder(connection, frameReader, inboundFlow, encoder,
                        lifecycleManager, listener);
        clientPrefaceString = clientPrefaceString(connection);
    }

    public Http2ConnectionHandler(Http2Connection connection, Http2ConnectionDecoder decoder,
            Http2ConnectionEncoder encoder, Http2LifecycleManager lifecycleManager) {
        this.connection = checkNotNull(connection, "connection");
        this.lifecycleManager = checkNotNull(lifecycleManager, "lifecycleManager");
        this.encoder = checkNotNull(encoder, "encoder");
        this.decoder = checkNotNull(decoder, "decoder");
        clientPrefaceString = clientPrefaceString(connection);
    }

    public Http2Connection connection() {
        return connection;
    }

    public Http2LifecycleManager lifecycleManager() {
        return lifecycleManager;
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
        if (connection.isServer()) {
            throw protocolError("Client-side HTTP upgrade requested for a server");
        }
        if (prefaceSent || decoder.prefaceReceived()) {
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
        if (prefaceSent || decoder.prefaceReceived()) {
            throw protocolError("HTTP upgrade must occur before HTTP/2 preface is sent or received");
        }

        // Apply the settings but no ACK is necessary.
        encoder.remoteSettings(settings);

        // Create a stream in the half-closed state.
        connection.createRemoteStream(HTTP_UPGRADE_STREAM_ID, true);
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
        lifecycleManager.close(ctx, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelFuture future = ctx.newSucceededFuture();
        final Collection<Http2Stream> streams = connection.activeStreams();
        for (Http2Stream s : streams.toArray(new Http2Stream[streams.size()])) {
            lifecycleManager.closeStream(s, future);
        }
        super.channelInactive(ctx);
    }

    /**
     * Handles {@link Http2Exception} objects that were thrown from other handlers. Ignores all other exceptions.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Http2Exception ex = getEmbeddedHttp2Exception(cause);
        if (ex != null) {
            lifecycleManager.onHttp2Exception(ctx, ex);
        } else {
            super.exceptionCaught(ctx, cause);
        }
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
        } catch (Http2Exception e) {
            lifecycleManager.onHttp2Exception(ctx, e);
        } catch (Throwable e) {
            lifecycleManager.onHttp2Exception(ctx, new Http2Exception(Http2Error.INTERNAL_ERROR, e.getMessage(), e));
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
}

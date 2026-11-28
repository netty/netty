/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.UnstableApi;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Opens a WebSocket on one {@link Http2StreamChannel} using
 * <a href="https://www.rfc-editor.org/rfc/rfc8441">RFC 8441</a>. Instances can be shared between streams.
 * The parent pipeline must use {@link Http2FrameCodec} with {@link Http2MultiplexHandler}.
 * Both operations must be called on the child channel's event loop. They install the frame codecs immediately
 * before the supplied application context. After success that context can read and write {@link WebSocketFrame}s.
 *
 * <p>A server advertises support with {@link Http2Settings#connectProtocolEnabled(boolean)}.
 * Use {@link #isWebSocketRequest(Http2Headers)} in the existing request handler, then check the resource,
 * Origin, and any credentials before calling {@link #accept}. Other streams keep their HTTP/2 pipelines.
 * A client should wait for an {@link Http2SettingsFrame} advertising support before opening its outbound stream.
 *
 * <p>By default, closing the child channel performs the WebSocket closing handshake. An incoming close frame
 * is acknowledged and forwarded to the application. To respond to control frames yourself, select
 * {@link ControlFrameHandling#APPLICATION}. In either mode, the child channel's close future signals that
 * the stream closed. Inspect the received close frame for the WebSocket close status. To abort immediately,
 * write a {@link Http2ResetFrame}.
 * Stream reads and writes use the existing HTTP/2 flow controller. When using manual HTTP/2 window updates,
 * account for the original {@link Http2DataFrame#initialFlowControlledBytes()} before the WebSocket codecs.
 */
@UnstableApi
public final class Http2WebSocketHandshaker {
    /** Selects who responds to WebSocket control frames for each stream opened by this handshaker. */
    public enum ControlFrameHandling {
        /**
         * Automatically answers Ping frames, acknowledges an incoming Close, and initiates the closing
         * handshake when the child channel is closed. Pong and Close frames are forwarded to the application.
         */
        AUTOMATIC,

        /**
         * Passes Ping, Pong and Close frames to the application, which is responsible for writing Pong
         * and Close replies. For a graceful shutdown, write and flush a Close frame; Netty sends the
         * HTTP/2 end-of-stream signal after both Close frames and handles protocol errors and close timeouts.
         * Closing the channel without first writing a Close frame aborts the stream instead.
         */
        APPLICATION
    }

    private final int maxFramePayloadLength;
    private final boolean allowCompression;
    private final long timeoutMillis;
    private final boolean automaticControlFrames;

    /** Uses a 64 KiB frame limit, disables compression, and uses a ten-second handshake timeout. */
    public Http2WebSocketHandshaker() {
        this(65536, false);
    }

    /** Uses the given frame limit and optionally negotiates permessage-deflate. */
    public Http2WebSocketHandshaker(int maxFramePayloadLength, boolean allowCompression) {
        this(maxFramePayloadLength, allowCompression, 10000);
    }

    /**
     * @param maxFramePayloadLength maximum wire frame payload and the decompression buffer size for default compression
     * @param allowCompression whether to offer or accept permessage-deflate
     * @param timeoutMillis timeout in milliseconds for opening on the client and for closing on either endpoint
     */
    public Http2WebSocketHandshaker(int maxFramePayloadLength, boolean allowCompression, long timeoutMillis) {
        this(maxFramePayloadLength, allowCompression, timeoutMillis, ControlFrameHandling.AUTOMATIC);
    }

    /**
     * @param maxFramePayloadLength maximum wire frame payload and the decompression buffer size for default compression
     * @param allowCompression whether to offer or accept permessage-deflate
     * @param timeoutMillis timeout in milliseconds for opening on the client and for closing on either endpoint
     * @param controlFrameHandling whether Netty or the application responds to control frames
     */
    public Http2WebSocketHandshaker(int maxFramePayloadLength, boolean allowCompression, long timeoutMillis,
                                    ControlFrameHandling controlFrameHandling) {
        this.automaticControlFrames = checkNotNull(controlFrameHandling, "controlFrameHandling") ==
                ControlFrameHandling.AUTOMATIC;
        this.maxFramePayloadLength = checkPositive(maxFramePayloadLength, "maxFramePayloadLength");
        this.allowCompression = allowCompression;
        this.timeoutMillis = checkPositive(timeoutMillis, "timeoutMillis");
    }

    /** Returns whether the request uses extended CONNECT with the websocket protocol. */
    public static boolean isWebSocketRequest(Http2Headers headers) {
        return Http2WebSocketHandshakeHeaders.isWebSocketRequest(headers);
    }

    /**
     * Sends the opening request on a fresh client stream. Supply the HTTP scheme, authority and path, plus any
     * regular fields such as Origin, cookies, or offered subprotocols. The handshaker generates the CONNECT
     * method, protocol, version and extension offer. The returned future supplies the final response headers,
     * including the selected subprotocol, after the frame codecs are ready. Cancelling it initiates an abort
     * of this stream. To observe the end of the abort, wait for the child channel's close future.
     */
    public Future<Http2Headers> connect(ChannelHandlerContext ctx, Http2Headers requestHeaders) {
        return connect0(ctx, requestHeaders, null);
    }

    /**
     * Opens a stream using the supplied permessage-deflate handshaker instead of the constructor's compression
     * setting. The supplied handshaker must not offer {@code client_max_window_bits} because the local encoder
     * cannot support all of the window sizes a server could select. As required by RFC 7692, the client
     * accepts {@code client_no_context_takeover} in a response even when it does not appear in the offer.
     *
     * @param ctx the application context on the client stream's event loop
     * @param requestHeaders the application headers for the opening request
     * @param compression the permessage-deflate configuration for this stream
     * @return the same handshake future as {@link #connect(ChannelHandlerContext, Http2Headers)}
     * @throws IllegalArgumentException if the decompression buffer limit is unlimited or exceeds the frame limit
     */
    public Future<Http2Headers> connect(ChannelHandlerContext ctx, Http2Headers requestHeaders,
                                        PerMessageDeflateClientExtensionHandshaker compression) {
        checkCompressionLimit(checkNotNull(compression, "compression").maxAllocation());
        return connect0(ctx, requestHeaders, compression);
    }

    private Future<Http2Headers> connect0(ChannelHandlerContext ctx, Http2Headers requestHeaders,
                                          PerMessageDeflateClientExtensionHandshaker customCompression) {
        Http2FrameCodec codec = frameCodec(ctx, false);
        if (((Http2StreamChannel) ctx.channel()).stream().id() > 0 ||
                ctx.pipeline().context(ClientHandshake.class) != null) {
            return ctx.executor().newFailedFuture(new IllegalStateException("expected a fresh HTTP/2 stream"));
        }
        if (!codec.isConnectProtocolEnabled()) {
            return ctx.executor().newFailedFuture(new WebSocketHandshakeException(
                    "peer has not advertised SETTINGS_ENABLE_CONNECT_PROTOCOL"));
        }
        PerMessageDeflateClientExtensionHandshaker compression = customCompression;
        if (compression == null && allowCompression) {
            compression = Http2WebSocketHandshakeHeaders.clientCompression(maxFramePayloadLength);
        }
        final Http2Headers request;
        try {
            request = Http2WebSocketHandshakeHeaders.newRequest(requestHeaders, compression, customCompression != null);
        } catch (WebSocketHandshakeException e) {
            return ctx.executor().newFailedFuture(e);
        }

        Promise<Http2Headers> result = ctx.executor().newPromise();
        ClientHandshake handshake = new ClientHandshake(request, compression, result);
        ctx.pipeline().addBefore(ctx.name(), null, handshake);
        ScheduledFuture<?> timeout = ctx.executor().schedule(() -> result.tryFailure(
                new WebSocketHandshakeException("WebSocket opening handshake timed out")), timeoutMillis,
                TimeUnit.MILLISECONDS);
        result.addListener(future -> {
            timeout.cancel(false);
            if (!future.isSuccess() && !handshake.remoteReset) {
                ctx.close();
            }
        });
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(request)).addListener(future -> {
            if (!future.isSuccess()) {
                result.tryFailure(future.cause());
            }
        });
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        return result;
    }

    /**
     * Accepts an inbound request the application has routed and authorized. The application may select one of
     * the offered subprotocols with {@link HttpHeaderNames#SEC_WEBSOCKET_PROTOCOL} in {@code responseHeaders}
     * and can add regular response fields. The handshaker sets the status and negotiates extensions.
     * Invalid opening requests get a final 400 response. Application configuration errors fail the future
     * before any response is written. The returned future reports completion of the local response write.
     * Neither supplied headers object is modified.
     */
    public ChannelFuture accept(ChannelHandlerContext ctx, Http2HeadersFrame request, Http2Headers responseHeaders) {
        return accept0(ctx, request, responseHeaders, null);
    }

    /**
     * Accepts a stream using the supplied permessage-deflate handshaker instead of the constructor's compression
     * setting.
     *
     * @param ctx the application context on the server stream's event loop
     * @param request the opening request the application has routed and authorized
     * @param responseHeaders the application headers for the opening response
     * @param compression the permessage-deflate configuration for this stream
     * @return the same local response-write future as
     *         {@link #accept(ChannelHandlerContext, Http2HeadersFrame, Http2Headers)}
     * @throws IllegalArgumentException if the decompression buffer limit is unlimited or exceeds the frame limit
     */
    public ChannelFuture accept(ChannelHandlerContext ctx, Http2HeadersFrame request, Http2Headers responseHeaders,
                                PerMessageDeflateServerExtensionHandshaker compression) {
        checkCompressionLimit(checkNotNull(compression, "compression").maxAllocation());
        return accept0(ctx, request, responseHeaders, compression);
    }

    private ChannelFuture accept0(ChannelHandlerContext ctx, Http2HeadersFrame request, Http2Headers responseHeaders,
                                  PerMessageDeflateServerExtensionHandshaker customCompression) {
        Http2FrameCodec codec = frameCodec(ctx, true);
        Http2StreamChannel channel = (Http2StreamChannel) ctx.channel();
        if (request.stream() != null && request.stream() != channel.stream()) {
            return ctx.newFailedFuture(new IllegalArgumentException("request belongs to a different HTTP/2 stream"));
        }
        if (ctx.pipeline().context(Http2WebSocketFrameCodec.class) != null) {
            return ctx.newFailedFuture(new IllegalStateException("WebSocket already established"));
        }
        final List<WebSocketExtensionData> offers;
        try {
            if (!codec.isConnectProtocolEnabled() || request.isEndStream() ||
                    AbstractHttp2StreamChannel.isRemoteSideClosed(channel)) {
                throw new WebSocketHandshakeException("extended CONNECT is disabled or the stream has ended");
            }
            offers = Http2WebSocketHandshakeHeaders.validateRequest(request.headers());
        } catch (WebSocketHandshakeException e) {
            Http2Headers error = new DefaultHttp2Headers().status("400");
            CharSequence version = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
            if (isWebSocketRequest(request.headers()) && !AsciiString.contentEquals(
                    WebSocketVersion.V13.toHttpHeaderValue(), version)) {
                error.set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue());
            }
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(error, true))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            return ctx.newFailedFuture(e);
        }

        final Http2Headers response;
        final WebSocketServerExtension extension;
        try {
            response = Http2WebSocketHandshakeHeaders.copyOf(responseHeaders);
            PerMessageDeflateServerExtensionHandshaker compression = customCompression;
            if (compression == null && allowCompression) {
                compression = Http2WebSocketHandshakeHeaders.serverCompression(maxFramePayloadLength);
            }
            extension = Http2WebSocketHandshakeHeaders.negotiateServer(
                    request.headers(), response, compression, offers);
        } catch (WebSocketHandshakeException e) {
            return ctx.newFailedFuture(e);
        }
        Http2WebSocketFrameCodec.install(ctx, true, maxFramePayloadLength, extension, timeoutMillis,
                automaticControlFrames);
        // Write the HTTP response before returning control to the application. A failed response aborts the stream.
        ChannelHandlerContext transport = ctx.pipeline().context(Http2WebSocketFrameCodec.class);
        return transport.writeAndFlush(new DefaultHttp2HeadersFrame(response)).addListener(f -> {
            if (!f.isSuccess()) {
                transport.close();
            }
        });
    }

    private void checkCompressionLimit(int maxAllocation) {
        if (maxAllocation <= 0 || maxAllocation > maxFramePayloadLength) {
            throw new IllegalArgumentException("compression maxAllocation must be between 1 and "
                    + maxFramePayloadLength + " (was " + maxAllocation + ')');
        }
    }

    private static Http2FrameCodec frameCodec(ChannelHandlerContext ctx, boolean server) {
        checkNotNull(ctx, "ctx");
        if (ctx.isRemoved() || !(ctx.channel() instanceof Http2StreamChannel) ||
                ctx.executor() != ctx.channel().eventLoop() || !ctx.executor().inEventLoop()) {
            throw new IllegalStateException("expected an HTTP/2 stream context on its event loop");
        }
        Http2FrameCodec codec = ctx.channel().parent().pipeline().get(Http2FrameCodec.class);
        if (codec == null || codec.connection().isServer() != server) {
            throw new IllegalStateException(server ? "expected a server stream" : "expected a client stream");
        }
        return codec;
    }

    private final class ClientHandshake extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final Http2Headers request;
        private final PerMessageDeflateClientExtensionHandshaker compression;
        private final Promise<Http2Headers> result;
        private boolean remoteReset;

        ClientHandshake(Http2Headers request, PerMessageDeflateClientExtensionHandshaker compression,
                        Promise<Http2Headers> result) {
            this.request = request;
            this.compression = compression;
            this.result = result;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
            if (result.isDone()) {
                return;
            }
            if (!(msg instanceof Http2HeadersFrame) && !(msg instanceof Http2DataFrame)) {
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
                if (!ctx.channel().config().isAutoRead()) {
                    ctx.read();
                }
                return;
            }
            if (!(msg instanceof Http2HeadersFrame)) {
                result.tryFailure(new WebSocketHandshakeException("expected a response on an open stream"));
                return;
            }
            Http2Headers response = ((Http2HeadersFrame) msg).headers();
            HttpStatusClass status = HttpStatusClass.valueOf(response.status());
            if (status == HttpStatusClass.INFORMATIONAL && !AsciiString.contentEquals("101", response.status()) &&
                    !((Http2HeadersFrame) msg).isEndStream()) {
                if (!ctx.channel().config().isAutoRead()) {
                    ctx.read();
                }
                return;
            }
            if (status != HttpStatusClass.SUCCESS) {
                result.tryFailure(new WebSocketHandshakeException("unexpected CONNECT status: " + response.status()));
                return;
            }
            if (((Http2HeadersFrame) msg).isEndStream()) {
                result.tryFailure(new WebSocketHandshakeException("successful CONNECT response ended the stream"));
                return;
            }
            try {
                WebSocketClientExtension extension =
                        Http2WebSocketHandshakeHeaders.validateResponse(request, response, compression);
                if (!result.setUncancellable()) {
                    return;
                }
                Http2WebSocketFrameCodec.install(ctx, false, maxFramePayloadLength, extension, timeoutMillis,
                        automaticControlFrames);
                ctx.pipeline().remove(this);
                result.trySuccess(response);
            } catch (WebSocketHandshakeException e) {
                result.tryFailure(e);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof Http2ResetFrame) {
                remoteReset = true;
                result.tryFailure(new WebSocketHandshakeException("HTTP/2 stream reset during WebSocket handshake"));
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            result.tryFailure(new ClosedChannelException());
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            result.tryFailure(cause);
        }
    }
}

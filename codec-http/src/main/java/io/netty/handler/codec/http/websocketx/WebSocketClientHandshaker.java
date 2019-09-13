/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Base class for web socket client handshake implementations
 */
public abstract class WebSocketClientHandshaker {

    private static final String HTTP_SCHEME_PREFIX = HttpScheme.HTTP + "://";
    private static final String HTTPS_SCHEME_PREFIX = HttpScheme.HTTPS + "://";
    protected static final int DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS = 10000;

    private final URI uri;

    private final WebSocketVersion version;

    private volatile boolean handshakeComplete;

    private volatile long forceCloseTimeoutMillis = DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS;

    private volatile int forceCloseInit;

    private static final AtomicIntegerFieldUpdater<WebSocketClientHandshaker> FORCE_CLOSE_INIT_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(WebSocketClientHandshaker.class, "forceCloseInit");

    private volatile boolean forceCloseComplete;

    private final String expectedSubprotocol;

    private volatile String actualSubprotocol;

    protected final HttpHeaders customHeaders;

    private final int maxFramePayloadLength;

    private final boolean absoluteUpgradeUrl;

    private final String httpRequestEncoderName;

    private final String httpResponseDecoderName;

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength,
                                        long forceCloseTimeoutMillis) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, forceCloseTimeoutMillis, false);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     * @param  absoluteUpgradeUrl
     *            Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over
     *            clear HTTP
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength,
                                        long forceCloseTimeoutMillis, boolean absoluteUpgradeUrl) {
        this(uri, version, subprotocol, customHeaders, maxFramePayloadLength, forceCloseTimeoutMillis,
            absoluteUpgradeUrl, null, null);
    }

    /**
     * Base constructor
     *
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     * @param  absoluteUpgradeUrl
     *            Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over
     *            clear HTTP
     * @param httpRequestEncoderName
     *            After the handshake is complete, the {@link HttpRequestEncoder} or {@link HttpClientCodec}
     *            in the pipeline needs to be replaced with a websocket encoder.  If httpRequestEncoderName is present,
     *            the handshaker assumes that this is the name of the http request encoder that should be replaced.
     *            If not specified, the first {@link HttpRequestEncoder} or {@link HttpClientCodec} found in the
     *            pipeline will be replaced.
     * @param httpResponseDecoderName
     *            After the handshake is complete, the {@link HttpResponseDecoder} or {@link HttpClientCodec}
     *            in the pipeline needs to be replaced with a websocket decoder.  If httpResponseDecoderName is present,
     *            the handshaker assumes that this is the name of the http response decoder that should be replaced.
     *            If not specified, the first {@link HttpResponseDecoder} or {@link HttpClientCodec} found in the
     *            pipeline will be replaced
     */
    protected WebSocketClientHandshaker(URI uri, WebSocketVersion version, String subprotocol,
                                        HttpHeaders customHeaders, int maxFramePayloadLength,
                                        long forceCloseTimeoutMillis, boolean absoluteUpgradeUrl,
                                        String httpRequestEncoderName, String httpResponseDecoderName) {
        this.uri = uri;
        this.version = version;
        expectedSubprotocol = subprotocol;
        this.customHeaders = customHeaders;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        this.absoluteUpgradeUrl = absoluteUpgradeUrl;
        this.httpRequestEncoderName = httpRequestEncoderName;
        this.httpResponseDecoderName = httpResponseDecoderName;
    }

    /**
     * Returns the URI to the web socket. e.g. "ws://myhost.com/path"
     */
    public URI uri() {
        return uri;
    }

    /**
     * Version of the web socket specification that is being used
     */
    public WebSocketVersion version() {
        return version;
    }

    /**
     * Returns the max length for any frame's payload
     */
    public int maxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * Flag to indicate if the opening handshake is complete
     */
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    private void setHandshakeComplete() {
        handshakeComplete = true;
    }

    /**
     * Returns the CSV of requested subprotocol(s) sent to the server as specified in the constructor
     */
    public String expectedSubprotocol() {
        return expectedSubprotocol;
    }

    /**
     * Returns the subprotocol response sent by the server. Only available after end of handshake.
     * Null if no subprotocol was requested or confirmed by the server.
     */
    public String actualSubprotocol() {
        return actualSubprotocol;
    }

    private void setActualSubprotocol(String actualSubprotocol) {
        this.actualSubprotocol = actualSubprotocol;
    }

    public long forceCloseTimeoutMillis() {
        return forceCloseTimeoutMillis;
    }

    /**
     * Flag to indicate if the closing handshake was initiated because of timeout.
     * For testing only.
     */
    protected boolean isForceCloseComplete() {
        return forceCloseComplete;
    }

    /**
     * Sets timeout to close the connection if it was not closed by the server.
     *
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified
     */
    public WebSocketClientHandshaker setForceCloseTimeoutMillis(long forceCloseTimeoutMillis) {
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        return this;
    }

    /**
     * Begins the opening handshake
     *
     * @param channel
     *            Channel
     */
    public ChannelFuture handshake(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return handshake(channel, channel.newPromise());
    }

    /**
     * Begins the opening handshake
     *
     * @param channel
     *            Channel
     * @param promise
     *            the {@link ChannelPromise} to be notified when the opening handshake is sent
     */
    public final ChannelFuture handshake(Channel channel, final ChannelPromise promise) {
        try {
            findHttpResponseDecoderToReplace(channel.pipeline());
        } catch (IllegalStateException e) {
            return promise.setFailure(e);
        }

        FullHttpRequest request = newHandshakeRequest();

        channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ChannelPipeline p = future.channel().pipeline();
                    try {
                        ChannelHandlerContext ctx =
                            p.context(findHttpRequestEncoderToReplace(future.channel().pipeline()));
                        p.addAfter(ctx.name(), "ws-encoder", newWebSocketEncoder());
                        promise.setSuccess();
                    } catch (IllegalStateException e) {
                        promise.setFailure(e);
                    }
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
        return promise;
    }

    /**
     * Returns a new {@link FullHttpRequest) which will be used for the handshake.
     */
    protected abstract FullHttpRequest newHandshakeRequest();

    /**
     * Validates and finishes the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     */
    public final void finishHandshake(Channel channel, FullHttpResponse response) {
        verify(response);

        // Verify the subprotocol that we received from the server.
        // This must be one of our expected subprotocols - or null/empty if we didn't want to speak a subprotocol
        String receivedProtocol = response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        receivedProtocol = receivedProtocol != null ? receivedProtocol.trim() : null;
        String expectedProtocol = expectedSubprotocol != null ? expectedSubprotocol : "";
        boolean protocolValid = false;

        if (expectedProtocol.isEmpty() && receivedProtocol == null) {
            // No subprotocol required and none received
            protocolValid = true;
            setActualSubprotocol(expectedSubprotocol); // null or "" - we echo what the user requested
        } else if (!expectedProtocol.isEmpty() && receivedProtocol != null && !receivedProtocol.isEmpty()) {
            // We require a subprotocol and received one -> verify it
            for (String protocol : expectedProtocol.split(",")) {
                if (protocol.trim().equals(receivedProtocol)) {
                    protocolValid = true;
                    setActualSubprotocol(receivedProtocol);
                    break;
                }
            }
        } // else mixed cases - which are all errors

        if (!protocolValid) {
            throw new WebSocketHandshakeException(String.format(
                    "Invalid subprotocol. Actual: %s. Expected one of: %s",
                    receivedProtocol, expectedSubprotocol));
        }

        setHandshakeComplete();

        final ChannelPipeline p = channel.pipeline();
        // Remove decompressor from pipeline if its in use
        HttpContentDecompressor decompressor = p.get(HttpContentDecompressor.class);
        if (decompressor != null) {
            p.remove(decompressor);
        }

        // Remove aggregator if present before
        HttpObjectAggregator aggregator = p.get(HttpObjectAggregator.class);
        if (aggregator != null) {
            p.remove(aggregator);
        }

        ChannelHandler httpResponseDecoder = findHttpResponseDecoderToReplace(channel.pipeline());
        ChannelHandlerContext ctx = p.context(httpResponseDecoder);
        if (httpResponseDecoder instanceof HttpClientCodec) {
            final HttpClientCodec codec = (HttpClientCodec) httpResponseDecoder;
            // Remove the encoder part of the codec as the user may start writing frames after this method returns.
            codec.removeOutboundHandler();

            p.addAfter(ctx.name(), "ws-decoder", newWebsocketDecoder());

            // Delay the removal of the decoder so the user can setup the pipeline if needed to handle
            // WebSocketFrame messages.
            // See https://github.com/netty/netty/issues/4533
            channel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    p.remove(codec);
                }
            });
        } else {
            // httpResponseDecoder is not an instance of HttpClientCodec, so it must be
            // an instance of HttpResponseDecoder, and there must be a matching HttpRequestEncoder
            // instance in the pipeline that needs to be removed as well
            p.remove(findHttpRequestEncoderToReplace(channel.pipeline()));

            final ChannelHandlerContext context = ctx;
            p.addAfter(context.name(), "ws-decoder", newWebsocketDecoder());

            // Delay the removal of the decoder so the user can setup the pipeline if needed to handle
            // WebSocketFrame messages.
            // See https://github.com/netty/netty/issues/4533
            channel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    p.remove(context.handler());
                }
            });
        }
    }

    /**
     * Process the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     * @return future
     *            the {@link ChannelFuture} which is notified once the handshake completes.
     */
    public final ChannelFuture processHandshake(final Channel channel, HttpResponse response) {
        return processHandshake(channel, response, channel.newPromise());
    }

    /**
     * Process the opening handshake initiated by {@link #handshake}}.
     *
     * @param channel
     *            Channel
     * @param response
     *            HTTP response containing the closing handshake details
     * @param promise
     *            the {@link ChannelPromise} to notify once the handshake completes.
     * @return future
     *            the {@link ChannelFuture} which is notified once the handshake completes.
     */
    public final ChannelFuture processHandshake(final Channel channel, HttpResponse response,
                                                final ChannelPromise promise) {
        if (response instanceof FullHttpResponse) {
            try {
                finishHandshake(channel, (FullHttpResponse) response);
                promise.setSuccess();
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
        } else {
            ChannelPipeline p = channel.pipeline();
            ChannelHandlerContext ctx;
            try {
                ctx = p.context(findHttpResponseDecoderToReplace(channel.pipeline()));
            } catch (IllegalStateException e) {
                return promise.setFailure(e);
            }
            // Add aggregator and ensure we feed the HttpResponse so it is aggregated. A limit of 8192 should be more
            // then enough for the websockets handshake payload.
            //
            // TODO: Make handshake work without HttpObjectAggregator at all.
            String aggregatorName = "httpAggregator";
            p.addAfter(ctx.name(), aggregatorName, new HttpObjectAggregator(8192));
            p.addAfter(aggregatorName, "handshaker", new SimpleChannelInboundHandler<FullHttpResponse>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                    // Remove ourself and do the actual handshake
                    ctx.pipeline().remove(this);
                    try {
                        finishHandshake(channel, msg);
                        promise.setSuccess();
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    // Remove ourself and fail the handshake promise.
                    ctx.pipeline().remove(this);
                    promise.setFailure(cause);
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    // Fail promise if Channel was closed
                    if (!promise.isDone()) {
                        promise.tryFailure(new ClosedChannelException());
                    }
                    ctx.fireChannelInactive();
                }
            });
            try {
                ctx.fireChannelRead(ReferenceCountUtil.retain(response));
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
        }
        return promise;
    }

    /**
     * Verify the {@link FullHttpResponse} and throws a {@link WebSocketHandshakeException} if something is wrong.
     */
    protected abstract void verify(FullHttpResponse response);

    /**
     * Returns the decoder to use after handshake is complete.
     */
    protected abstract WebSocketFrameDecoder newWebsocketDecoder();

    /**
     * Returns the encoder to use after the handshake is complete.
     */
    protected abstract WebSocketFrameEncoder newWebSocketEncoder();

    /**
     * Performs the closing handshake
     *
     * @param channel
     *            Channel
     * @param frame
     *            Closing Frame that was received
     */
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return close(channel, frame, channel.newPromise());
    }

    /**
     * Performs the closing handshake
     *
     * @param channel
     *            Channel
     * @param frame
     *            Closing Frame that was received
     * @param promise
     *            the {@link ChannelPromise} to be notified when the closing handshake is done
     */
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame, ChannelPromise promise) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        channel.writeAndFlush(frame, promise);
        applyForceCloseTimeout(channel, promise);
        return promise;
    }

    private void applyForceCloseTimeout(final Channel channel, ChannelFuture flushFuture) {
        final long forceCloseTimeoutMillis = this.forceCloseTimeoutMillis;
        final WebSocketClientHandshaker handshaker = this;
        if (forceCloseTimeoutMillis <= 0 || !channel.isActive() || forceCloseInit != 0) {
            return;
        }

        flushFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // If flush operation failed, there is no reason to expect
                // a server to receive CloseFrame. Thus this should be handled
                // by the application separately.
                // Also, close might be called twice from different threads.
                if (future.isSuccess() && channel.isActive() &&
                        FORCE_CLOSE_INIT_UPDATER.compareAndSet(handshaker, 0, 1)) {
                    final Future<?> forceCloseFuture = channel.eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            if (channel.isActive()) {
                                channel.close();
                                forceCloseComplete = true;
                            }
                        }
                    }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);

                    channel.closeFuture().addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            forceCloseFuture.cancel(false);
                        }
                    });
                }
            }
        });
    }

    /**
     * Return the constructed raw path for the give {@link URI}.
     */
    protected String upgradeUrl(URI wsURL) {
        if (absoluteUpgradeUrl) {
            return wsURL.toString();
        }

        String path = wsURL.getRawPath();
        String query = wsURL.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + '?' + query;
        }

        return path == null || path.isEmpty() ? "/" : path;
    }

    /**
     * Finds the {@link ChannelHandler} instance in the pipeline that is responsible for encoding
     * HTTP requests, which should be either a {@link HttpRequestEncoder} or a {@link HttpClientCodec}.
     * This is the handler we will replace with a websocket encoder once the handshake is complete.
     * By default this method will first look for an encoder with the name {@link #httpRequestEncoderName}
     * if defined.  It no name is defined, an attempt will be made to find the first {@link HttpRequestEncoder}
     * or {@link HttpClientCodec} in the pipeline.  This method may be overridden by subclasses to specify
     * the exact encoder instance that should be removed.
     * @param pipeline the pipeline in which to find the encoder.
     * @return the {@link ChannelHandler} in the given pipeline that is used to encode HTTP requests.
     * @throws IllegalStateException if the http request encoder could be found in the pipeline.
     */
    protected ChannelHandler findHttpRequestEncoderToReplace(ChannelPipeline pipeline) throws IllegalStateException {
        // If a name was specified for the http request encoder, require that a handler with that name exists:
        if (httpRequestEncoderName != null) {
            final ChannelHandler encoder = pipeline.get(httpRequestEncoderName);
            if (encoder == null) {
                throw new IllegalStateException("No http request encoder found with name \"" +
                    httpRequestEncoderName + "\"");
            } else if (!(encoder instanceof HttpRequestEncoder || encoder instanceof HttpClientCodec)) {
                throw new IllegalStateException("Expected encoder with name \"" + httpRequestEncoderName +
                    "\" to be a " + HttpRequestEncoder.class.getSimpleName() + " or " +
                    HttpClientCodec.class.getSimpleName() + " but found " +
                    encoder.getClass().getCanonicalName());
            }
            return encoder;
        }

        // Otherwise fall back to the original logic of finding the first
        // HttpRequestEncoder or HttpClientCodec:
        ChannelHandler encoder = pipeline.get(HttpRequestEncoder.class);
        if (encoder == null) {
            encoder = pipeline.get(HttpClientCodec.class);
            if (encoder == null) {
                throw new IllegalStateException("ChannelPipeline does not contain " +
                    "a " + HttpClientCodec.class.getSimpleName() + " or " +
                    HttpClientCodec.class.getSimpleName());
            }
        }
        return encoder;
    }

    /**
     * Finds the {@link ChannelHandler} instance in the pipeline that is responsible for decoding
     * HTTP responses, which should be either a {@link HttpResponseDecoder} or a {@link HttpClientCodec}.
     * This is the handler we will replace with a websocket decoder once the handshake is complete.
     * By default this method will first look for a decoder with the name {@link #httpResponseDecoderName}
     * if defined.  It no name is defined, an attempt will be made to find the first {@link HttpResponseDecoder}
     * or {@link HttpClientCodec} in the pipeline.  This method may be overridden by subclasses to specify
     * the exact decoder instance that should be removed.
     * @param pipeline the pipeline in which to find the decoder.
     * @return the {@link ChannelHandler} in the given pipeline that is used to decode HTTP responses.
     * @throws IllegalStateException if the http response decoder could be found in the pipeline.
     */
    protected ChannelHandler findHttpResponseDecoderToReplace(ChannelPipeline pipeline) throws IllegalStateException {
        // If a name was specified for the http codec, require that a handler with that name exists:
        if (httpResponseDecoderName != null) {
            final ChannelHandler decoder = pipeline.get(httpResponseDecoderName);
            if (decoder == null) {
                throw new IllegalStateException("No http response decoder found with name \"" +
                    httpResponseDecoderName + "\"");
            }
            if (!(decoder instanceof HttpResponseDecoder || decoder instanceof HttpClientCodec)) {
                throw new IllegalStateException("Expected decoder with name \"" + httpResponseDecoderName +
                    "\" to be a " + HttpResponseDecoder.class.getSimpleName() + " or " +
                    HttpClientCodec.class.getSimpleName() + " but found " +
                    decoder.getClass().getCanonicalName());
            }
            return decoder;
        }

        // Otherwise fall back to the original logic of finding the first
        // HttpResponseDecoder or HttpClientCodec:
        ChannelHandler decoder = pipeline.get(HttpResponseDecoder.class);
        if (decoder == null) {
            decoder = pipeline.get(HttpClientCodec.class);
            if (decoder == null) {
                throw new IllegalStateException("ChannelPipeline does not contain " +
                    "a " + HttpResponseDecoder.class.getSimpleName() + " or " +
                    HttpClientCodec.class.getSimpleName());
            }
        }
        return decoder;
    }

    static CharSequence websocketHostValue(URI wsURL) {
        int port = wsURL.getPort();
        if (port == -1) {
            return wsURL.getHost();
        }
        String host = wsURL.getHost();
        String scheme = wsURL.getScheme();
        if (port == HttpScheme.HTTP.port()) {
            return HttpScheme.HTTP.name().contentEquals(scheme)
                    || WebSocketScheme.WS.name().contentEquals(scheme) ?
                    host : NetUtil.toSocketAddressString(host, port);
        }
        if (port == HttpScheme.HTTPS.port()) {
            return HttpScheme.HTTPS.name().contentEquals(scheme)
                    || WebSocketScheme.WSS.name().contentEquals(scheme) ?
                    host : NetUtil.toSocketAddressString(host, port);
        }

        // if the port is not standard (80/443) its needed to add the port to the header.
        // See http://tools.ietf.org/html/rfc6454#section-6.2
        return NetUtil.toSocketAddressString(host, port);
    }

    static CharSequence websocketOriginValue(URI wsURL) {
        String scheme = wsURL.getScheme();
        final String schemePrefix;
        int port = wsURL.getPort();
        final int defaultPort;
        if (WebSocketScheme.WSS.name().contentEquals(scheme)
            || HttpScheme.HTTPS.name().contentEquals(scheme)
            || (scheme == null && port == WebSocketScheme.WSS.port())) {

            schemePrefix = HTTPS_SCHEME_PREFIX;
            defaultPort = WebSocketScheme.WSS.port();
        } else {
            schemePrefix = HTTP_SCHEME_PREFIX;
            defaultPort = WebSocketScheme.WS.port();
        }

        // Convert uri-host to lower case (by RFC 6454, chapter 4 "Origin of a URI")
        String host = wsURL.getHost().toLowerCase(Locale.US);

        if (port != defaultPort && port != -1) {
            // if the port is not standard (80/443) its needed to add the port to the header.
            // See http://tools.ietf.org/html/rfc6454#section-6.2
            return schemePrefix + NetUtil.toSocketAddressString(host, port);
        }
        return schemePrefix + host;
    }
}

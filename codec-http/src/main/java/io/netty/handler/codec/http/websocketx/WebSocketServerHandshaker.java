/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base class for server side web socket opening and closing handshakes
 */
public abstract class WebSocketServerHandshaker {
    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketServerHandshaker.class);

    private final String uri;

    private final String[] subprotocols;

    private final WebSocketVersion version;

    private final WebSocketDecoderConfig decoderConfig;

    private String selectedSubprotocol;

    /**
     * Use this as wildcard to support all requested sub-protocols
     */
    public static final String SUB_PROTOCOL_WILDCARD = "*";

    /**
     * Constructor specifying the destination web socket location
     *
     * @param version
     *            the protocol version
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    protected WebSocketServerHandshaker(
            WebSocketVersion version, String uri, String subprotocols,
            int maxFramePayloadLength) {
        this(version, uri, subprotocols, WebSocketDecoderConfig.newBuilder()
            .maxFramePayloadLength(maxFramePayloadLength)
            .build());
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param version
     *            the protocol version
     * @param uri
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not supported.
     * @param decoderConfig
     *            Frames decoder configuration.
     */
    protected WebSocketServerHandshaker(
            WebSocketVersion version, String uri, String subprotocols, WebSocketDecoderConfig decoderConfig) {
        this.version = version;
        this.uri = uri;
        if (subprotocols != null) {
            String[] subprotocolArray = subprotocols.split(",");
            for (int i = 0; i < subprotocolArray.length; i++) {
                subprotocolArray[i] = subprotocolArray[i].trim();
            }
            this.subprotocols = subprotocolArray;
        } else {
            this.subprotocols = EmptyArrays.EMPTY_STRINGS;
        }
        this.decoderConfig = ObjectUtil.checkNotNull(decoderConfig, "decoderConfig");
    }

    /**
     * Returns the URL of the web socket
     */
    public String uri() {
        return uri;
    }

    /**
     * Returns the CSV of supported sub protocols
     */
    public Set<String> subprotocols() {
        Set<String> ret = new LinkedHashSet<String>();
        Collections.addAll(ret, subprotocols);
        return ret;
    }

    /**
     * Returns the version of the specification being supported
     */
    public WebSocketVersion version() {
        return version;
    }

    /**
     * Gets the maximum length for any frame's payload.
     *
     * @return The maximum length for a frame's payload
     */
    public int maxFramePayloadLength() {
        return decoderConfig.maxFramePayloadLength();
    }

    /**
     * Gets this decoder configuration.
     *
     * @return This decoder configuration.
     */
    public WebSocketDecoderConfig decoderConfig() {
        return decoderConfig;
    }

    /**
     * Performs the opening handshake. When call this method you <strong>MUST NOT</strong> retain the
     * {@link FullHttpRequest} which is passed in.
     *
     * @param channel
     *              Channel
     * @param req
     *              HTTP Request
     * @return future
     *              The {@link ChannelFuture} which is notified once the opening handshake completes
     */
    public ChannelFuture handshake(Channel channel, FullHttpRequest req) {
        return handshake(channel, req, null, channel.newPromise());
    }

    /**
     * Performs the opening handshake
     *
     * When call this method you <strong>MUST NOT</strong> retain the {@link FullHttpRequest} which is passed in.
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     * @param responseHeaders
     *            Extra headers to add to the handshake response or {@code null} if no extra headers should be added
     * @param promise
     *            the {@link ChannelPromise} to be notified when the opening handshake is done
     * @return future
     *            the {@link ChannelFuture} which is notified when the opening handshake is done
     */
    public final ChannelFuture handshake(Channel channel, FullHttpRequest req,
                                            HttpHeaders responseHeaders, final ChannelPromise promise) {

        if (logger.isDebugEnabled()) {
            logger.debug("{} WebSocket version {} server handshake", channel, version());
        }
        FullHttpResponse response = newHandshakeResponse(req, responseHeaders);
        ChannelPipeline p = channel.pipeline();
        if (p.get(HttpObjectAggregator.class) != null) {
            p.remove(HttpObjectAggregator.class);
        }
        if (p.get(HttpContentCompressor.class) != null) {
            p.remove(HttpContentCompressor.class);
        }
        ChannelHandlerContext ctx = p.context(HttpRequestDecoder.class);
        final String encoderName;
        if (ctx == null) {
            // this means the user use an HttpServerCodec
            ctx = p.context(HttpServerCodec.class);
            if (ctx == null) {
                promise.setFailure(
                        new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline"));
                return promise;
            }
            p.addBefore(ctx.name(), "wsencoder", newWebSocketEncoder());
            p.addBefore(ctx.name(), "wsdecoder", newWebsocketDecoder());
            encoderName = ctx.name();
        } else {
            p.replace(ctx.name(), "wsdecoder", newWebsocketDecoder());

            encoderName = p.context(HttpResponseEncoder.class).name();
            p.addBefore(encoderName, "wsencoder", newWebSocketEncoder());
        }
        channel.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ChannelPipeline p = future.channel().pipeline();
                    p.remove(encoderName);
                    promise.setSuccess();
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
        return promise;
    }

    /**
     * Performs the opening handshake. When call this method you <strong>MUST NOT</strong> retain the
     * {@link FullHttpRequest} which is passed in.
     *
     * @param channel
     *              Channel
     * @param req
     *              HTTP Request
     * @return future
     *              The {@link ChannelFuture} which is notified once the opening handshake completes
     */
    public ChannelFuture handshake(Channel channel, HttpRequest req) {
        return handshake(channel, req, null, channel.newPromise());
    }

    /**
     * Performs the opening handshake
     *
     * When call this method you <strong>MUST NOT</strong> retain the {@link HttpRequest} which is passed in.
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     * @param responseHeaders
     *            Extra headers to add to the handshake response or {@code null} if no extra headers should be added
     * @param promise
     *            the {@link ChannelPromise} to be notified when the opening handshake is done
     * @return future
     *            the {@link ChannelFuture} which is notified when the opening handshake is done
     */
    public final ChannelFuture handshake(final Channel channel, HttpRequest req,
                                         final HttpHeaders responseHeaders, final ChannelPromise promise) {

        if (req instanceof FullHttpRequest) {
            return handshake(channel, (FullHttpRequest) req, responseHeaders, promise);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("{} WebSocket version {} server handshake", channel, version());
        }
        ChannelPipeline p = channel.pipeline();
        ChannelHandlerContext ctx = p.context(HttpRequestDecoder.class);
        if (ctx == null) {
            // this means the user use an HttpServerCodec
            ctx = p.context(HttpServerCodec.class);
            if (ctx == null) {
                promise.setFailure(
                        new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline"));
                return promise;
            }
        }
        // Add aggregator and ensure we feed the HttpRequest so it is aggregated. A limit o 8192 should be more then
        // enough for the websockets handshake payload.
        //
        // TODO: Make handshake work without HttpObjectAggregator at all.
        String aggregatorName = "httpAggregator";
        p.addAfter(ctx.name(), aggregatorName, new HttpObjectAggregator(8192));
        p.addAfter(aggregatorName, "handshaker", new SimpleChannelInboundHandler<FullHttpRequest>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
                // Remove ourself and do the actual handshake
                ctx.pipeline().remove(this);
                handshake(channel, msg, responseHeaders, promise);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                // Remove ourself and fail the handshake promise.
                ctx.pipeline().remove(this);
                promise.tryFailure(cause);
                ctx.fireExceptionCaught(cause);
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
            ctx.fireChannelRead(ReferenceCountUtil.retain(req));
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
        return promise;
    }

    /**
     * Returns a new {@link FullHttpResponse) which will be used for as response to the handshake request.
     */
    protected abstract FullHttpResponse newHandshakeResponse(FullHttpRequest req,
                                         HttpHeaders responseHeaders);
    /**
     * Performs the closing handshake.
     *
     * When called from within a {@link ChannelHandler} you most likely want to use
     * {@link #close(ChannelHandlerContext, CloseWebSocketFrame)}.
     *
     * @param channel
     *            the {@link Channel} to use.
     * @param frame
     *            Closing Frame that was received.
     */
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame) {
        ObjectUtil.checkNotNull(channel, "channel");
        return close(channel, frame, channel.newPromise());
    }

    /**
     * Performs the closing handshake.
     *
     * When called from within a {@link ChannelHandler} you most likely want to use
     * {@link #close(ChannelHandlerContext, CloseWebSocketFrame, ChannelPromise)}.
     *
     * @param channel
     *            the {@link Channel} to use.
     * @param frame
     *            Closing Frame that was received.
     * @param promise
     *            the {@link ChannelPromise} to be notified when the closing handshake is done
     */
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame, ChannelPromise promise) {
        return close0(channel, frame, promise);
    }

    /**
     * Performs the closing handshake.
     *
     * @param ctx
     *            the {@link ChannelHandlerContext} to use.
     * @param frame
     *            Closing Frame that was received.
     */
    public ChannelFuture close(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        ObjectUtil.checkNotNull(ctx, "ctx");
        return close(ctx, frame, ctx.newPromise());
    }

    /**
     * Performs the closing handshake.
     *
     * @param ctx
     *            the {@link ChannelHandlerContext} to use.
     * @param frame
     *            Closing Frame that was received.
     * @param promise
     *            the {@link ChannelPromise} to be notified when the closing handshake is done.
     */
    public ChannelFuture close(ChannelHandlerContext ctx, CloseWebSocketFrame frame, ChannelPromise promise) {
        ObjectUtil.checkNotNull(ctx, "ctx");
        return close0(ctx, frame, promise).addListener(ChannelFutureListener.CLOSE);
    }

    private ChannelFuture close0(ChannelOutboundInvoker invoker, CloseWebSocketFrame frame, ChannelPromise promise) {
        return invoker.writeAndFlush(frame, promise).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Selects the first matching supported sub protocol
     *
     * @param requestedSubprotocols
     *            CSV of protocols to be supported. e.g. "chat, superchat"
     * @return First matching supported sub protocol. Null if not found.
     */
    protected String selectSubprotocol(String requestedSubprotocols) {
        if (requestedSubprotocols == null || subprotocols.length == 0) {
            return null;
        }

        String[] requestedSubprotocolArray = requestedSubprotocols.split(",");
        for (String p: requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol: subprotocols) {
                if (SUB_PROTOCOL_WILDCARD.equals(supportedSubprotocol)
                        || requestedSubprotocol.equals(supportedSubprotocol)) {
                    selectedSubprotocol = requestedSubprotocol;
                    return requestedSubprotocol;
                }
            }
        }

        // No match found
        return null;
    }

    /**
     * Returns the selected subprotocol. Null if no subprotocol has been selected.
     * <p>
     * This is only available AFTER <tt>handshake()</tt> has been called.
     * </p>
     */
    public String selectedSubprotocol() {
        return selectedSubprotocol;
    }

    /**
     * Returns the decoder to use after handshake is complete.
     */
    protected abstract WebSocketFrameDecoder newWebsocketDecoder();

    /**
     * Returns the encoder to use after the handshake is complete.
     */
    protected abstract WebSocketFrameEncoder newWebSocketEncoder();

}

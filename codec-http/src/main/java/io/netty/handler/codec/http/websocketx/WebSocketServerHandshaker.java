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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base class for server side web socket opening and closing handshakes
 */
public abstract class WebSocketServerHandshaker {
    protected static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketServerHandshaker.class);

    private static final String[] EMPTY_ARRAY = new String[0];

    private final String uri;

    private final String[] subprotocols;

    private final WebSocketVersion version;

    private final int maxFramePayloadLength;

    private String selectedSubprotocol;

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
        this.version = version;
        this.uri = uri;
        if (subprotocols != null) {
            String[] subprotocolArray = StringUtil.split(subprotocols, ',');
            for (int i = 0; i < subprotocolArray.length; i++) {
                subprotocolArray[i] = subprotocolArray[i].trim();
            }
            this.subprotocols = subprotocolArray;
        } else {
            this.subprotocols = EMPTY_ARRAY;
        }
        this.maxFramePayloadLength = maxFramePayloadLength;
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
        return maxFramePayloadLength;
    }

    /**
     * Performs the opening handshake
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     */
    public ChannelFuture handshake(Channel channel, FullHttpRequest req) {
        return handshake(channel, req, null, channel.newPromise());
    }

    /**
     * Performs the opening handshake
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     * @param responseHeaders
     *            Extra headers to add to the handshake response or {@code null} if no extra headers should be added
     * @param promise
     *            the {@link ChannelPromise} to be notified when the opening handshake is done
     */
    public final ChannelFuture handshake(Channel channel, FullHttpRequest req,
                                            HttpHeaders responseHeaders, final ChannelPromise promise) {

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Channel %s WS Version %s server handshake", version(), channel.id()));
        }
        FullHttpResponse response = newHandshakeResponse(req, responseHeaders);
        channel.write(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ChannelPipeline p = future.channel().pipeline();
                    if (p.get(HttpObjectAggregator.class) != null) {
                        p.remove(HttpObjectAggregator.class);
                    }
                    ChannelHandlerContext ctx = p.context(HttpRequestDecoder.class);
                    if (ctx == null) {
                        // this means the user use a HttpServerCodec
                        ctx = p.context(HttpServerCodec.class);
                        if (ctx == null) {
                            promise.setFailure(
                                    new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline"));
                            return;
                        }
                        p.addBefore(ctx.name(), "wsencoder", newWebsocketDecoder());
                        p.replace(ctx.name(), "wsdecoder", newWebSocketEncoder());
                    } else {
                        p.replace(ctx.name(), "wsdecoder", newWebsocketDecoder());

                        p.replace(HttpResponseEncoder.class, "wsencoder", newWebSocketEncoder());
                    }
                    promise.setSuccess();
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
        return promise;
    }

    /**
     * Returns a new {@link FullHttpResponse) which will be used for as response to the handshake request.
     */
    protected abstract FullHttpResponse newHandshakeResponse(FullHttpRequest req,
                                         HttpHeaders responseHeaders);
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
        return channel.write(frame, promise).addListener(ChannelFutureListener.CLOSE);
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

        String[] requestedSubprotocolArray = StringUtil.split(requestedSubprotocols, ',');
        for (String p: requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol: subprotocols) {
                if (requestedSubprotocol.equals(supportedSubprotocol)) {
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

    protected void setSelectedSubprotocol(String value) {
        selectedSubprotocol = value;
    }

    /**
     * Returns the decoder to use after handshake is complete.
     */
    protected abstract ChannelInboundByteHandler newWebsocketDecoder();

    /**
     * Returns the encoder to use after the handshake is complete.
     */
    protected abstract ChannelOutboundMessageHandler<WebSocketFrame> newWebSocketEncoder();

}

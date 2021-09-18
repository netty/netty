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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * <p>
 * Performs server side opening and closing handshakes for web socket specification version <a
 * href="https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00" >draft-ietf-hybi-thewebsocketprotocol-
 * 00</a>
 * </p>
 * <p>
 * A very large portion of this code was taken from the Netty 3.2 HTTP example.
 * </p>
 */
public class WebSocketServerHandshaker00 extends WebSocketServerHandshaker {

    private static final Pattern BEGINNING_DIGIT = Pattern.compile("[^0-9]");
    private static final Pattern BEGINNING_SPACE = Pattern.compile("[^ ]");

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols
     * @param maxFramePayloadLength
     *            Maximum allowable frame payload length. Setting this value to your application's requirement may
     *            reduce denial of service attacks using long data frames.
     */
    public WebSocketServerHandshaker00(String webSocketURL, String subprotocols, int maxFramePayloadLength) {
        this(webSocketURL, subprotocols, WebSocketDecoderConfig.newBuilder()
            .maxFramePayloadLength(maxFramePayloadLength)
            .build());
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols
     * @param decoderConfig
     *            Frames decoder configuration.
     */
    public WebSocketServerHandshaker00(String webSocketURL, String subprotocols, WebSocketDecoderConfig decoderConfig) {
        super(WebSocketVersion.V00, webSocketURL, subprotocols, decoderConfig);
    }

    /**
     * <p>
     * Handle the web socket handshake for the web socket specification <a href=
     * "https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00">HyBi version 0</a> and lower. This standard
     * is really a rehash of <a href="https://tools.ietf.org/html/draft-hixie-thewebsocketprotocol-76" >hixie-76</a> and
     * <a href="https://tools.ietf.org/html/draft-hixie-thewebsocketprotocol-75" >hixie-75</a>.
     * </p>
     *
     * <p>
     * Browser request to the server:
     * </p>
     *
     * <pre>
     * GET /demo HTTP/1.1
     * Upgrade: WebSocket
     * Connection: Upgrade
     * Host: example.com
     * Origin: http://example.com
     * Sec-WebSocket-Protocol: chat, sample
     * Sec-WebSocket-Key1: 4 @1  46546xW%0l 1 5
     * Sec-WebSocket-Key2: 12998 5 Y3 1  .P00
     *
     * ^n:ds[4U
     * </pre>
     *
     * <p>
     * Server response:
     * </p>
     *
     * <pre>
     * HTTP/1.1 101 WebSocket Protocol Handshake
     * Upgrade: WebSocket
     * Connection: Upgrade
     * Sec-WebSocket-Origin: http://example.com
     * Sec-WebSocket-Location: ws://example.com/demo
     * Sec-WebSocket-Protocol: sample
     *
     * 8jKS'y:G*Co,Wxa-
     * </pre>
     */
    @Override
    protected FullHttpResponse newHandshakeResponse(FullHttpRequest req, HttpHeaders headers) {

        // Serve the WebSocket handshake request.
        if (!req.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                || !HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE))) {
            throw new WebSocketServerHandshakeException("not a WebSocket handshake request: missing upgrade", req);
        }

        // Hixie 75 does not contain these headers while Hixie 76 does
        boolean isHixie76 = req.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_KEY1) &&
                            req.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_KEY2);

        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        //throw before allocating FullHttpResponse
        if (origin == null && !isHixie76) {
            throw new WebSocketServerHandshakeException("Missing origin header, got only " + req.headers().names(),
                                                        req);
        }

        // Create the WebSocket handshake response.
        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, new HttpResponseStatus(101,
                isHixie76 ? "WebSocket Protocol Handshake" : "Web Socket Protocol Handshake"),
                req.content().alloc().buffer(0));
        if (headers != null) {
            res.headers().add(headers);
        }

        res.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                     .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);

        // Fill in the headers and contents depending on handshake getMethod.
        if (isHixie76) {
            // New handshake getMethod with a challenge:
            res.headers().add(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, origin);
            res.headers().add(HttpHeaderNames.SEC_WEBSOCKET_LOCATION, uri());

            String subprotocols = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
            if (subprotocols != null) {
                String selectedSubprotocol = selectSubprotocol(subprotocols);
                if (selectedSubprotocol == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Requested subprotocol(s) not supported: {}", subprotocols);
                    }
                } else {
                    res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, selectedSubprotocol);
                }
            }

            // Calculate the answer of the challenge.
            String key1 = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_KEY1);
            String key2 = req.headers().get(HttpHeaderNames.SEC_WEBSOCKET_KEY2);
            int a = (int) (Long.parseLong(BEGINNING_DIGIT.matcher(key1).replaceAll("")) /
                           BEGINNING_SPACE.matcher(key1).replaceAll("").length());
            int b = (int) (Long.parseLong(BEGINNING_DIGIT.matcher(key2).replaceAll("")) /
                           BEGINNING_SPACE.matcher(key2).replaceAll("").length());
            long c = req.content().readLong();
            ByteBuf input = Unpooled.wrappedBuffer(new byte[16]).setIndex(0, 0);
            input.writeInt(a);
            input.writeInt(b);
            input.writeLong(c);
            res.content().writeBytes(WebSocketUtil.md5(input.array()));
        } else {
            // Old Hixie 75 handshake getMethod with no challenge:
            res.headers().add(HttpHeaderNames.WEBSOCKET_ORIGIN, origin);
            res.headers().add(HttpHeaderNames.WEBSOCKET_LOCATION, uri());

            String protocol = req.headers().get(HttpHeaderNames.WEBSOCKET_PROTOCOL);
            if (protocol != null) {
                res.headers().set(HttpHeaderNames.WEBSOCKET_PROTOCOL, selectSubprotocol(protocol));
            }
        }
        return res;
    }

    /**
     * Echo back the closing frame
     *
     * @param channel
     *            the {@link Channel} to use.
     * @param frame
     *            Web Socket frame that was received.
     * @param promise
     *            the {@link ChannelPromise} to be notified when the closing handshake is done.
     */
    @Override
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame, ChannelPromise promise) {
        return channel.writeAndFlush(frame, promise);
    }

    /**
     * Echo back the closing frame
     *
     * @param ctx
     *            the {@link ChannelHandlerContext} to use.
     * @param frame
     *            Closing Frame that was received.
     * @param promise
     *            the {@link ChannelPromise} to be notified when the closing handshake is done.
     */
    @Override
    public ChannelFuture close(ChannelHandlerContext ctx, CloseWebSocketFrame frame,
                               ChannelPromise promise) {
        return ctx.writeAndFlush(frame, promise);
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket00FrameDecoder(decoderConfig());
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket00FrameEncoder();
    }
}

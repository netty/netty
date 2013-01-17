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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * <p>
 * Performs server side opening and closing handshakes for web socket specification version <a
 * href="http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-10" >draft-ietf-hybi-thewebsocketprotocol-
 * 10</a>
 * </p>
 */
public class WebSocketServerHandshaker07 extends WebSocketServerHandshaker {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketServerHandshaker07.class);

    public static final String WEBSOCKET_07_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final boolean allowExtensions;

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath".
     *            Subsequent web socket frames will be sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param maxFramePayloadLength
     *            Maximum allowable frame payload length. Setting this value to your application's
     *            requirement may reduce denial of service attacks using long data frames.
     */
    public WebSocketServerHandshaker07(
            String webSocketURL, String subprotocols, boolean allowExtensions, int maxFramePayloadLength) {
        super(WebSocketVersion.V07, webSocketURL, subprotocols, maxFramePayloadLength);
        this.allowExtensions = allowExtensions;
    }

    /**
     * <p>
     * Handle the web socket handshake for the web socket specification <a href=
     * "http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-07">HyBi version 7</a>.
     * </p>
     *
     * <p>
     * Browser request to the server:
     * </p>
     *
     * <pre>
     * GET /chat HTTP/1.1
     * Host: server.example.com
     * Upgrade: websocket
     * Connection: Upgrade
     * Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
     * Sec-WebSocket-Origin: http://example.com
     * Sec-WebSocket-Protocol: chat, superchat
     * Sec-WebSocket-Version: 7
     * </pre>
     *
     * <p>
     * Server response:
     * </p>
     *
     * <pre>
     * HTTP/1.1 101 Switching Protocols
     * Upgrade: websocket
     * Connection: Upgrade
     * Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
     * Sec-WebSocket-Protocol: chat
     * </pre>
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP request
     */
    @Override
    public ChannelFuture handshake(Channel channel, FullHttpRequest req, ChannelPromise promise) {

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Channel %s WS Version 7 server handshake", channel.id()));
        }

        FullHttpResponse res =
                new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);

        String key = req.headers().get(Names.SEC_WEBSOCKET_KEY);
        if (key == null) {
            throw new WebSocketHandshakeException("not a WebSocket request: missing key");
        }
        String acceptSeed = key + WEBSOCKET_07_ACCEPT_GUID;
        byte[] sha1 = WebSocketUtil.sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
        String accept = WebSocketUtil.base64(sha1);

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("WS Version 7 Server Handshake key: %s. Response: %s.", key, accept));
        }

        res.headers().add(Names.UPGRADE, WEBSOCKET.toLowerCase());
        res.headers().add(Names.CONNECTION, Names.UPGRADE);
        res.headers().add(Names.SEC_WEBSOCKET_ACCEPT, accept);
        String subprotocols = req.headers().get(Names.SEC_WEBSOCKET_PROTOCOL);
        if (subprotocols != null) {
            String selectedSubprotocol = selectSubprotocol(subprotocols);
            if (selectedSubprotocol == null) {
                throw new WebSocketHandshakeException("Requested subprotocol(s) not supported: " + subprotocols);
            } else {
                res.headers().add(Names.SEC_WEBSOCKET_PROTOCOL, selectedSubprotocol);
                setSelectedSubprotocol(selectedSubprotocol);
            }
        }

        channel.write(res, promise);

        // Upgrade the connection and send the handshake response.
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                ChannelPipeline p = future.channel().pipeline();
                if (p.get(HttpObjectAggregator.class) != null) {
                    p.remove(HttpObjectAggregator.class);
                }

                p.get(HttpRequestDecoder.class).replace("wsdecoder",
                        new WebSocket07FrameDecoder(true, allowExtensions, maxFramePayloadLength()));
                p.replace(HttpResponseEncoder.class, "wsencoder", new WebSocket07FrameEncoder(false));
            }
        });

        return promise;
    }

    /**
     * Echo back the closing frame and close the connection
     *
     * @param channel
     *            Channel
     * @param frame
     *            Web Socket frame that was received
     */
    @Override
    public ChannelFuture close(Channel channel, CloseWebSocketFrame frame, ChannelPromise future) {
        future.addListener(ChannelFutureListener.CLOSE);
        return channel.write(frame, future);
    }
}

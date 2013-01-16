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
import io.netty.handler.codec.http.DefaultHttpRequestWithContent;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpRequestWithContent;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpResponseWithContent;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.Map;

/**
 * <p>
 * Performs client side opening and closing handshakes for web socket specification version <a
 * href="http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-07" >draft-ietf-hybi-thewebsocketprotocol-
 * 10</a>
 * </p>
 */
public class WebSocketClientHandshaker07 extends WebSocketClientHandshaker {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketClientHandshaker07.class);

    public static final String MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private String expectedChallengeResponseString;

    private final boolean allowExtensions;

    /**
     * Creates a new instance.
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    public WebSocketClientHandshaker07(URI webSocketURL, WebSocketVersion version, String subprotocol,
            boolean allowExtensions, Map<String, String> customHeaders, int maxFramePayloadLength) {
        super(webSocketURL, version, subprotocol, customHeaders, maxFramePayloadLength);
        this.allowExtensions = allowExtensions;
    }

    /**
     * /**
     * <p>
     * Sends the opening request to the server:
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
     * @param channel
     *            Channel into which we can write our request
     */
    @Override
    public ChannelFuture handshake(Channel channel, final ChannelPromise promise) {
        // Get path
        URI wsURL = getWebSocketUrl();
        String path = wsURL.getPath();
        if (wsURL.getQuery() != null && !wsURL.getQuery().isEmpty()) {
            path = wsURL.getPath() + '?' + wsURL.getQuery();
        }

        if (path == null || path.isEmpty()) {
            path = "/";
        }

        // Get 16 bit nonce and base 64 encode it
        byte[] nonce = WebSocketUtil.randomBytes(16);
        String key = WebSocketUtil.base64(nonce);

        String acceptSeed = key + MAGIC_GUID;
        byte[] sha1 = WebSocketUtil.sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
        expectedChallengeResponseString = WebSocketUtil.base64(sha1);

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("WS Version 07 Client Handshake key: %s. Expected response: %s.", key,
                    expectedChallengeResponseString));
        }

        // Format request
        HttpRequestWithContent request = new DefaultHttpRequestWithContent(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        request.headers().add(Names.UPGRADE, Values.WEBSOCKET.toLowerCase());
        request.headers().add(Names.CONNECTION, Values.UPGRADE);
        request.headers().add(Names.SEC_WEBSOCKET_KEY, key);
        request.headers().add(Names.HOST, wsURL.getHost());

        int wsPort = wsURL.getPort();
        String originValue = "http://" + wsURL.getHost();
        if (wsPort != 80 && wsPort != 443) {
            // if the port is not standard (80/443) its needed to add the port to the header.
            // See http://tools.ietf.org/html/rfc6454#section-6.2
            originValue = originValue + ':' + wsPort;
        }
        request.headers().add(Names.SEC_WEBSOCKET_ORIGIN, originValue);

        String expectedSubprotocol = getExpectedSubprotocol();
        if (expectedSubprotocol != null && !expectedSubprotocol.isEmpty()) {
            request.headers().add(Names.SEC_WEBSOCKET_PROTOCOL, expectedSubprotocol);
        }

        request.headers().add(Names.SEC_WEBSOCKET_VERSION, "7");

        if (customHeaders != null) {
            for (Map.Entry<String, String> e : customHeaders.entrySet()) {
                request.headers().add(e.getKey(), e.getValue());
            }
        }

        ChannelFuture future = channel.write(request);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                ChannelPipeline p = future.channel().pipeline();
                p.addAfter(
                        p.context(HttpRequestEncoder.class).name(),
                        "ws-encoder", new WebSocket07FrameEncoder(true));

                if (future.isSuccess()) {
                    promise.setSuccess();
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });

        return promise;
    }

    /**
     * <p>
     * Process server response:
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
     * @param response
     *            HTTP response returned from the server for the request sent by beginOpeningHandshake00().
     * @throws WebSocketHandshakeException
     */
    @Override
    public void finishHandshake(Channel channel, HttpResponseWithContent response) {
        final HttpResponseStatus status = HttpResponseStatus.SWITCHING_PROTOCOLS;

        if (!response.status().equals(status)) {
            throw new WebSocketHandshakeException("Invalid handshake response status: " + response.status());
        }

        String upgrade = response.headers().get(Names.UPGRADE);
        if (!Values.WEBSOCKET.equalsIgnoreCase(upgrade)) {
            throw new WebSocketHandshakeException("Invalid handshake response upgrade: "
                    + response.headers().get(Names.UPGRADE));
        }

        String connection = response.headers().get(Names.CONNECTION);
        if (!Values.UPGRADE.equalsIgnoreCase(connection)) {
            throw new WebSocketHandshakeException("Invalid handshake response connection: "
                    + response.headers().get(Names.CONNECTION));
        }

        String accept = response.headers().get(Names.SEC_WEBSOCKET_ACCEPT);
        if (accept == null || !accept.equals(expectedChallengeResponseString)) {
            throw new WebSocketHandshakeException(String.format("Invalid challenge. Actual: %s. Expected: %s", accept,
                    expectedChallengeResponseString));
        }

        String subprotocol = response.headers().get(Names.SEC_WEBSOCKET_PROTOCOL);
        setActualSubprotocol(subprotocol);

        setHandshakeComplete();

        ChannelPipeline p = channel.pipeline();
        p.get(HttpResponseDecoder.class).replace(
                "ws-decoder",
                new WebSocket07FrameDecoder(false, allowExtensions, getMaxFramePayloadLength()));
    }
}

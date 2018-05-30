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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.URI;

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
    private final boolean performMasking;
    private final boolean allowMaskMismatch;

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
                                       boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength) {
        this(webSocketURL, version, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength, true, false);
    }

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
     * @param performMasking
     *            Whether to mask all written websocket frames. This must be set to true in order to be fully compatible
     *            with the websocket specifications. Client applications that communicate with a non-standard server
     *            which doesn't require masking might set this to false to achieve a higher performance.
     * @param allowMaskMismatch
     *            When set to true, frames which are not masked properly according to the standard will still be
     *            accepted.
     */
    public WebSocketClientHandshaker07(URI webSocketURL, WebSocketVersion version, String subprotocol,
            boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength,
            boolean performMasking, boolean allowMaskMismatch) {
        super(webSocketURL, version, subprotocol, customHeaders, maxFramePayloadLength);
        this.allowExtensions = allowExtensions;
        this.performMasking = performMasking;
        this.allowMaskMismatch = allowMaskMismatch;
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
     */
    @Override
    protected FullHttpRequest newHandshakeRequest() {
        // Get path
        URI wsURL = uri();
        String path = rawPath(wsURL);

        // Get 16 bit nonce and base 64 encode it
        byte[] nonce = WebSocketUtil.randomBytes(16);
        String key = WebSocketUtil.base64(nonce);

        String acceptSeed = key + MAGIC_GUID;
        byte[] sha1 = WebSocketUtil.sha1(acceptSeed.getBytes(CharsetUtil.US_ASCII));
        expectedChallengeResponseString = WebSocketUtil.base64(sha1);

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "WebSocket version 07 client handshake key: {}, expected response: {}",
                    key, expectedChallengeResponseString);
        }

        // Format request
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        HttpHeaders headers = request.headers();

        if (customHeaders != null) {
            headers.add(customHeaders);
        }

        headers.set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, key)
               .set(HttpHeaderNames.HOST, websocketHostValue(wsURL))
               .set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, websocketOriginValue(wsURL));

        String expectedSubprotocol = expectedSubprotocol();
        if (expectedSubprotocol != null && !expectedSubprotocol.isEmpty()) {
            headers.set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, expectedSubprotocol);
        }

        headers.set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "7");
        return request;
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
     * @param response
     *            HTTP response returned from the server for the request sent by beginOpeningHandshake00().
     * @throws WebSocketHandshakeException
     */
    @Override
    protected void verify(FullHttpResponse response) {
        final HttpResponseStatus status = HttpResponseStatus.SWITCHING_PROTOCOLS;
        final HttpHeaders headers = response.headers();

        if (!response.status().equals(status)) {
            throw new WebSocketHandshakeException("Invalid handshake response getStatus: " + response.status());
        }

        CharSequence upgrade = headers.get(HttpHeaderNames.UPGRADE);
        if (!HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgrade)) {
            throw new WebSocketHandshakeException("Invalid handshake response upgrade: " + upgrade);
        }

        if (!headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) {
            throw new WebSocketHandshakeException("Invalid handshake response connection: "
                    + headers.get(HttpHeaderNames.CONNECTION));
        }

        CharSequence accept = headers.get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT);
        if (accept == null || !accept.equals(expectedChallengeResponseString)) {
            throw new WebSocketHandshakeException(String.format(
                    "Invalid challenge. Actual: %s. Expected: %s", accept, expectedChallengeResponseString));
        }
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket07FrameDecoder(false, allowExtensions, maxFramePayloadLength(), allowMaskMismatch);
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket07FrameEncoder(performMasking);
    }
}

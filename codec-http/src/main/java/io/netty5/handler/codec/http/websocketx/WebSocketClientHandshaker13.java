/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.internal.StringUtil;

import java.net.URI;

/**
 * <p>
 * Performs client side opening and closing handshakes for web socket specification version
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">websocketprotocol-v13</a>
 * </p>
 */
public class WebSocketClientHandshaker13 extends WebSocketClientHandshaker {

    private final boolean allowExtensions;
    private final boolean performMasking;
    private final boolean allowMaskMismatch;

    private volatile String sentNonce;

    /**
     * Creates a new instance.
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param allowExtensions
     *            Allow extensions to be used in the reserved bits of the web socket frame
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    public WebSocketClientHandshaker13(URI webSocketURL, String subprotocol,
                                       boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength) {
        this(webSocketURL, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength,
                true, false);
    }

    /**
     * Creates a new instance.
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
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
    public WebSocketClientHandshaker13(URI webSocketURL, String subprotocol,
            boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength,
            boolean performMasking, boolean allowMaskMismatch) {
        this(webSocketURL, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength,
                performMasking, allowMaskMismatch, DEFAULT_FORCE_CLOSE_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new instance.
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
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
     *            accepted
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified.
     */
    public WebSocketClientHandshaker13(URI webSocketURL, String subprotocol,
                                       boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength,
                                       boolean performMasking, boolean allowMaskMismatch,
                                       long forceCloseTimeoutMillis) {
        this(webSocketURL, subprotocol, allowExtensions, customHeaders, maxFramePayloadLength, performMasking,
                allowMaskMismatch, forceCloseTimeoutMillis, false);
    }

    /**
     * Creates a new instance.
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
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
     *            accepted
     * @param forceCloseTimeoutMillis
     *            Close the connection if it was not closed by the server after timeout specified.
     * @param  absoluteUpgradeUrl
     *            Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over
     *            clear HTTP
     */
    WebSocketClientHandshaker13(URI webSocketURL, String subprotocol,
                                boolean allowExtensions, HttpHeaders customHeaders, int maxFramePayloadLength,
                                boolean performMasking, boolean allowMaskMismatch,
                                long forceCloseTimeoutMillis, boolean absoluteUpgradeUrl) {
        super(webSocketURL, WebSocketVersion.V13, subprotocol, customHeaders, maxFramePayloadLength,
              forceCloseTimeoutMillis, absoluteUpgradeUrl);
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
     * Sec-WebSocket-Protocol: chat, superchat
     * Sec-WebSocket-Version: 13
     * </pre>
     *
     */
    @Override
    protected FullHttpRequest newHandshakeRequest(BufferAllocator allocator) {
        URI wsURL = uri();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, upgradeUrl(wsURL),
                allocator.allocate(0));
        HttpHeaders headers = request.headers();

        if (customHeaders != null) {
            headers.add(customHeaders);
            if (!headers.contains(HttpHeaderNames.HOST)) {
                // Only add HOST header if customHeaders did not contain it.
                // See https://github.com/netty/netty/issues/10101.
                headers.set(HttpHeaderNames.HOST, websocketHostValue(wsURL));
            }
        } else {
            headers.set(HttpHeaderNames.HOST, websocketHostValue(wsURL));
        }

        String nonce = createNonce();
        headers.set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, nonce);

        sentNonce = nonce;
        String expectedSubprotocol = expectedSubprotocol();
        if (!StringUtil.isNullOrEmpty(expectedSubprotocol)) {
            headers.set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, expectedSubprotocol);
        }

        headers.set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, version().toAsciiString());
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
     * @throws WebSocketHandshakeException if handshake response is invalid.
     */
    @Override
    protected void verify(FullHttpResponse response) {
        HttpResponseStatus status = response.status();
        if (!HttpResponseStatus.SWITCHING_PROTOCOLS.equals(status)) {
            throw new WebSocketClientHandshakeException("Invalid handshake response status: " + status, response);
        }

        HttpHeaders headers = response.headers();
        CharSequence upgrade = headers.get(HttpHeaderNames.UPGRADE);
        if (!HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgrade)) {
            throw new WebSocketClientHandshakeException("Invalid handshake response upgrade: " + upgrade, response);
        }

        if (!headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) {
            throw new WebSocketClientHandshakeException("Invalid handshake response connection: "
                    + headers.get(HttpHeaderNames.CONNECTION), response);
        }

        String accept = headers.get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT);
        if (accept == null) {
            throw new WebSocketClientHandshakeException("Invalid handshake response sec-websocket-accept: null",
                                                        response);
        }

        String expectedAccept = WebSocketUtil.calculateV13Accept(sentNonce);
        if (!expectedAccept.equals(accept.trim())) {
            throw new WebSocketClientHandshakeException("Invalid handshake response sec-websocket-accept: " + accept +
                                                        ", expected: " + expectedAccept, response);
        }
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket13FrameDecoder(false, allowExtensions, maxFramePayloadLength(), allowMaskMismatch);
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket13FrameEncoder(performMasking);
    }

    @Override
    public WebSocketClientHandshaker13 setForceCloseTimeoutMillis(long forceCloseTimeoutMillis) {
        super.setForceCloseTimeoutMillis(forceCloseTimeoutMillis);
        return this;
    }

    /**
     * Creates a nonce consisting of a randomly selected 16-byte value
     * that has been base64-encoded.
     */
    private static String createNonce() {
        var nonce = WebSocketUtil.randomBytes(16);
        return WebSocketUtil.base64(nonce);
    }
}

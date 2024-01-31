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

import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.util.internal.ObjectUtil;

import java.net.URI;

import static io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig.DEFAULT_HANDSHAKE_TIMEOUT_MILLIS;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * WebSocket server configuration.
 */
public final class WebSocketClientProtocolConfig {

    static final boolean DEFAULT_PERFORM_MASKING = true;
    static final boolean DEFAULT_ALLOW_MASK_MISMATCH = false;
    static final boolean DEFAULT_HANDLE_CLOSE_FRAMES = true;
    static final boolean DEFAULT_DROP_PONG_FRAMES = true;
    static final boolean DEFAULT_GENERATE_ORIGIN_HEADER = true;
    static final boolean DEFAULT_WITH_UTF8_VALIDATOR = true;

    private final URI webSocketUri;
    private final String subprotocol;
    private final WebSocketVersion version;
    private final boolean allowExtensions;
    private final HttpHeaders customHeaders;
    private final int maxFramePayloadLength;
    private final boolean performMasking;
    private final boolean allowMaskMismatch;
    private final boolean handleCloseFrames;
    private final WebSocketCloseStatus sendCloseFrame;
    private final boolean dropPongFrames;
    private final long handshakeTimeoutMillis;
    private final long forceCloseTimeoutMillis;
    private final boolean absoluteUpgradeUrl;
    private final boolean generateOriginHeader;
    private final boolean withUTF8Validator;

    private WebSocketClientProtocolConfig(
        URI webSocketUri,
        String subprotocol,
        WebSocketVersion version,
        boolean allowExtensions,
        HttpHeaders customHeaders,
        int maxFramePayloadLength,
        boolean performMasking,
        boolean allowMaskMismatch,
        boolean handleCloseFrames,
        WebSocketCloseStatus sendCloseFrame,
        boolean dropPongFrames,
        long handshakeTimeoutMillis,
        long forceCloseTimeoutMillis,
        boolean absoluteUpgradeUrl,
        boolean generateOriginHeader,
        boolean withUTF8Validator
    ) {
        this.webSocketUri = webSocketUri;
        this.subprotocol = subprotocol;
        this.version = version;
        this.allowExtensions = allowExtensions;
        this.customHeaders = customHeaders;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.performMasking = performMasking;
        this.allowMaskMismatch = allowMaskMismatch;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        this.handleCloseFrames = handleCloseFrames;
        this.sendCloseFrame = sendCloseFrame;
        this.dropPongFrames = dropPongFrames;
        this.handshakeTimeoutMillis = checkPositive(handshakeTimeoutMillis, "handshakeTimeoutMillis");
        this.absoluteUpgradeUrl = absoluteUpgradeUrl;
        this.generateOriginHeader = generateOriginHeader;
        this.withUTF8Validator = withUTF8Validator;
    }

    public URI webSocketUri() {
        return webSocketUri;
    }

    public String subprotocol() {
        return subprotocol;
    }

    public WebSocketVersion version() {
        return version;
    }

    public boolean allowExtensions() {
        return allowExtensions;
    }

    public HttpHeaders customHeaders() {
        return customHeaders;
    }

    public int maxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public boolean performMasking() {
        return performMasking;
    }

    public boolean allowMaskMismatch() {
        return allowMaskMismatch;
    }

    public boolean handleCloseFrames() {
        return handleCloseFrames;
    }

    public WebSocketCloseStatus sendCloseFrame() {
        return sendCloseFrame;
    }

    public boolean dropPongFrames() {
        return dropPongFrames;
    }

    public long handshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public long forceCloseTimeoutMillis() {
        return forceCloseTimeoutMillis;
    }

    public boolean absoluteUpgradeUrl() {
        return absoluteUpgradeUrl;
    }

    public boolean generateOriginHeader() {
        return generateOriginHeader;
    }

    public boolean withUTF8Validator() {
        return withUTF8Validator;
    }

    @Override
    public String toString() {
        return "WebSocketClientProtocolConfig" +
               " {webSocketUri=" + webSocketUri +
               ", subprotocol=" + subprotocol +
               ", version=" + version +
               ", allowExtensions=" + allowExtensions +
               ", customHeaders=" + customHeaders +
               ", maxFramePayloadLength=" + maxFramePayloadLength +
               ", performMasking=" + performMasking +
               ", allowMaskMismatch=" + allowMaskMismatch +
               ", handleCloseFrames=" + handleCloseFrames +
               ", sendCloseFrame=" + sendCloseFrame +
               ", dropPongFrames=" + dropPongFrames +
               ", handshakeTimeoutMillis=" + handshakeTimeoutMillis +
               ", forceCloseTimeoutMillis=" + forceCloseTimeoutMillis +
               ", absoluteUpgradeUrl=" + absoluteUpgradeUrl +
               ", generateOriginHeader=" + generateOriginHeader +
               "}";
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder newBuilder() {
        return new Builder(
                URI.create("https://localhost/"),
                null,
                WebSocketVersion.V13,
                false,
                EmptyHttpHeaders.INSTANCE,
                65536,
                DEFAULT_PERFORM_MASKING,
                DEFAULT_ALLOW_MASK_MISMATCH,
                DEFAULT_HANDLE_CLOSE_FRAMES,
                WebSocketCloseStatus.NORMAL_CLOSURE,
                DEFAULT_DROP_PONG_FRAMES,
                DEFAULT_HANDSHAKE_TIMEOUT_MILLIS,
                -1,
                false,
                DEFAULT_GENERATE_ORIGIN_HEADER,
                DEFAULT_WITH_UTF8_VALIDATOR);
    }

    public static final class Builder {
        private URI webSocketUri;
        private String subprotocol;
        private WebSocketVersion version;
        private boolean allowExtensions;
        private HttpHeaders customHeaders;
        private int maxFramePayloadLength;
        private boolean performMasking;
        private boolean allowMaskMismatch;
        private boolean handleCloseFrames;
        private WebSocketCloseStatus sendCloseFrame;
        private boolean dropPongFrames;
        private long handshakeTimeoutMillis;
        private long forceCloseTimeoutMillis;
        private boolean absoluteUpgradeUrl;
        private boolean generateOriginHeader;
        private boolean withUTF8Validator;

        private Builder(WebSocketClientProtocolConfig clientConfig) {
            this(ObjectUtil.checkNotNull(clientConfig, "clientConfig").webSocketUri(),
                 clientConfig.subprotocol(),
                 clientConfig.version(),
                 clientConfig.allowExtensions(),
                 clientConfig.customHeaders(),
                 clientConfig.maxFramePayloadLength(),
                 clientConfig.performMasking(),
                 clientConfig.allowMaskMismatch(),
                 clientConfig.handleCloseFrames(),
                 clientConfig.sendCloseFrame(),
                 clientConfig.dropPongFrames(),
                 clientConfig.handshakeTimeoutMillis(),
                 clientConfig.forceCloseTimeoutMillis(),
                 clientConfig.absoluteUpgradeUrl(),
                 clientConfig.generateOriginHeader(),
                 clientConfig.withUTF8Validator());
        }

        private Builder(URI webSocketUri,
                        String subprotocol,
                        WebSocketVersion version,
                        boolean allowExtensions,
                        HttpHeaders customHeaders,
                        int maxFramePayloadLength,
                        boolean performMasking,
                        boolean allowMaskMismatch,
                        boolean handleCloseFrames,
                        WebSocketCloseStatus sendCloseFrame,
                        boolean dropPongFrames,
                        long handshakeTimeoutMillis,
                        long forceCloseTimeoutMillis,
                        boolean absoluteUpgradeUrl,
                        boolean generateOriginHeader,
                        boolean withUTF8Validator) {
            this.webSocketUri = webSocketUri;
            this.subprotocol = subprotocol;
            this.version = version;
            this.allowExtensions = allowExtensions;
            this.customHeaders = customHeaders;
            this.maxFramePayloadLength = maxFramePayloadLength;
            this.performMasking = performMasking;
            this.allowMaskMismatch = allowMaskMismatch;
            this.handleCloseFrames = handleCloseFrames;
            this.sendCloseFrame = sendCloseFrame;
            this.dropPongFrames = dropPongFrames;
            this.handshakeTimeoutMillis = handshakeTimeoutMillis;
            this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
            this.absoluteUpgradeUrl = absoluteUpgradeUrl;
            this.generateOriginHeader = generateOriginHeader;
            this.withUTF8Validator = withUTF8Validator;
        }

        /**
         * URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
         * sent to this URL.
         */
        public Builder webSocketUri(String webSocketUri) {
            return webSocketUri(URI.create(webSocketUri));
        }

        /**
         * URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
         * sent to this URL.
         */
        public Builder webSocketUri(URI webSocketUri) {
            this.webSocketUri = webSocketUri;
            return this;
        }

        /**
         * Sub protocol request sent to the server.
         */
        public Builder subprotocol(String subprotocol) {
            this.subprotocol = subprotocol;
            return this;
        }

        /**
         * Version of web socket specification to use to connect to the server
         */
        public Builder version(WebSocketVersion version) {
            this.version = version;
            return this;
        }

        /**
         * Allow extensions to be used in the reserved bits of the web socket frame
         */
        public Builder allowExtensions(boolean allowExtensions) {
            this.allowExtensions = allowExtensions;
            return this;
        }

        /**
         * Map of custom headers to add to the client request
         */
        public Builder customHeaders(HttpHeaders customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * Maximum length of a frame's payload
         */
        public Builder maxFramePayloadLength(int maxFramePayloadLength) {
            this.maxFramePayloadLength = maxFramePayloadLength;
            return this;
        }

        /**
         * Whether to mask all written websocket frames. This must be set to true in order to be fully compatible
         * with the websocket specifications. Client applications that communicate with a non-standard server
         * which doesn't require masking might set this to false to achieve a higher performance.
         */
        public Builder performMasking(boolean performMasking) {
            this.performMasking = performMasking;
            return this;
        }

        /**
         * When set to true, frames which are not masked properly according to the standard will still be accepted.
         */
        public Builder allowMaskMismatch(boolean allowMaskMismatch) {
            this.allowMaskMismatch = allowMaskMismatch;
            return this;
        }

        /**
         * {@code true} if close frames should not be forwarded and just close the channel
         */
        public Builder handleCloseFrames(boolean handleCloseFrames) {
            this.handleCloseFrames = handleCloseFrames;
            return this;
        }

        /**
         * Close frame to send, when close frame was not send manually. Or {@code null} to disable proper close.
         */
        public Builder sendCloseFrame(WebSocketCloseStatus sendCloseFrame) {
            this.sendCloseFrame = sendCloseFrame;
            return this;
        }

        /**
         * {@code true} if pong frames should not be forwarded
         */
        public Builder dropPongFrames(boolean dropPongFrames) {
            this.dropPongFrames = dropPongFrames;
            return this;
        }

        /**
         * Handshake timeout in mills, when handshake timeout, will trigger user
         * event {@link ClientHandshakeStateEvent#HANDSHAKE_TIMEOUT}
         */
        public Builder handshakeTimeoutMillis(long handshakeTimeoutMillis) {
            this.handshakeTimeoutMillis = handshakeTimeoutMillis;
            return this;
        }

        /**
         * Close the connection if it was not closed by the server after timeout specified
         */
        public Builder forceCloseTimeoutMillis(long forceCloseTimeoutMillis) {
            this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
            return this;
        }

        /**
         * Use an absolute url for the Upgrade request, typically when connecting through an HTTP proxy over clear HTTP
         */
        public Builder absoluteUpgradeUrl(boolean absoluteUpgradeUrl) {
            this.absoluteUpgradeUrl = absoluteUpgradeUrl;
            return this;
        }

        /**
         * Allows to generate the `Origin`|`Sec-WebSocket-Origin` header value for handshake request
         * according the given webSocketURI. Usually it's not necessary and can be disabled,
         * but for backward compatibility is set to {@code true} as default.
         */
        public Builder generateOriginHeader(boolean generateOriginHeader) {
            this.generateOriginHeader = generateOriginHeader;
            return this;
        }

        /**
         * Toggles UTF8 validation for payload of text websocket frames. By default validation is enabled.
         */
        public Builder withUTF8Validator(boolean withUTF8Validator) {
            this.withUTF8Validator = withUTF8Validator;
            return this;
        }

        /**
         * Build unmodifiable client protocol configuration.
         */
        public WebSocketClientProtocolConfig build() {
            return new WebSocketClientProtocolConfig(
                webSocketUri,
                subprotocol,
                version,
                allowExtensions,
                customHeaders,
                maxFramePayloadLength,
                performMasking,
                allowMaskMismatch,
                handleCloseFrames,
                sendCloseFrame,
                dropPongFrames,
                handshakeTimeoutMillis,
                forceCloseTimeoutMillis,
                absoluteUpgradeUrl,
                generateOriginHeader,
                withUTF8Validator
            );
        }
    }
}

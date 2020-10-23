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

import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.util.internal.ObjectUtil;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * WebSocket server configuration.
 */
public final class WebSocketServerProtocolConfig {

    static final long DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 10000L;

    private final String websocketPath;
    private final String subprotocols;
    private final boolean checkStartsWith;
    private final long handshakeTimeoutMillis;
    private final long forceCloseTimeoutMillis;
    private final boolean handleCloseFrames;
    private final WebSocketCloseStatus sendCloseFrame;
    private final boolean dropPongFrames;
    private final WebSocketDecoderConfig decoderConfig;

    private WebSocketServerProtocolConfig(
        String websocketPath,
        String subprotocols,
        boolean checkStartsWith,
        long handshakeTimeoutMillis,
        long forceCloseTimeoutMillis,
        boolean handleCloseFrames,
        WebSocketCloseStatus sendCloseFrame,
        boolean dropPongFrames,
        WebSocketDecoderConfig decoderConfig
    ) {
        this.websocketPath = websocketPath;
        this.subprotocols = subprotocols;
        this.checkStartsWith = checkStartsWith;
        this.handshakeTimeoutMillis = checkPositive(handshakeTimeoutMillis, "handshakeTimeoutMillis");
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        this.handleCloseFrames = handleCloseFrames;
        this.sendCloseFrame = sendCloseFrame;
        this.dropPongFrames = dropPongFrames;
        this.decoderConfig = decoderConfig == null ? WebSocketDecoderConfig.DEFAULT : decoderConfig;
    }

    public String websocketPath() {
        return websocketPath;
    }

    public String subprotocols() {
        return subprotocols;
    }

    public boolean checkStartsWith() {
        return checkStartsWith;
    }

    public long handshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public long forceCloseTimeoutMillis() {
        return forceCloseTimeoutMillis;
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

    public WebSocketDecoderConfig decoderConfig() {
        return decoderConfig;
    }

    @Override
    public String toString() {
        return "WebSocketServerProtocolConfig" +
            " {websocketPath=" + websocketPath +
            ", subprotocols=" + subprotocols +
            ", checkStartsWith=" + checkStartsWith +
            ", handshakeTimeoutMillis=" + handshakeTimeoutMillis +
            ", forceCloseTimeoutMillis=" + forceCloseTimeoutMillis +
            ", handleCloseFrames=" + handleCloseFrames +
            ", sendCloseFrame=" + sendCloseFrame +
            ", dropPongFrames=" + dropPongFrames +
            ", decoderConfig=" + decoderConfig +
            "}";
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder newBuilder() {
        return new Builder("/", null, false, DEFAULT_HANDSHAKE_TIMEOUT_MILLIS, 0L,
                           true, WebSocketCloseStatus.NORMAL_CLOSURE, true, WebSocketDecoderConfig.DEFAULT);
    }

    public static final class Builder {
        private String websocketPath;
        private String subprotocols;
        private boolean checkStartsWith;
        private long handshakeTimeoutMillis;
        private long forceCloseTimeoutMillis;
        private boolean handleCloseFrames;
        private WebSocketCloseStatus sendCloseFrame;
        private boolean dropPongFrames;
        private WebSocketDecoderConfig decoderConfig;
        private WebSocketDecoderConfig.Builder decoderConfigBuilder;

        private Builder(WebSocketServerProtocolConfig serverConfig) {
            this(ObjectUtil.checkNotNull(serverConfig, "serverConfig").websocketPath(),
                 serverConfig.subprotocols(),
                 serverConfig.checkStartsWith(),
                 serverConfig.handshakeTimeoutMillis(),
                 serverConfig.forceCloseTimeoutMillis(),
                 serverConfig.handleCloseFrames(),
                 serverConfig.sendCloseFrame(),
                 serverConfig.dropPongFrames(),
                 serverConfig.decoderConfig()
            );
        }

        private Builder(String websocketPath,
                        String subprotocols,
                        boolean checkStartsWith,
                        long handshakeTimeoutMillis,
                        long forceCloseTimeoutMillis,
                        boolean handleCloseFrames,
                        WebSocketCloseStatus sendCloseFrame,
                        boolean dropPongFrames,
                        WebSocketDecoderConfig decoderConfig) {
            this.websocketPath = websocketPath;
            this.subprotocols = subprotocols;
            this.checkStartsWith = checkStartsWith;
            this.handshakeTimeoutMillis = handshakeTimeoutMillis;
            this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
            this.handleCloseFrames = handleCloseFrames;
            this.sendCloseFrame = sendCloseFrame;
            this.dropPongFrames = dropPongFrames;
            this.decoderConfig = decoderConfig;
        }

        /**
         * URI path component to handle websocket upgrade requests on.
         */
        public Builder websocketPath(String websocketPath) {
            this.websocketPath = websocketPath;
            return this;
        }

        /**
         * CSV of supported protocols
         */
        public Builder subprotocols(String subprotocols) {
            this.subprotocols = subprotocols;
            return this;
        }

        /**
         * {@code true} to handle all requests, where URI path component starts from
         * {@link WebSocketServerProtocolConfig#websocketPath()}, {@code false} for exact match (default).
         */
        public Builder checkStartsWith(boolean checkStartsWith) {
            this.checkStartsWith = checkStartsWith;
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
         * Close the connection if it was not closed by the client after timeout specified
         */
        public Builder forceCloseTimeoutMillis(long forceCloseTimeoutMillis) {
            this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
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
         * Frames decoder configuration.
         */
        public Builder decoderConfig(WebSocketDecoderConfig decoderConfig) {
            this.decoderConfig = decoderConfig == null ? WebSocketDecoderConfig.DEFAULT : decoderConfig;
            this.decoderConfigBuilder = null;
            return this;
        }

        private WebSocketDecoderConfig.Builder decoderConfigBuilder() {
            if (decoderConfigBuilder == null) {
                decoderConfigBuilder = decoderConfig.toBuilder();
            }
            return decoderConfigBuilder;
        }

        public Builder maxFramePayloadLength(int maxFramePayloadLength) {
            decoderConfigBuilder().maxFramePayloadLength(maxFramePayloadLength);
            return this;
        }

        public Builder expectMaskedFrames(boolean expectMaskedFrames) {
            decoderConfigBuilder().expectMaskedFrames(expectMaskedFrames);
            return this;
        }

        public Builder allowMaskMismatch(boolean allowMaskMismatch) {
            decoderConfigBuilder().allowMaskMismatch(allowMaskMismatch);
            return this;
        }

        public Builder allowExtensions(boolean allowExtensions) {
            decoderConfigBuilder().allowExtensions(allowExtensions);
            return this;
        }

        public Builder closeOnProtocolViolation(boolean closeOnProtocolViolation) {
            decoderConfigBuilder().closeOnProtocolViolation(closeOnProtocolViolation);
            return this;
        }

        public Builder withUTF8Validator(boolean withUTF8Validator) {
            decoderConfigBuilder().withUTF8Validator(withUTF8Validator);
            return this;
        }

        /**
         * Build unmodifiable server protocol configuration.
         */
        public WebSocketServerProtocolConfig build() {
            return new WebSocketServerProtocolConfig(
                websocketPath,
                subprotocols,
                checkStartsWith,
                handshakeTimeoutMillis,
                forceCloseTimeoutMillis,
                handleCloseFrames,
                sendCloseFrame,
                dropPongFrames,
                decoderConfigBuilder == null ? decoderConfig : decoderConfigBuilder.build()
            );
        }
    }
}

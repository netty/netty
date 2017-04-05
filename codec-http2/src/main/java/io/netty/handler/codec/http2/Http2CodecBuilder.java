/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.logging.LogLevel.INFO;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A builder for {@link Http2Codec}.
 */
@UnstableApi
public final class Http2CodecBuilder {
    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new Http2FrameLogger(INFO, Http2Codec.class);

    private final Http2StreamChannelBootstrap bootstrap;
    private final boolean server;
    private Http2Settings initialSettings;
    private Http2FrameLogger frameLogger;
    private SensitivityDetector headersSensitivityDetector;

    /**
     * Creates a new {@link Http2Codec} builder.
     *
     * @param server {@code true} this is a server
     * @param streamHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}. {@code null} if the event loop from the parent channel should be used.
     */
    public Http2CodecBuilder(boolean server, ChannelHandler streamHandler) {
        this(server, new Http2StreamChannelBootstrap().handler(streamHandler));
    }

    /**
     * Creates a new {@link Http2Codec} builder.
     *
     * @param server {@code true} this is a server
     * @param bootstrap bootstrap used to instantiate child channels for remotely-created streams.
     */
    public Http2CodecBuilder(boolean server, Http2StreamChannelBootstrap bootstrap) {
        this.bootstrap = checkNotNull(bootstrap, "bootstrap");
        this.server = server;
        this.initialSettings = Http2Settings.defaultSettings();
        this.frameLogger = HTTP2_FRAME_LOGGER;
        this.headersSensitivityDetector = null;
    }

    /**
     * Specifies the initial settings to send to peer.
     *
     * @param initialSettings non default initial settings to send to peer
     * @return {@link Http2CodecBuilder} the builder for the {@link Http2Codec}
     */
    public Http2CodecBuilder initialSettings(Http2Settings initialSettings) {
        this.initialSettings = initialSettings;
        return this;
    }

    /**
     * Returns the initial settings to send to peer.
     */
    public Http2Settings initialSettings() {
        return initialSettings;
    }

    /**
     * Specifies the frame logger to log messages with.
     *
     * @param frameLogger handler used to log all frames
     * @return {@link Http2CodecBuilder} the builder for the {@link Http2Codec}
     */
    public Http2CodecBuilder frameLogger(Http2FrameLogger frameLogger) {
        this.frameLogger = frameLogger;
        return this;
    }

    /**
     * Returns the frame logger to log messages with.
     */
    public Http2FrameLogger frameLogger() {
        return frameLogger;
    }

    /**
     * Specifies the headers sensitivity detector.
     *
     * @param headersSensitivityDetector decides whether headers should be considered sensitive or not
     * @return {@link Http2CodecBuilder} the builder for the {@link Http2Codec}
     */
    public Http2CodecBuilder headersSensitivityDetector(SensitivityDetector headersSensitivityDetector) {
        this.headersSensitivityDetector = headersSensitivityDetector;
        return this;
    }

    /**
     * Returns the headers sensitivity detector.
     */
    public SensitivityDetector headersSensitivityDetector() {
        return headersSensitivityDetector;
    }

    private Http2FrameWriter frameWriter() {
        return headersSensitivityDetector() == null ?
            new DefaultHttp2FrameWriter() :
            new DefaultHttp2FrameWriter(headersSensitivityDetector());
    }

    /**
     * Builds/creates a new {@link Http2Codec} instance using this builder's current settings.
     */
    public Http2Codec build() {
        return new Http2Codec(server, bootstrap,
            frameWriter(), frameLogger(), initialSettings());
    }
}

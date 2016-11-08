/*
 * Copyright 2016 The Netty Project
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

import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2ChannelClosedException;
import io.netty.handler.codec.http2.StreamBufferingEncoder.Http2GoAwayException;
import io.netty.util.internal.UnstableApi;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http2.DefaultHttp2LocalFlowController.DEFAULT_WINDOW_UPDATE_RATIO;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Builder for the {@link Http2FrameCodec}.
 */
@UnstableApi
public final class Http2FrameCodecBuilder {

    private final boolean server;
    private Http2FrameWriter frameWriter;
    private Http2FrameReader frameReader;
    private Http2Settings initialSettings;
    private long gracefulShutdownTimeoutMillis;
    private float windowUpdateRatio;
    private Http2FrameLogger frameLogger;
    private boolean bufferOutboundStreams;

    private Http2FrameCodecBuilder(boolean server) {
        this.server = server;
        frameWriter = new DefaultHttp2FrameWriter();
        frameReader = new DefaultHttp2FrameReader();
        initialSettings = new Http2Settings();
        gracefulShutdownTimeoutMillis = Http2CodecUtil.DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
        windowUpdateRatio = DEFAULT_WINDOW_UPDATE_RATIO;
    }

    /**
     * Creates a builder for a HTTP/2 client.
     */
    public static Http2FrameCodecBuilder forClient() {
        return new Http2FrameCodecBuilder(false);
    }

    /**
     * Creates a builder for a HTTP/2 server.
     */
    public static Http2FrameCodecBuilder forServer() {
        return new Http2FrameCodecBuilder(true);
    }

    /**
     * Specify the {@link Http2FrameWriter} to use.
     *
     * <p>If not set, the {@link DefaultHttp2FrameWriter} is used.
     */
    public Http2FrameCodecBuilder frameWriter(Http2FrameWriter frameWriter) {
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        return this;
    }

    /**
     * Specify the {@link Http2FrameWriter} to use.
     *
     * <p>If not set, the {@link DefaultHttp2FrameReader} is used.
     */
    public Http2FrameCodecBuilder frameReader(Http2FrameReader frameReader) {
        this.frameReader = checkNotNull(frameReader, "frameReader");
        return this;
    }

    /**
     * Specify the initial {@link Http2Settings} to send to the remote endpoint.
     *
     * <p>If not set, the default values of {@link Http2Settings} are used.
     */
    public Http2FrameCodecBuilder initialSettings(Http2Settings initialSettings) {
        this.initialSettings = checkNotNull(initialSettings, "initialSettings");
        return this;
    }

    /**
     * The amount of time to wait for all active streams to be closed, before the connection is closed.
     *
     * <p>The default value is {@link Http2CodecUtil#DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS}.
     */
    public Http2FrameCodecBuilder gracefulShutdownTimeout(long timeout, TimeUnit unit) {
        gracefulShutdownTimeoutMillis =
                checkNotNull(unit, "unit").toMillis(checkPositiveOrZero(timeout, "timeout"));
        return this;
    }

    /**
     * Specify the HTTP/2 flow control window update ratio for both the connection and stream window.
     */
    public Http2FrameCodecBuilder windowUpdateRatio(float windowUpdateRatio) {
        if (Float.compare(windowUpdateRatio, 0) < 1 || Float.compare(windowUpdateRatio, 1) > -1) {
            throw new IllegalArgumentException("windowUpdateRatio must be (0,1). Was: " + windowUpdateRatio);
        }
        this.windowUpdateRatio = windowUpdateRatio;
        return this;
    }

    /**
     * Specify the {@link Http2FrameLogger} to use.
     *
     * <p>By default no frame logger is used.
     */
    public Http2FrameCodecBuilder frameLogger(Http2FrameLogger frameLogger) {
        this.frameLogger = checkNotNull(frameLogger, "frameLogger");
        return this;
    }

    /**
     * Whether to buffer new outbound HTTP/2 streams when the {@code MAX_CONCURRENT_STREAMS} limit is reached.
     *
     * <p>When this limit is hit, instead of rejecting any new streams, newly created streams and their corresponding
     * frames are buffered. Once an active stream gets closed or the maximum number of concurrent streams is increased,
     * the codec will automatically try to empty its buffer and create as many new streams as possible.
     *
     * <p>If a {@code GOAWAY} frame is received from the remote endpoint, all buffered writes for streams with an ID
     * less than the specified {@code lastStreamId} will immediately fail with a {@link Http2GoAwayException}.
     *
     * <p>If the channel gets closed, all new and buffered writes will immediately fail with a
     * {@link Http2ChannelClosedException}.
     *
     * <p>This implementation makes the buffering mostly transparent and does not enforce an upper bound as to how many
     * streams/frames can be buffered.
     */
    public Http2FrameCodecBuilder bufferOutboundStreams(boolean bufferOutboundStreams) {
        this.bufferOutboundStreams = bufferOutboundStreams;
        return this;
    }

    /**
     * Build a {@link Http2FrameCodec} object.
     */
    public Http2FrameCodec build() {
        Http2Connection connection = new DefaultHttp2Connection(server);

        if (frameLogger != null) {
            frameWriter = new Http2OutboundFrameLogger(frameWriter, frameLogger);
            frameReader = new Http2InboundFrameLogger(frameReader, frameLogger);
        }

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);

        if (bufferOutboundStreams) {
            encoder = new StreamBufferingEncoder(encoder);
        }

        connection.local().flowController(new DefaultHttp2LocalFlowController(connection, windowUpdateRatio,
                                                                              true /* auto refill conn window */));

        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader);

        return new Http2FrameCodec(encoder, decoder, initialSettings, gracefulShutdownTimeoutMillis);
    }
}

/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A builder for {@link Http2MultiplexCodec}.
 *
 * @deprecated use {@link Http2FrameCodecBuilder} together with {@link Http2MultiplexHandler}.
 */
@Deprecated
public class Http2MultiplexCodecBuilder
        extends AbstractHttp2ConnectionHandlerBuilder<Http2MultiplexCodec, Http2MultiplexCodecBuilder> {
    private Http2FrameWriter frameWriter;

    final ChannelHandler childHandler;
    private ChannelHandler upgradeStreamHandler;

    Http2MultiplexCodecBuilder(boolean server, ChannelHandler childHandler) {
        server(server);
        this.childHandler = checkSharable(checkNotNull(childHandler, "childHandler"));
        // For backwards compatibility we should disable to timeout by default at this layer.
        gracefulShutdownTimeoutMillis(0);
    }

    private static ChannelHandler checkSharable(ChannelHandler handler) {
        if (handler instanceof ChannelHandlerAdapter && !((ChannelHandlerAdapter) handler).isSharable() &&
                !handler.getClass().isAnnotationPresent(ChannelHandler.Sharable.class)) {
            throw new IllegalArgumentException("The handler must be Sharable");
        }
        return handler;
    }

    // For testing only.
    Http2MultiplexCodecBuilder frameWriter(Http2FrameWriter frameWriter) {
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        return this;
    }

    /**
     * Creates a builder for an HTTP/2 client.
     *
     * @param childHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}.
     */
    public static Http2MultiplexCodecBuilder forClient(ChannelHandler childHandler) {
        return new Http2MultiplexCodecBuilder(false, childHandler);
    }

    /**
     * Creates a builder for an HTTP/2 server.
     *
     * @param childHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}.
     */
    public static Http2MultiplexCodecBuilder forServer(ChannelHandler childHandler) {
        return new Http2MultiplexCodecBuilder(true, childHandler);
    }

    public Http2MultiplexCodecBuilder withUpgradeStreamHandler(ChannelHandler upgradeStreamHandler) {
        if (isServer()) {
            throw new IllegalArgumentException("Server codecs don't use an extra handler for the upgrade stream");
        }
        this.upgradeStreamHandler = upgradeStreamHandler;
        return this;
    }

    @Override
    public Http2Settings initialSettings() {
        return super.initialSettings();
    }

    @Override
    public Http2MultiplexCodecBuilder initialSettings(Http2Settings settings) {
        return super.initialSettings(settings);
    }

    @Override
    public long gracefulShutdownTimeoutMillis() {
        return super.gracefulShutdownTimeoutMillis();
    }

    @Override
    public Http2MultiplexCodecBuilder gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
    }

    @Override
    public boolean isServer() {
        return super.isServer();
    }

    @Override
    public int maxReservedStreams() {
        return super.maxReservedStreams();
    }

    @Override
    public Http2MultiplexCodecBuilder maxReservedStreams(int maxReservedStreams) {
        return super.maxReservedStreams(maxReservedStreams);
    }

    @Override
    public boolean isValidateHeaders() {
        return super.isValidateHeaders();
    }

    @Override
    public Http2MultiplexCodecBuilder validateHeaders(boolean validateHeaders) {
        return super.validateHeaders(validateHeaders);
    }

    @Override
    public Http2FrameLogger frameLogger() {
        return super.frameLogger();
    }

    @Override
    public Http2MultiplexCodecBuilder frameLogger(Http2FrameLogger frameLogger) {
        return super.frameLogger(frameLogger);
    }

    @Override
    public boolean encoderEnforceMaxConcurrentStreams() {
        return super.encoderEnforceMaxConcurrentStreams();
    }

    @Override
    public Http2MultiplexCodecBuilder encoderEnforceMaxConcurrentStreams(boolean encoderEnforceMaxConcurrentStreams) {
        return super.encoderEnforceMaxConcurrentStreams(encoderEnforceMaxConcurrentStreams);
    }

    @Override
    public int encoderEnforceMaxQueuedControlFrames() {
        return super.encoderEnforceMaxQueuedControlFrames();
    }

    @Override
    public Http2MultiplexCodecBuilder encoderEnforceMaxQueuedControlFrames(int maxQueuedControlFrames) {
        return super.encoderEnforceMaxQueuedControlFrames(maxQueuedControlFrames);
    }

    @Override
    public Http2HeadersEncoder.SensitivityDetector headerSensitivityDetector() {
        return super.headerSensitivityDetector();
    }

    @Override
    public Http2MultiplexCodecBuilder headerSensitivityDetector(
            Http2HeadersEncoder.SensitivityDetector headerSensitivityDetector) {
        return super.headerSensitivityDetector(headerSensitivityDetector);
    }

    @Override
    public Http2MultiplexCodecBuilder encoderIgnoreMaxHeaderListSize(boolean ignoreMaxHeaderListSize) {
        return super.encoderIgnoreMaxHeaderListSize(ignoreMaxHeaderListSize);
    }

    @Override
    @Deprecated
    public Http2MultiplexCodecBuilder initialHuffmanDecodeCapacity(int initialHuffmanDecodeCapacity) {
        return super.initialHuffmanDecodeCapacity(initialHuffmanDecodeCapacity);
    }

    @Override
    public Http2MultiplexCodecBuilder autoAckSettingsFrame(boolean autoAckSettings) {
        return super.autoAckSettingsFrame(autoAckSettings);
    }

    @Override
    public Http2MultiplexCodecBuilder autoAckPingFrame(boolean autoAckPingFrame) {
        return super.autoAckPingFrame(autoAckPingFrame);
    }

    @Override
    public Http2MultiplexCodecBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return super.decoupleCloseAndGoAway(decoupleCloseAndGoAway);
    }

    @Override
    public Http2MultiplexCodecBuilder flushPreface(boolean flushPreface) {
        return super.flushPreface(flushPreface);
    }

    @Override
    public int decoderEnforceMaxConsecutiveEmptyDataFrames() {
        return super.decoderEnforceMaxConsecutiveEmptyDataFrames();
    }

    @Override
    public Http2MultiplexCodecBuilder decoderEnforceMaxConsecutiveEmptyDataFrames(int maxConsecutiveEmptyFrames) {
        return super.decoderEnforceMaxConsecutiveEmptyDataFrames(maxConsecutiveEmptyFrames);
    }

    @Override
    public Http2MultiplexCodecBuilder decoderEnforceMaxRstFramesPerWindow(
            int maxRstFramesPerWindow, int secondsPerWindow) {
        return super.decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow);
    }

    @Override
    public Http2MultiplexCodec build() {
        Http2FrameWriter frameWriter = this.frameWriter;
        if (frameWriter != null) {
            // This is to support our tests and will never be executed by the user as frameWriter(...)
            // is package-private.
            DefaultHttp2Connection connection = new DefaultHttp2Connection(isServer(), maxReservedStreams());
            Long maxHeaderListSize = initialSettings().maxHeaderListSize();
            Http2FrameReader frameReader = new DefaultHttp2FrameReader(maxHeaderListSize == null ?
                    new DefaultHttp2HeadersDecoder(isValidateHeaders()) :
                    new DefaultHttp2HeadersDecoder(isValidateHeaders(), maxHeaderListSize));

            if (frameLogger() != null) {
                frameWriter = new Http2OutboundFrameLogger(frameWriter, frameLogger());
                frameReader = new Http2InboundFrameLogger(frameReader, frameLogger());
            }
            Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
            if (encoderEnforceMaxConcurrentStreams()) {
                encoder = new StreamBufferingEncoder(encoder);
            }
            Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader,
                    promisedRequestVerifier(), isAutoAckSettingsFrame(), isAutoAckPingFrame(), isValidateHeaders());
            int maxConsecutiveEmptyDataFrames = decoderEnforceMaxConsecutiveEmptyDataFrames();
            if (maxConsecutiveEmptyDataFrames > 0) {
                decoder = new Http2EmptyDataFrameConnectionDecoder(decoder, maxConsecutiveEmptyDataFrames);
            }

            return build(decoder, encoder, initialSettings());
        }
        return super.build();
    }

    @Override
    protected Http2MultiplexCodec build(
            Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        Http2MultiplexCodec codec = new Http2MultiplexCodec(encoder, decoder, initialSettings, childHandler,
                upgradeStreamHandler, decoupleCloseAndGoAway(), flushPreface());
        codec.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis());
        return codec;
    }
}

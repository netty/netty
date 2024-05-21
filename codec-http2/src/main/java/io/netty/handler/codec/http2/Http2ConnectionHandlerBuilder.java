/*
 * Copyright 2015 The Netty Project
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

import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;

/**
 * Builder which builds {@link Http2ConnectionHandler} objects.
 */
public final class Http2ConnectionHandlerBuilder
        extends AbstractHttp2ConnectionHandlerBuilder<Http2ConnectionHandler, Http2ConnectionHandlerBuilder> {

    @Override
    public Http2ConnectionHandlerBuilder validateHeaders(boolean validateHeaders) {
        return super.validateHeaders(validateHeaders);
    }

    @Override
    public Http2ConnectionHandlerBuilder initialSettings(Http2Settings settings) {
        return super.initialSettings(settings);
    }

    @Override
    public Http2Settings initialSettings() {
        return super.initialSettings();
    }

    @Override
    public Http2ConnectionHandlerBuilder frameListener(Http2FrameListener frameListener) {
        return super.frameListener(frameListener);
    }

    @Override
    public Http2ConnectionHandlerBuilder gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
    }

    @Override
    public Http2ConnectionHandlerBuilder server(boolean isServer) {
        return super.server(isServer);
    }

    @Override
    public Http2ConnectionHandlerBuilder connection(Http2Connection connection) {
        return super.connection(connection);
    }

    @Override
    public Http2ConnectionHandlerBuilder maxReservedStreams(int maxReservedStreams) {
        return super.maxReservedStreams(maxReservedStreams);
    }

    @Override
    public Http2ConnectionHandlerBuilder codec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
        return super.codec(decoder, encoder);
    }

    @Override
    public Http2ConnectionHandlerBuilder frameLogger(Http2FrameLogger frameLogger) {
        return super.frameLogger(frameLogger);
    }

    @Override
    public Http2ConnectionHandlerBuilder encoderEnforceMaxConcurrentStreams(
            boolean encoderEnforceMaxConcurrentStreams) {
        return super.encoderEnforceMaxConcurrentStreams(encoderEnforceMaxConcurrentStreams);
    }

    @Override
    public Http2ConnectionHandlerBuilder encoderIgnoreMaxHeaderListSize(boolean encoderIgnoreMaxHeaderListSize) {
        return super.encoderIgnoreMaxHeaderListSize(encoderIgnoreMaxHeaderListSize);
    }

    @Override
    public Http2ConnectionHandlerBuilder headerSensitivityDetector(SensitivityDetector headerSensitivityDetector) {
        return super.headerSensitivityDetector(headerSensitivityDetector);
    }

    @Override
    @Deprecated
    public Http2ConnectionHandlerBuilder initialHuffmanDecodeCapacity(int initialHuffmanDecodeCapacity) {
        return super.initialHuffmanDecodeCapacity(initialHuffmanDecodeCapacity);
    }

    @Override
    public Http2ConnectionHandlerBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return super.decoupleCloseAndGoAway(decoupleCloseAndGoAway);
    }

    @Override
    public Http2ConnectionHandlerBuilder flushPreface(boolean flushPreface) {
        return super.flushPreface(flushPreface);
    }

    @Override
    public int decoderEnforceMaxConsecutiveEmptyDataFrames() {
        return super.decoderEnforceMaxConsecutiveEmptyDataFrames();
    }

    @Override
    public Http2ConnectionHandlerBuilder decoderEnforceMaxConsecutiveEmptyDataFrames(int maxConsecutiveEmptyFrames) {
        return super.decoderEnforceMaxConsecutiveEmptyDataFrames(maxConsecutiveEmptyFrames);
    }

    @Override
    public Http2ConnectionHandlerBuilder decoderEnforceMaxRstFramesPerWindow(
            int maxRstFramesPerWindow, int secondsPerWindow) {
        return super.decoderEnforceMaxRstFramesPerWindow(maxRstFramesPerWindow, secondsPerWindow);
    }

    @Override
    public Http2ConnectionHandler build() {
        return super.build();
    }

    @Override
    protected Http2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                           Http2Settings initialSettings) {
        return new Http2ConnectionHandler(decoder, encoder, initialSettings, decoupleCloseAndGoAway(), flushPreface());
    }
}

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

import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_RESERVED_STREAMS;
import static io.netty.handler.codec.http2.Http2PromisedRequestVerifier.ALWAYS_VERIFY;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Abstract base class which defines commonly used features required to build {@link Http2ConnectionHandler} instances.
 *
 * <h3>Three ways to build a {@link Http2ConnectionHandler}</h3>
 * <h4>Let the builder create a {@link Http2ConnectionHandler}</h4>
 * Simply call all the necessary setter methods, and then use {@link #build()} to build a new
 * {@link Http2ConnectionHandler}. Setting the following properties are prohibited because they are used for
 * other ways of building a {@link Http2ConnectionHandler}.
 * conflicts with this option:
 * <ul>
 *   <li>{@link #connection(Http2Connection)}</li>
 *   <li>{@link #codec(Http2ConnectionDecoder, Http2ConnectionEncoder)}</li>
 * </ul>
 *
 *
 * <h4>Let the builder use the {@link Http2ConnectionHandler} you specified</h4>
 * Call {@link #connection(Http2Connection)} to tell the builder that you want to build the handler from the
 * {@link Http2Connection} you specified. Setting the following properties are prohibited and thus will trigger
 * an {@link IllegalStateException} because they conflict with this option.
 * <ul>
 *   <li>{@link #server(boolean)}</li>
 *   <li>{@link #codec(Http2ConnectionDecoder, Http2ConnectionEncoder)}</li>
 * </ul>
 *
 * <h4>Let the builder use the {@link Http2ConnectionDecoder} and {@link Http2ConnectionEncoder} you specified</h4>
 * Call {@link #codec(Http2ConnectionDecoder, Http2ConnectionEncoder)} to tell the builder that you want to built the
 * handler from the {@link Http2ConnectionDecoder} and {@link Http2ConnectionEncoder} you specified. Setting the
 * following properties are prohibited and thus will trigger an {@link IllegalStateException} because they conflict
 * with this option:
 * <ul>
 *   <li>{@link #server(boolean)}</li>
 *   <li>{@link #connection(Http2Connection)}</li>
 *   <li>{@link #frameLogger(Http2FrameLogger)}</li>
 *   <li>{@link #headerSensitivityDetector(SensitivityDetector)}</li>
 *   <li>{@link #encoderEnforceMaxConcurrentStreams(boolean)}</li>
 *   <li>{@link #encoderIgnoreMaxHeaderListSize(boolean)}</li>
 * </ul>
 *
 * <h3>Exposing necessary methods in a subclass</h3>
 * {@link #build()} method and all property access methods are {@code protected}. Choose the methods to expose to the
 * users of your builder implementation and make them {@code public}.
 *
 * @param <T> The type of handler created by this builder.
 * @param <B> The concrete type of this builder.
 */
public abstract class AbstractHttp2ConnectionHandlerBuilder<T extends Http2ConnectionHandler,
                                                            B extends AbstractHttp2ConnectionHandlerBuilder<T, B>> {

    private static final SensitivityDetector DEFAULT_HEADER_SENSITIVITY_DETECTOR = Http2HeadersEncoder.NEVER_SENSITIVE;

    private static final int DEFAULT_MAX_RST_FRAMES_PER_CONNECTION_FOR_SERVER = 200;

    // The properties that can always be set.
    private Http2Settings initialSettings = Http2Settings.defaultSettings();
    private Http2FrameListener frameListener;
    private long gracefulShutdownTimeoutMillis = Http2CodecUtil.DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
    private boolean decoupleCloseAndGoAway;
    private boolean flushPreface = true;

    // The property that will prohibit connection() and codec() if set by server(),
    // because this property is used only when this builder creates an Http2Connection.
    private Boolean isServer;
    private Integer maxReservedStreams;

    // The property that will prohibit server() and codec() if set by connection().
    private Http2Connection connection;

    // The properties that will prohibit server() and connection() if set by codec().
    private Http2ConnectionDecoder decoder;
    private Http2ConnectionEncoder encoder;

    // The properties that are:
    // * mutually exclusive against codec() and
    // * OK to use with server() and connection()
    private Boolean validateHeaders;
    private Http2FrameLogger frameLogger;
    private SensitivityDetector headerSensitivityDetector;
    private Boolean encoderEnforceMaxConcurrentStreams;
    private Boolean encoderIgnoreMaxHeaderListSize;
    private Http2PromisedRequestVerifier promisedRequestVerifier = ALWAYS_VERIFY;
    private boolean autoAckSettingsFrame = true;
    private boolean autoAckPingFrame = true;
    private int maxQueuedControlFrames = Http2CodecUtil.DEFAULT_MAX_QUEUED_CONTROL_FRAMES;
    private int maxConsecutiveEmptyFrames = 2;
    private Integer maxRstFramesPerWindow;
    private int secondsPerWindow = 30;

    /**
     * Sets the {@link Http2Settings} to use for the initial connection settings exchange.
     */
    protected Http2Settings initialSettings() {
        return initialSettings;
    }

    /**
     * Sets the {@link Http2Settings} to use for the initial connection settings exchange.
     */
    protected B initialSettings(Http2Settings settings) {
        initialSettings = checkNotNull(settings, "settings");
        return self();
    }

    /**
     * Returns the listener of inbound frames.
     *
     * @return {@link Http2FrameListener} if set, or {@code null} if not set.
     */
    protected Http2FrameListener frameListener() {
        return frameListener;
    }

    /**
     * Sets the listener of inbound frames.
     * This listener will only be set if the decoder's listener is {@code null}.
     */
    protected B frameListener(Http2FrameListener frameListener) {
        this.frameListener = checkNotNull(frameListener, "frameListener");
        return self();
    }

    /**
     * Returns the graceful shutdown timeout of the {@link Http2Connection} in milliseconds. Returns -1 if the
     * timeout is indefinite.
     */
    protected long gracefulShutdownTimeoutMillis() {
        return gracefulShutdownTimeoutMillis;
    }

    /**
     * Sets the graceful shutdown timeout of the {@link Http2Connection} in milliseconds.
     */
    protected B gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        if (gracefulShutdownTimeoutMillis < -1) {
            throw new IllegalArgumentException("gracefulShutdownTimeoutMillis: " + gracefulShutdownTimeoutMillis +
                                               " (expected: -1 for indefinite or >= 0)");
        }
        this.gracefulShutdownTimeoutMillis = gracefulShutdownTimeoutMillis;
        return self();
    }

    /**
     * Returns if {@link #build()} will to create a {@link Http2Connection} in server mode ({@code true})
     * or client mode ({@code false}).
     */
    protected boolean isServer() {
        return isServer != null ? isServer : true;
    }

    /**
     * Sets if {@link #build()} will to create a {@link Http2Connection} in server mode ({@code true})
     * or client mode ({@code false}).
     */
    protected B server(boolean isServer) {
        enforceConstraint("server", "connection", connection);
        enforceConstraint("server", "codec", decoder);
        enforceConstraint("server", "codec", encoder);

        this.isServer = isServer;
        return self();
    }

    /**
     * Get the maximum number of streams which can be in the reserved state at any given time.
     * <p>
     * By default this value will be ignored on the server for local endpoint. This is because the RFC provides
     * no way to explicitly communicate a limit to how many states can be in the reserved state, and instead relies
     * on the peer to send RST_STREAM frames when they will be rejected.
     */
    protected int maxReservedStreams() {
        return maxReservedStreams != null ? maxReservedStreams : DEFAULT_MAX_RESERVED_STREAMS;
    }

    /**
     * Set the maximum number of streams which can be in the reserved state at any given time.
     */
    protected B maxReservedStreams(int maxReservedStreams) {
        enforceConstraint("server", "connection", connection);
        enforceConstraint("server", "codec", decoder);
        enforceConstraint("server", "codec", encoder);

        this.maxReservedStreams = checkPositiveOrZero(maxReservedStreams, "maxReservedStreams");
        return self();
    }

    /**
     * Returns the {@link Http2Connection} to use.
     *
     * @return {@link Http2Connection} if set, or {@code null} if not set.
     */
    protected Http2Connection connection() {
        return connection;
    }

    /**
     * Sets the {@link Http2Connection} to use.
     */
    protected B connection(Http2Connection connection) {
        enforceConstraint("connection", "maxReservedStreams", maxReservedStreams);
        enforceConstraint("connection", "server", isServer);
        enforceConstraint("connection", "codec", decoder);
        enforceConstraint("connection", "codec", encoder);

        this.connection = checkNotNull(connection, "connection");

        return self();
    }

    /**
     * Returns the {@link Http2ConnectionDecoder} to use.
     *
     * @return {@link Http2ConnectionDecoder} if set, or {@code null} if not set.
     */
    protected Http2ConnectionDecoder decoder() {
        return decoder;
    }

    /**
     * Returns the {@link Http2ConnectionEncoder} to use.
     *
     * @return {@link Http2ConnectionEncoder} if set, or {@code null} if not set.
     */
    protected Http2ConnectionEncoder encoder() {
        return encoder;
    }

    /**
     * Sets the {@link Http2ConnectionDecoder} and {@link Http2ConnectionEncoder} to use.
     */
    protected B codec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
        enforceConstraint("codec", "server", isServer);
        enforceConstraint("codec", "maxReservedStreams", maxReservedStreams);
        enforceConstraint("codec", "connection", connection);
        enforceConstraint("codec", "frameLogger", frameLogger);
        enforceConstraint("codec", "validateHeaders", validateHeaders);
        enforceConstraint("codec", "headerSensitivityDetector", headerSensitivityDetector);
        enforceConstraint("codec", "encoderEnforceMaxConcurrentStreams", encoderEnforceMaxConcurrentStreams);

        checkNotNull(decoder, "decoder");
        checkNotNull(encoder, "encoder");

        if (decoder.connection() != encoder.connection()) {
            throw new IllegalArgumentException("The specified encoder and decoder have different connections.");
        }

        this.decoder = decoder;
        this.encoder = encoder;

        return self();
    }

    /**
     * Returns if HTTP headers should be validated according to
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.6">RFC 7540, 8.1.2.6</a>.
     */
    protected boolean isValidateHeaders() {
        return validateHeaders != null ? validateHeaders : true;
    }

    /**
     * Sets if HTTP headers should be validated according to
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.6">RFC 7540, 8.1.2.6</a>.
     */
    protected B validateHeaders(boolean validateHeaders) {
        enforceNonCodecConstraints("validateHeaders");
        this.validateHeaders = validateHeaders;
        return self();
    }

    /**
     * Returns the logger that is used for the encoder and decoder.
     *
     * @return {@link Http2FrameLogger} if set, or {@code null} if not set.
     */
    protected Http2FrameLogger frameLogger() {
        return frameLogger;
    }

    /**
     * Sets the logger that is used for the encoder and decoder.
     */
    protected B frameLogger(Http2FrameLogger frameLogger) {
        enforceNonCodecConstraints("frameLogger");
        this.frameLogger = checkNotNull(frameLogger, "frameLogger");
        return self();
    }

    /**
     * Returns if the encoder should queue frames if the maximum number of concurrent streams
     * would otherwise be exceeded.
     */
    protected boolean encoderEnforceMaxConcurrentStreams() {
        return encoderEnforceMaxConcurrentStreams != null ? encoderEnforceMaxConcurrentStreams : false;
    }

    /**
     * Sets if the encoder should queue frames if the maximum number of concurrent streams
     * would otherwise be exceeded.
     */
    protected B encoderEnforceMaxConcurrentStreams(boolean encoderEnforceMaxConcurrentStreams) {
        enforceNonCodecConstraints("encoderEnforceMaxConcurrentStreams");
        this.encoderEnforceMaxConcurrentStreams = encoderEnforceMaxConcurrentStreams;
        return self();
    }

    /**
     * Returns the maximum number of queued control frames that are allowed before the connection is closed.
     * This allows to protected against various attacks that can lead to high CPU / memory usage if the remote-peer
     * floods us with frames that would have us produce control frames, but stops to read from the underlying socket.
     *
     * {@code 0} means no protection is in place.
     */
    protected int encoderEnforceMaxQueuedControlFrames() {
        return maxQueuedControlFrames;
    }

    /**
     * Sets the maximum number of queued control frames that are allowed before the connection is closed.
     * This allows to protected against various attacks that can lead to high CPU / memory usage if the remote-peer
     * floods us with frames that would have us produce control frames, but stops to read from the underlying socket.
     *
     * {@code 0} means no protection should be applied.
     */
    protected B encoderEnforceMaxQueuedControlFrames(int maxQueuedControlFrames) {
        enforceNonCodecConstraints("encoderEnforceMaxQueuedControlFrames");
        this.maxQueuedControlFrames = checkPositiveOrZero(maxQueuedControlFrames, "maxQueuedControlFrames");
        return self();
    }

    /**
     * Returns the {@link SensitivityDetector} to use.
     */
    protected SensitivityDetector headerSensitivityDetector() {
        return headerSensitivityDetector != null ? headerSensitivityDetector : DEFAULT_HEADER_SENSITIVITY_DETECTOR;
    }

    /**
     * Sets the {@link SensitivityDetector} to use.
     */
    protected B headerSensitivityDetector(SensitivityDetector headerSensitivityDetector) {
        enforceNonCodecConstraints("headerSensitivityDetector");
        this.headerSensitivityDetector = checkNotNull(headerSensitivityDetector, "headerSensitivityDetector");
        return self();
    }

    /**
     * Sets if the <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>
     * should be ignored when encoding headers.
     * @param ignoreMaxHeaderListSize {@code true} to ignore
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     * @return this.
     */
    protected B encoderIgnoreMaxHeaderListSize(boolean ignoreMaxHeaderListSize) {
        enforceNonCodecConstraints("encoderIgnoreMaxHeaderListSize");
        encoderIgnoreMaxHeaderListSize = ignoreMaxHeaderListSize;
        return self();
    }

    /**
     * Does nothing, do not call.
     *
     * @deprecated Huffman decoding no longer depends on having a decode capacity.
     */
    @Deprecated
    protected B initialHuffmanDecodeCapacity(int initialHuffmanDecodeCapacity) {
        return self();
    }

    /**
     * Set the {@link Http2PromisedRequestVerifier} to use.
     * @return this.
     */
    protected B promisedRequestVerifier(Http2PromisedRequestVerifier promisedRequestVerifier) {
        enforceNonCodecConstraints("promisedRequestVerifier");
        this.promisedRequestVerifier = checkNotNull(promisedRequestVerifier, "promisedRequestVerifier");
        return self();
    }

    /**
     * Get the {@link Http2PromisedRequestVerifier} to use.
     * @return the {@link Http2PromisedRequestVerifier} to use.
     */
    protected Http2PromisedRequestVerifier promisedRequestVerifier() {
        return promisedRequestVerifier;
    }

    /**
     * Returns the maximum number of consecutive empty DATA frames (without end_of_stream flag) that are allowed before
     * the connection is closed. This allows to protect against the remote peer flooding us with such frames and
     * so use up a lot of CPU. There is no valid use-case for empty DATA frames without end_of_stream flag.
     *
     * {@code 0} means no protection is in place.
     */
    protected int decoderEnforceMaxConsecutiveEmptyDataFrames() {
        return maxConsecutiveEmptyFrames;
    }

    /**
     * Sets the maximum number of consecutive empty DATA frames (without end_of_stream flag) that are allowed before
     * the connection is closed. This allows to protect against the remote peer flooding us with such frames and
     * so use up a lot of CPU. There is no valid use-case for empty DATA frames without end_of_stream flag.
     *
     * {@code 0} means no protection should be applied.
     */
    protected B decoderEnforceMaxConsecutiveEmptyDataFrames(int maxConsecutiveEmptyFrames) {
        enforceNonCodecConstraints("maxConsecutiveEmptyFrames");
        this.maxConsecutiveEmptyFrames = checkPositiveOrZero(
                maxConsecutiveEmptyFrames, "maxConsecutiveEmptyFrames");
        return self();
    }

    /**
     * Sets the maximum number RST frames that are allowed per window before
     * the connection is closed. This allows to protect against the remote peer flooding us with such frames and
     * so use up a lot of CPU.
     *
     * {@code 0} for any of the parameters means no protection should be applied.
     */
    protected B decoderEnforceMaxRstFramesPerWindow(int maxRstFramesPerWindow, int secondsPerWindow) {
        enforceNonCodecConstraints("decoderEnforceMaxRstFramesPerWindow");
        this.maxRstFramesPerWindow = checkPositiveOrZero(
                maxRstFramesPerWindow, "maxRstFramesPerWindow");
        this.secondsPerWindow = checkPositiveOrZero(secondsPerWindow, "secondsPerWindow");
        return self();
    }

    /**
     * Determine if settings frame should automatically be acknowledged and applied.
     * @return this.
     */
    protected B autoAckSettingsFrame(boolean autoAckSettings) {
        enforceNonCodecConstraints("autoAckSettingsFrame");
        autoAckSettingsFrame = autoAckSettings;
        return self();
    }

    /**
     * Determine if the SETTINGS frames should be automatically acknowledged and applied.
     * @return {@code true} if the SETTINGS frames should be automatically acknowledged and applied.
     */
    protected boolean isAutoAckSettingsFrame() {
        return autoAckSettingsFrame;
    }

    /**
     * Determine if PING frame should automatically be acknowledged or not.
     * @return this.
     */
    protected B autoAckPingFrame(boolean autoAckPingFrame) {
        enforceNonCodecConstraints("autoAckPingFrame");
        this.autoAckPingFrame = autoAckPingFrame;
        return self();
    }

    /**
     * Determine if the PING frames should be automatically acknowledged or not.
     * @return {@code true} if the PING frames should be automatically acknowledged.
     */
    protected boolean isAutoAckPingFrame() {
        return autoAckPingFrame;
    }

    /**
     * Determine if the {@link Channel#close()} should be coupled with goaway and graceful close.
     * @param decoupleCloseAndGoAway {@code true} to make {@link Channel#close()} directly close the underlying
     *   transport, and not attempt graceful closure via GOAWAY.
     * @return {@code this}.
     */
    protected B decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        this.decoupleCloseAndGoAway = decoupleCloseAndGoAway;
        return self();
    }

    /**
     * Determine if the {@link Channel#close()} should be coupled with goaway and graceful close.
     */
    protected boolean decoupleCloseAndGoAway() {
        return decoupleCloseAndGoAway;
    }

    /**
     * Determine if the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-3.5">Preface</a>
     * should be automatically flushed when the {@link Channel} becomes active or not.
     * <p>
     * Client may choose to opt-out from this automatic behavior and manage flush manually if it's ready to send
     * request frames immediately after the preface. It may help to avoid unnecessary latency.
     *
     * @param flushPreface {@code true} to automatically flush, {@code false otherwise}.
     * @return {@code this}.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-3.5">HTTP/2 Connection Preface</a>
     */
    protected B flushPreface(boolean flushPreface) {
        this.flushPreface = flushPreface;
        return self();
    }

    /**
     * Determine if the <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-3.5">Preface</a>
     * should be automatically flushed when the {@link Channel} becomes active or not.
     * <p>
     * Client may choose to opt-out from this automatic behavior and manage flush manually if it's ready to send
     * request frames immediately after the preface. It may help to avoid unnecessary latency.
     *
     * @return {@code true} if automatically flushed.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-3.5">HTTP/2 Connection Preface</a>
     */
    protected boolean flushPreface() {
        return flushPreface;
    }

    /**
     * Create a new {@link Http2ConnectionHandler}.
     */
    protected T build() {
        if (encoder != null) {
            assert decoder != null;
            return buildFromCodec(decoder, encoder);
        }

        Http2Connection connection = this.connection;
        if (connection == null) {
            connection = new DefaultHttp2Connection(isServer(), maxReservedStreams());
        }

        return buildFromConnection(connection);
    }

    private T buildFromConnection(Http2Connection connection) {
        Long maxHeaderListSize = initialSettings.maxHeaderListSize();
        Http2FrameReader reader = new DefaultHttp2FrameReader(new DefaultHttp2HeadersDecoder(isValidateHeaders(),
                maxHeaderListSize == null ? DEFAULT_HEADER_LIST_SIZE : maxHeaderListSize,
                /* initialHuffmanDecodeCapacity= */ -1));
        Http2FrameWriter writer = encoderIgnoreMaxHeaderListSize == null ?
                new DefaultHttp2FrameWriter(headerSensitivityDetector()) :
                new DefaultHttp2FrameWriter(headerSensitivityDetector(), encoderIgnoreMaxHeaderListSize);

        if (frameLogger != null) {
            reader = new Http2InboundFrameLogger(reader, frameLogger);
            writer = new Http2OutboundFrameLogger(writer, frameLogger);
        }

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        boolean encoderEnforceMaxConcurrentStreams = encoderEnforceMaxConcurrentStreams();

        if (maxQueuedControlFrames != 0) {
            encoder = new Http2ControlFrameLimitEncoder(encoder, maxQueuedControlFrames);
        }
        if (encoderEnforceMaxConcurrentStreams) {
            if (connection.isServer()) {
                encoder.close();
                reader.close();
                throw new IllegalArgumentException(
                        "encoderEnforceMaxConcurrentStreams: " + encoderEnforceMaxConcurrentStreams +
                        " not supported for server");
            }
            encoder = new StreamBufferingEncoder(encoder);
        }

        DefaultHttp2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader,
            promisedRequestVerifier(), isAutoAckSettingsFrame(), isAutoAckPingFrame(), isValidateHeaders());
        return buildFromCodec(decoder, encoder);
    }

    private T buildFromCodec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
        int maxConsecutiveEmptyDataFrames = decoderEnforceMaxConsecutiveEmptyDataFrames();
        if (maxConsecutiveEmptyDataFrames > 0) {
            decoder = new Http2EmptyDataFrameConnectionDecoder(decoder, maxConsecutiveEmptyDataFrames);
        }
        final int maxRstFrames;
        if (maxRstFramesPerWindow == null) {
            // Only enable by default on the server.
            if (isServer()) {
                maxRstFrames = DEFAULT_MAX_RST_FRAMES_PER_CONNECTION_FOR_SERVER;
            } else {
                maxRstFrames = 0;
            }
        } else {
            maxRstFrames = maxRstFramesPerWindow;
        }
        if (maxRstFrames > 0 && secondsPerWindow > 0) {
            decoder = new Http2MaxRstFrameDecoder(decoder, maxRstFrames, secondsPerWindow);
        }
        final T handler;
        try {
            // Call the abstract build method
            handler = build(decoder, encoder, initialSettings);
        } catch (Throwable t) {
            encoder.close();
            decoder.close();
            throw new IllegalStateException("failed to build an Http2ConnectionHandler", t);
        }

        // Setup post build options
        handler.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
        if (handler.decoder().frameListener() == null) {
            handler.decoder().frameListener(frameListener);
        }
        return handler;
    }

    /**
     * Implement this method to create a new {@link Http2ConnectionHandler} or its subtype instance.
     * <p>
     * The return of this method will be subject to the following:
     * <ul>
     *   <li>{@link #frameListener(Http2FrameListener)} will be set if not already set in the decoder</li>
     *   <li>{@link #gracefulShutdownTimeoutMillis(long)} will always be set</li>
     * </ul>
     */
    protected abstract T build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                               Http2Settings initialSettings) throws Exception;

    /**
     * Returns {@code this}.
     */
    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    private void enforceNonCodecConstraints(String rejected) {
        enforceConstraint(rejected, "server/connection", decoder);
        enforceConstraint(rejected, "server/connection", encoder);
    }

    private static void enforceConstraint(String methodName, String rejectorName, Object value) {
        if (value != null) {
            throw new IllegalStateException(
                    methodName + "() cannot be called because " + rejectorName + "() has been called already.");
        }
    }
}

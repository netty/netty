/*
 * Copyright 2015 The Netty Project
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

import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

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
@UnstableApi
public abstract class AbstractHttp2ConnectionHandlerBuilder<T extends Http2ConnectionHandler,
                                                            B extends AbstractHttp2ConnectionHandlerBuilder<T, B>> {

    private static final long DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS = MILLISECONDS.convert(30, SECONDS);
    private static final SensitivityDetector DEFAULT_HEADER_SENSITIVITY_DETECTOR = Http2HeadersEncoder.NEVER_SENSITIVE;

    // The properties that can always be set.
    private Http2Settings initialSettings = new Http2Settings();
    private Http2FrameListener frameListener;
    private long gracefulShutdownTimeoutMillis = DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;

    // The property that will prohibit connection() and codec() if set by server(),
    // because this property is used only when this builder creates a Http2Connection.
    private Boolean isServer;

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
     * Returns the graceful shutdown timeout of the {@link Http2Connection} in milliseconds.
     */
    protected long gracefulShutdownTimeoutMillis() {
        return gracefulShutdownTimeoutMillis;
    }

    /**
     * Sets the graceful shutdown timeout of the {@link Http2Connection} in milliseconds.
     */
    protected B gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
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
        this.encoderIgnoreMaxHeaderListSize = ignoreMaxHeaderListSize;
        return self();
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
            connection = new DefaultHttp2Connection(isServer());
        }

        return buildFromConnection(connection);
    }

    private T buildFromConnection(Http2Connection connection) {
        Http2FrameReader reader = new DefaultHttp2FrameReader(isValidateHeaders());
        Http2FrameWriter writer = encoderIgnoreMaxHeaderListSize == null ?
                new DefaultHttp2FrameWriter(headerSensitivityDetector()) :
                new DefaultHttp2FrameWriter(headerSensitivityDetector(), encoderIgnoreMaxHeaderListSize);

        if (frameLogger != null) {
            reader = new Http2InboundFrameLogger(reader, frameLogger);
            writer = new Http2OutboundFrameLogger(writer, frameLogger);
        }

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        boolean encoderEnforceMaxConcurrentStreams = encoderEnforceMaxConcurrentStreams();

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

        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        return buildFromCodec(decoder, encoder);
    }

    private T buildFromCodec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
        final T handler;
        try {
            // Call the abstract build method
            handler = build(decoder, encoder, initialSettings);
        } catch (Throwable t) {
            encoder.close();
            decoder.close();
            throw new IllegalStateException("failed to build a Http2ConnectionHandler", t);
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

    private void enforceNonCodecConstraints(String rejectee) {
        enforceConstraint(rejectee, "server/connection", decoder);
        enforceConstraint(rejectee, "server/connection", encoder);
    }

    private static void enforceConstraint(String methodName, String rejectorName, Object value) {
        if (value != null) {
            throw new IllegalStateException(
                    methodName + "() cannot be called because " + rejectorName + "() has been called already.");
        }
    }
}

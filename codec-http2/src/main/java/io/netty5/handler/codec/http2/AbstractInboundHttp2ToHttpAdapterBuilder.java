/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeadersFactory;
import io.netty5.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

/**
 * A skeletal builder implementation of {@link InboundHttp2ToHttpAdapter} and its subtypes.
 */
@UnstableApi
public abstract class AbstractInboundHttp2ToHttpAdapterBuilder<
        T extends InboundHttp2ToHttpAdapter, B extends AbstractInboundHttp2ToHttpAdapterBuilder<T, B>> {

    private final Http2Connection connection;
    private int maxContentLength;
    private HttpHeadersFactory headersFactory;
    private HttpHeadersFactory trailersFactory;
    private boolean propagateSettings;

    /**
     * Creates a new {@link InboundHttp2ToHttpAdapter} builder for the specified {@link Http2Connection}.
     *
     * @param connection the object which will provide connection notification events
     *                   for the current connection
     */
    protected AbstractInboundHttp2ToHttpAdapterBuilder(Http2Connection connection) {
        this.connection = requireNonNull(connection, "connection");
        headersFactory = DefaultHttpHeadersFactory.headersFactory();
        trailersFactory = DefaultHttpHeadersFactory.trailersFactory();
    }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * Returns the {@link Http2Connection}.
     */
    protected Http2Connection connection() {
        return connection;
    }

    /**
     * Returns the maximum length of the message content.
     */
    protected int maxContentLength() {
        return maxContentLength;
    }

    /**
     * Specifies the maximum length of the message content.
     *
     * @param maxContentLength the maximum length of the message content. If the length of the message content
     *        exceeds this value, a {@link TooLongFrameException} will be raised
     * @return {@link AbstractInboundHttp2ToHttpAdapterBuilder} the builder for the {@link InboundHttp2ToHttpAdapter}
     */
    protected B maxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return self();
    }

    /**
     * Return the {@link HttpHeadersFactory} used to create HTTP/1 header objects
     */
    protected HttpHeadersFactory headersFactory() {
        return headersFactory;
    }

    /**
     * Return the {@link HttpHeadersFactory} used to create HTTP/1 header objects
     */
    protected HttpHeadersFactory trailersFactory() {
        return trailersFactory;
    }

    /**
     * Specify the {@link HttpHeadersFactory} used when creating header objects.
     * @param headersFactory The header factory.
     */
    protected B headersFactory(HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory, "headersFactory");
        return self();
    }

    /**
     * Specify the {@link  HttpHeadersFactory} used when creating trailers.
     * @param trailersFactory The header factory.
     */
    protected B trailersFactory(HttpHeadersFactory trailersFactory) {
        this.trailersFactory = requireNonNull(trailersFactory, "trailersFactory");
        return self();
    }

    /**
     * Returns {@code true} if a read settings frame should be propagated along the channel pipeline.
     */
    protected boolean isPropagateSettings() {
        return propagateSettings;
    }

    /**
     * Specifies whether a read settings frame should be propagated along the channel pipeline.
     *
     * @param propagate if {@code true} read settings will be passed along the pipeline. This can be useful
     *                     to clients that need hold off sending data until they have received the settings.
     * @return {@link AbstractInboundHttp2ToHttpAdapterBuilder} the builder for the {@link InboundHttp2ToHttpAdapter}
     */
    protected B propagateSettings(boolean propagate) {
        propagateSettings = propagate;
        return self();
    }

    /**
     * Builds/creates a new {@link InboundHttp2ToHttpAdapter} instance using this builder's current settings.
     */
    protected T build() {
        final T instance;
        try {
            instance = build(connection(), maxContentLength(), isPropagateSettings(),
                    headersFactory(), trailersFactory());
        } catch (Throwable t) {
            throw new IllegalStateException("failed to create a new InboundHttp2ToHttpAdapter", t);
        }
        connection.addListener(instance);
        return instance;
    }

    /**
     * Creates a new {@link InboundHttp2ToHttpAdapter} with the specified properties.
     */
    protected abstract T build(Http2Connection connection, int maxContentLength, boolean propagateSettings,
                               HttpHeadersFactory headersFactory, HttpHeadersFactory trailersFactory) throws Exception;
}

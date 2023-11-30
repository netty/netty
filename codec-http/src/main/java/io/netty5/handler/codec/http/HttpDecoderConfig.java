/*
 * Copyright 2023 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeadersFactory;

import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static java.util.Objects.requireNonNull;

/**
 * A configuration object for specifying the behaviour of {@link HttpObjectDecoder} and its subclasses.
 * <p>
 * The {@link HttpDecoderConfig} objects are mutable to reduce allocation,
 * but also {@link Cloneable} in case a defensive copy is needed.
 */
public class HttpDecoderConfig implements Cloneable {
    private boolean chunkedSupported = HttpObjectDecoder.DEFAULT_CHUNKED_SUPPORTED;
    private HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.headersFactory();
    private HttpHeadersFactory trailersFactory = DefaultHttpHeadersFactory.trailersFactory();
    private boolean allowDuplicateContentLengths = HttpObjectDecoder.DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS;
    private int maxInitialLineLength = HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
    private int maxHeaderSize = HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
    private int initialBufferSize = HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE;

    public int getInitialBufferSize() {
        return initialBufferSize;
    }

    /**
     * Set the initial size of the temporary buffer used when parsing the lines of the HTTP headers.
     *
     * @param initialBufferSize The buffer size in bytes.
     * @return This decoder config.
     */
    public HttpDecoderConfig setInitialBufferSize(int initialBufferSize) {
        checkPositive(initialBufferSize, "initialBufferSize");
        this.initialBufferSize = initialBufferSize;
        return this;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    /**
     * Set the maximum length of the first line of the HTTP header.
     * This limits how much memory Netty will use when parsed the initial HTTP header line.
     * You would typically set this to the same value as {@link #setMaxHeaderSize(int)}.
     *
     * @param maxInitialLineLength The maximum length, in bytes.
     * @return This decoder config.
     */
    public HttpDecoderConfig setMaxInitialLineLength(int maxInitialLineLength) {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    /**
     * Set the maximum line length of header lines.
     * This limits how much memory Netty will use when parsing HTTP header key-value pairs.
     * You would typically set this to the same value as {@link #setMaxInitialLineLength(int)}.
     *
     * @param maxHeaderSize The maximum length, in bytes.
     * @return This decoder config.
     */
    public HttpDecoderConfig setMaxHeaderSize(int maxHeaderSize) {
        checkPositive(maxHeaderSize, "maxHeaderSize");
        this.maxHeaderSize = maxHeaderSize;
        return this;
    }

    public boolean isChunkedSupported() {
        return chunkedSupported;
    }

    /**
     * Set whether {@code Transfer-Encoding: Chunked} should be supported.
     *
     * @param chunkedSupported if {@code false}, then a {@code Transfer-Encoding: Chunked} header will produce an error,
     * instead of a stream of chunks.
     * @return This decoder config.
     */
    public HttpDecoderConfig setChunkedSupported(boolean chunkedSupported) {
        this.chunkedSupported = chunkedSupported;
        return this;
    }

    public HttpHeadersFactory getHeadersFactory() {
        return headersFactory;
    }

    /**
     * Set the {@link HttpHeadersFactory} to use when creating new HTTP headers objects.
     * The default headers factory is {@link DefaultHttpHeadersFactory#headersFactory()}.
     * <p>
     * For the purpose of {@link #clone()}, it is assumed that the factory is either immutable, or can otherwise be
     * shared across different decoders and decoder configs.
     *
     * @param headersFactory The header factory to use.
     * @return This decoder config.
     */
    public HttpDecoderConfig setHeadersFactory(HttpHeadersFactory headersFactory) {
        requireNonNull(headersFactory, "headersFactory");
        this.headersFactory = headersFactory;
        return this;
    }

    public boolean isAllowDuplicateContentLengths() {
        return allowDuplicateContentLengths;
    }

    /**
     * Set whether more than one {@code Content-Length} header is allowed.
     * You usually want to disallow this (which is the default) as multiple {@code Content-Length} headers can indicate
     * a request- or response-splitting attack.
     *
     * @param allowDuplicateContentLengths set to {@code true} to allow multiple content length headers.
     * @return This decoder config.
     */
    public HttpDecoderConfig setAllowDuplicateContentLengths(boolean allowDuplicateContentLengths) {
        this.allowDuplicateContentLengths = allowDuplicateContentLengths;
        return this;
    }

    /**
     * Set whether header validation should be enabled or not.
     * This works by changing the configured {@linkplain #setHeadersFactory(HttpHeadersFactory) header factory}
     * and {@linkplain #setTrailersFactory(HttpHeadersFactory) trailer factory}.
     * <p>
     * You usually want header validation enabled (which is the default) in order to prevent request-/response-splitting
     * attacks.
     *
     * @param validateHeaders set to {@code false} to disable header validation.
     * @return This decoder config.
     */
    public HttpDecoderConfig setValidateHeaders(boolean validateHeaders) {
        headersFactory = withValidation(DefaultHttpHeadersFactory.headersFactory(), validateHeaders);
        trailersFactory = withValidation(DefaultHttpHeadersFactory.trailersFactory(), validateHeaders);
        return this;
    }

    private static HttpHeadersFactory withValidation(DefaultHttpHeadersFactory factory, boolean validateHeaders) {
        return factory.withNameValidation(validateHeaders)
                .withValueValidation(validateHeaders)
                .withCookieValidation(validateHeaders);
    }

    public HttpHeadersFactory getTrailersFactory() {
        return trailersFactory;
    }

    /**
     * Set the {@link HttpHeadersFactory} used to create HTTP trailers.
     * This differs from {@link #setHeadersFactory(HttpHeadersFactory)} in that trailers have different validation
     * requirements.
     * The default trailer factory is {@link DefaultHttpHeadersFactory#headersFactory()}.
     * <p>
     * For the purpose of {@link #clone()}, it is assumed that the factory is either immutable, or can otherwise be
     * shared across different decoders and decoder configs.
     *
     * @param trailersFactory The headers factory to use for creating trailers.
     * @return This decoder config.
     */
    public HttpDecoderConfig setTrailersFactory(HttpHeadersFactory trailersFactory) {
        requireNonNull(trailersFactory, "trailersFactory");
        this.trailersFactory = trailersFactory;
        return this;
    }

    @Override
    public HttpDecoderConfig clone() {
        try {
            return (HttpDecoderConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}

/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpRequestDecoder.HttpRequestDecoderBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_CHUNKED_SUPPORTED;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_VALIDATE_HEADERS;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Abstract base class for {@link HttpObjectDecoder} builders.
 *
 * @param <T> the type of {@link HttpObjectDecoder} that is being built
 * @param <B> the concrete implementation of this {@link AbstractHttpObjectDecoderBuilder}
 *
 * @see HttpRequestDecoderBuilder
 */
public abstract class AbstractHttpObjectDecoderBuilder
        <T extends HttpObjectDecoder, B extends AbstractHttpObjectDecoderBuilder<T, B>> {

    protected int maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    protected int maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    protected boolean chunkedSupported = DEFAULT_CHUNKED_SUPPORTED;
    protected int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    protected boolean validateHeaders = DEFAULT_VALIDATE_HEADERS;
    protected int initialBufferSize = DEFAULT_INITIAL_BUFFER_SIZE;

    // ReentrantLock used for options to only allocate HashMap when actually in use.
    protected final Lock optionsLock = new ReentrantLock();
    protected volatile Map<HttpDecoderOption<?>, Object> options;

    AbstractHttpObjectDecoderBuilder() {
        // Disallow extending from a different package.
    }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * The maximum length of the initial line (e.g. {@code "GET / HTTP/1.0"} or {@code "HTTP/1.0 200 OK"}). If the
     * length of the initial line exceeds this value, a {@link TooLongFrameException} will be raised.
     */
    public B maxInitialLineLength(int maxInitialLineLength) {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        this.maxInitialLineLength = maxInitialLineLength;
        return self();
    }

    /**
     * The maximum length of all headers. If the sum of the length of each header exceeds this value, a {@link
     * TooLongFrameException} will be raised.
     */
    public B maxHeaderSize(int maxHeaderSize) {
        checkPositive(maxHeaderSize, "maxHeaderSize");
        this.maxHeaderSize = maxHeaderSize;
        return self();
    }

    public B chunkedSupported(boolean chunkedSupported) {
        this.chunkedSupported = chunkedSupported;
        return self();
    }

    /**
     * The maximum length of the content or each chunk. If the content length (or the length of each chunk) exceeds this
     * value, the content or chunk will be split into multiple {@link HttpContent}s whose length is {@code maxChunkSize}
     * at maximum.
     */
    public B maxChunkSize(int maxChunkSize) {
        checkPositive(maxChunkSize, "maxChunkSize");
        this.maxChunkSize = maxChunkSize;
        return self();
    }

    public B validateHeaders(boolean validateHeaders) {
        this.validateHeaders = validateHeaders;
        return self();
    }

    public B initialBufferSize(int initialBufferSize) {
        checkPositive(initialBufferSize, "initialBufferSize");
        this.initialBufferSize = initialBufferSize;
        return self();
    }

    /**
     * Specify a {@link HttpDecoderOption} to configure this {@link HttpObjectDecoder} with special behavior. Use a
     * value of {@code null} to remove a previously set {@link HttpDecoderOption}.
     *
     * @see HttpDecoderOption
     */
    public <O> B option(HttpDecoderOption<O> option, O value) {
        checkNotNull(option, "option");
        optionsLock.lock();
        try {
            if (value == null) {
                if (options != null) {
                    options.remove(option);
                }
            } else {
                if (options == null) {
                    options = new LinkedHashMap<HttpDecoderOption<?>, Object>();
                }
                options.put(option, value);
            }
        } finally {
            optionsLock.unlock();
        }
        return self();
    }

    /**
     * Specify a collection of {@link HttpDecoderOption}s to configure this {@link HttpObjectDecoder} with special
     * behavior. These options overwrite and append to any existing options, but it will not clear any other unspecified
     * options.
     *
     * @see HttpDecoderOption
     */
    public B options(Map<HttpDecoderOption<?>, Object> options) {
        checkNotNull(options, "options");
        if (!options.isEmpty()) {
            optionsLock.lock();
            try {
                if (this.options == null) {
                    this.options = new LinkedHashMap<HttpDecoderOption<?>, Object>(options);
                } else {
                    this.options.putAll(options);
                }
            } finally {
                optionsLock.unlock();
            }
        }
        return self();
    }

    /**
     * Clear any previously set {@link HttpDecoderOption}s.
     */
    public B clearOptions() {
        optionsLock.lock();
        try {
            if (options != null) {
                options.clear();
            }
        } finally {
            optionsLock.unlock();
        }
        return self();
    }

    public abstract T build();
}

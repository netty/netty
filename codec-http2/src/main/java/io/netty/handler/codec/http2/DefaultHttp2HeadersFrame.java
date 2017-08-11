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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link Http2HeadersFrame} implementation.
 */
@UnstableApi
public final class DefaultHttp2HeadersFrame extends AbstractHttp2StreamFrame implements Http2HeadersFrame {
    private final Http2Headers headers;
    private final boolean endStream;
    private final int padding;

    /**
     * Equivalent to {@code new DefaultHttp2HeadersFrame(headers, false)}.
     *
     * @param headers the non-{@code null} headers to send
     */
    public DefaultHttp2HeadersFrame(Http2Headers headers) {
        this(headers, false);
    }

    /**
     * Equivalent to {@code new DefaultHttp2HeadersFrame(headers, endStream, 0)}.
     *
     * @param headers the non-{@code null} headers to send
     */
    public DefaultHttp2HeadersFrame(Http2Headers headers, boolean endStream) {
        this(headers, endStream, 0);
    }

    /**
     * Construct a new headers message.
     *
     * @param headers the non-{@code null} headers to send
     * @param endStream whether these headers should terminate the stream
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    public DefaultHttp2HeadersFrame(Http2Headers headers, boolean endStream, int padding) {
        this.headers = checkNotNull(headers, "headers");
        this.endStream = endStream;
        verifyPadding(padding);
        this.padding = padding;
    }

    @Override
    public DefaultHttp2HeadersFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public String name() {
        return "HEADERS";
    }

    @Override
    public Http2Headers headers() {
        return headers;
    }

    @Override
    public boolean isEndStream() {
        return endStream;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(stream=" + stream() + ", headers=" + headers
               + ", endStream=" + endStream + ", padding=" + padding + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2HeadersFrame)) {
            return false;
        }
        DefaultHttp2HeadersFrame other = (DefaultHttp2HeadersFrame) o;
        return super.equals(other) && headers.equals(other.headers)
                && endStream == other.endStream && padding == other.padding;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + headers.hashCode();
        hash = hash * 31 + (endStream ? 0 : 1);
        hash = hash * 31 + padding;
        return hash;
    }
}

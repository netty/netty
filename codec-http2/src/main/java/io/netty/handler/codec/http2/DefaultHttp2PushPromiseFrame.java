/*
 * Copyright 2018 The Netty Project
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
 * The default {@link Http2PushPromiseFrame} implementation.
 */
@UnstableApi
public final class DefaultHttp2PushPromiseFrame extends AbstractHttp2StreamFrame implements Http2PushPromiseFrame {
    private final int padding;
    private final Http2Headers headers;

    private Http2FrameStream promisedStream;

    /**
     * Construct a new push promise message.
     *
     * @param headers the non-{@code null} headers to send
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    public DefaultHttp2PushPromiseFrame(Http2Headers headers, int padding) {
        this.padding = padding;
        this.headers = checkNotNull(headers, "headers");
        verifyPadding(padding);
    }

    /**
     * Construct a new push promise message with no padding.
     *
     * @param headers the non-{@code null} headers to send
     */
    public DefaultHttp2PushPromiseFrame(Http2Headers headers) {
        this(headers, 0);
    }

    @Override
    public DefaultHttp2PushPromiseFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public Http2FrameStream promisedStream() {
        return promisedStream;
    }

    @Override
    public DefaultHttp2PushPromiseFrame promisedStream(Http2FrameStream stream) {
        promisedStream = checkNotNull(stream, "promisedStream");
        return this;
    }

    @Override
    public Http2Headers headers() {
        return headers;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public boolean isEndStream() {
        return false;
    }

    @Override
    public String name() {
        return "PUSH_PROMISE";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(stream=" + stream() + ", headers=" + headers
                + ", promisedStream=" + promisedStream() + ", padding=" + padding + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2PushPromiseFrame)) {
            return false;
        }
        DefaultHttp2PushPromiseFrame other = (DefaultHttp2PushPromiseFrame) o;
        return super.equals(other) && headers.equals(other.headers)
                && padding == other.padding;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + headers.hashCode();
        hash = hash * 31 + padding;
        return hash;
    }
}

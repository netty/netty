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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;

/**
 * The default {@link Http2DataFrame} implementation.
 */
public final class DefaultHttp2DataFrame extends AbstractHttp2StreamFrame implements Http2DataFrame {
    private final ByteBuf content;
    private final boolean endStream;
    private final int padding;

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(content, false)}.
     *
     * @param content non-{@code null} payload
     */
    public DefaultHttp2DataFrame(ByteBuf content) {
        this(content, false);
    }

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, endStream)}.
     *
     * @param endStream whether this data should terminate the stream
     */
    public DefaultHttp2DataFrame(boolean endStream) {
        this(Unpooled.EMPTY_BUFFER, endStream);
    }

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(content, endStream, 0)}.
     *
     * @param content non-{@code null} payload
     * @param endStream whether this data should terminate the stream
     */
    public DefaultHttp2DataFrame(ByteBuf content, boolean endStream) {
        this(content, endStream, 0);
    }

    /**
     * Construct a new data message.
     *
     * @param content non-{@code null} payload
     * @param endStream whether this data should terminate the stream
     * @param padding additional bytes that should be added to obscure the true content size
     */
    public DefaultHttp2DataFrame(ByteBuf content, boolean endStream, int padding) {
        this.content = checkNotNull(content, "content");
        this.endStream = endStream;
        if (padding < 0 || padding > Http2CodecUtil.MAX_UNSIGNED_BYTE) {
            throw new IllegalArgumentException("padding must be non-negative and less than 256");
        }
        this.padding = padding;
    }

    @Override
    public DefaultHttp2DataFrame setStream(Object stream) {
      super.setStream(stream);
      return this;
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
    public ByteBuf content() {
        if (content.refCnt() <= 0) {
            throw new IllegalReferenceCountException(content.refCnt());
        }
        return content;
    }

    @Override
    public DefaultHttp2DataFrame copy() {
        return new DefaultHttp2DataFrame(content().copy(), endStream, padding);
    }

    @Override
    public DefaultHttp2DataFrame duplicate() {
        return new DefaultHttp2DataFrame(content().duplicate(), endStream, padding);
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }

    @Override
    public DefaultHttp2DataFrame retain() {
        content.retain();
        return this;
    }

    @Override
    public DefaultHttp2DataFrame retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        return "DefaultHttp2DataFrame(stream=" + stream() + ", content=" + content
            + ", endStream=" + endStream + ", padding=" + padding + ")";
    }

    @Override
    public DefaultHttp2DataFrame touch() {
        content.touch();
        return this;
    }

    @Override
    public DefaultHttp2DataFrame touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2DataFrame)) {
            return false;
        }
        DefaultHttp2DataFrame other = (DefaultHttp2DataFrame) o;
        return super.equals(other) && content.equals(other.content())
            && endStream == other.endStream && padding == other.padding;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + content.hashCode();
        hash = hash * 31 + (endStream ? 0 : 1);
        hash = hash * 31 + padding;
        return hash;
    }
}

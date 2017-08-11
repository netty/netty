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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link Http2DataFrame} implementation.
 */
@UnstableApi
public final class DefaultHttp2DataFrame extends AbstractHttp2StreamFrame implements Http2DataFrame {
    private final ByteBuf content;
    private final boolean endStream;
    private final int padding;
    private final int initialFlowControlledBytes;

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
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    public DefaultHttp2DataFrame(ByteBuf content, boolean endStream, int padding) {
        this.content = checkNotNull(content, "content");
        this.endStream = endStream;
        verifyPadding(padding);
        this.padding = padding;
        if (content().readableBytes() + (long) padding > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("content + padding must be <= Integer.MAX_VALUE");
        }
        initialFlowControlledBytes = content().readableBytes() + padding;
    }

    @Override
    public DefaultHttp2DataFrame stream(Http2FrameStream stream) {
        super.stream(stream);
        return this;
    }

    @Override
    public String name() {
        return "DATA";
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
    public int initialFlowControlledBytes() {
        return initialFlowControlledBytes;
    }

    @Override
    public DefaultHttp2DataFrame copy() {
        return replace(content().copy());
    }

    @Override
    public DefaultHttp2DataFrame duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public DefaultHttp2DataFrame retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public DefaultHttp2DataFrame replace(ByteBuf content) {
        return new DefaultHttp2DataFrame(content, endStream, padding);
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
        return StringUtil.simpleClassName(this) + "(stream=" + stream() + ", content=" + content
               + ", endStream=" + endStream + ", padding=" + padding + ')';
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

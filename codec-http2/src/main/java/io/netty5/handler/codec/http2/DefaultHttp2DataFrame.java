/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferClosedException;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import static io.netty5.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static java.util.Objects.requireNonNull;

/**
 * The default {@link Http2DataFrame} implementation.
 */
@UnstableApi
public final class DefaultHttp2DataFrame extends AbstractHttp2StreamFrame implements Http2DataFrame {
    private final Buffer content;
    private final boolean endStream;
    private final int padding;
    private final int initialFlowControlledBytes;

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(content, false)}.
     * <p>
     * Ownership of the payload buffer moves into the {@link DefaultHttp2DataFrame} instance.
     *
     * @param content non-{@code null} payload
     */
    public DefaultHttp2DataFrame(Buffer content) {
        this(content, false);
    }

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, endStream)}.
     *
     * @param endStream whether this data should terminate the stream
     */
    public DefaultHttp2DataFrame(boolean endStream) {
        this(DefaultBufferAllocators.onHeapAllocator().allocate(0), endStream, 0);
    }

    /**
     * Equivalent to {@code new DefaultHttp2DataFrame(content, endStream, 0)}.
     * <p>
     * Ownership of the payload buffer moves into the {@link DefaultHttp2DataFrame} instance.
     *
     * @param content non-{@code null} payload
     * @param endStream whether this data should terminate the stream
     */
    public DefaultHttp2DataFrame(Buffer content, boolean endStream) {
        this(content, endStream, 0);
    }

    /**
     * Construct a new data message.
     * <p>
     * Ownership of the payload buffer moves into the {@link DefaultHttp2DataFrame} instance.
     *
     * @param content non-{@code null} payload
     * @param endStream whether this data should terminate the stream
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    public DefaultHttp2DataFrame(Buffer content, boolean endStream, int padding) {
        this.content = requireNonNull(content, "content").moveAndClose();
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
    public Buffer content() {
        if (content.isAccessible()) {
            return content;
        }
        throw new BufferClosedException();
    }

    @Override
    public int initialFlowControlledBytes() {
        return initialFlowControlledBytes;
    }

    @Override
    public DefaultHttp2DataFrame copy() {
        return new DefaultHttp2DataFrame(content.copy(), endStream, padding);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(stream=" + stream() + ", content=" + content
               + ", endStream=" + endStream + ", padding=" + padding + ')';
    }

    @Override
    public void close() {
        content.close();
    }

    @Override
    public Http2DataFrame moveAndClose() {
        return new DefaultHttp2DataFrame(content, endStream, padding);
    }

    @Override
    public boolean isAccessible() {
        return content.isAccessible();
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

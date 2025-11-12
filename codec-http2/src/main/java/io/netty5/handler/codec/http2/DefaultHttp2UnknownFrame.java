/*
 * Copyright 2017 The Netty Project
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
import io.netty5.buffer.BufferHolder;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;

@UnstableApi
public final class DefaultHttp2UnknownFrame extends BufferHolder<Http2UnknownFrame> implements Http2UnknownFrame {
    private final short frameType;
    private final Http2Flags flags;
    private Http2FrameStream stream;

    public DefaultHttp2UnknownFrame(short frameType, Http2Flags flags) {
        this(frameType, flags, onHeapAllocator().allocate(0));
    }

    public DefaultHttp2UnknownFrame(short frameType, Http2Flags flags, Buffer data) {
        super(data);
        this.frameType = frameType;
        this.flags = flags;
    }

    @Override
    public Buffer content() {
        return getBuffer();
    }

    @Override
    public Http2FrameStream stream() {
        return stream;
    }

    @Override
    public DefaultHttp2UnknownFrame stream(Http2FrameStream stream) {
        this.stream = stream;
        return this;
    }

    @Override
    public short frameType() {
        return frameType;
    }

    @Override
    public Http2Flags flags() {
        return flags;
    }

    @Override
    public String name() {
        return "UNKNOWN";
    }

    @Override
    public DefaultHttp2UnknownFrame copy() {
        return new DefaultHttp2UnknownFrame(frameType, flags, getBuffer().copy()).stream(stream);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(frameType=" + frameType + ", stream=" + stream +
               ", flags=" + flags + ", content=" + getBuffer().toString(StandardCharsets.UTF_8) + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2UnknownFrame)) {
            return false;
        }
        DefaultHttp2UnknownFrame other = (DefaultHttp2UnknownFrame) o;
        Http2FrameStream otherStream = other.stream();
        return (stream == otherStream || otherStream != null && otherStream.equals(stream))
               && flags.equals(other.flags())
               && frameType == other.frameType()
               && super.equals(other);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + frameType;
        hash = hash * 31 + flags.hashCode();
        if (stream != null) {
            hash = hash * 31 + stream.hashCode();
        }

        return hash;
    }

    @Override
    public Http2UnknownFrame move() {
        return new DefaultHttp2UnknownFrame(frameType, flags, getBuffer()).stream(stream);
    }
}

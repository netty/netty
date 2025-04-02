/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.StringUtil;

public final class DefaultSpdyUnknownFrame extends DefaultByteBufHolder implements SpdyUnknownFrame {
    private final int frameType;
    private final byte flags;

    public DefaultSpdyUnknownFrame(int frameType, byte flags, ByteBuf data) {
        super(data);
        this.frameType = frameType;
        this.flags = flags;
    }

    @Override
    public int frameType() {
        return frameType;
    }

    @Override
    public byte flags() {
        return flags;
    }

    @Override
    public DefaultSpdyUnknownFrame copy() {
        return replace(content().copy());
    }

    @Override
    public DefaultSpdyUnknownFrame duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public DefaultSpdyUnknownFrame retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public DefaultSpdyUnknownFrame replace(final ByteBuf content) {
        return new DefaultSpdyUnknownFrame(frameType, flags, content);
    }

    @Override
    public DefaultSpdyUnknownFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultSpdyUnknownFrame retain(final int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultSpdyUnknownFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultSpdyUnknownFrame touch(final Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof DefaultSpdyUnknownFrame)) {
            return false;
        }
        final DefaultSpdyUnknownFrame that = (DefaultSpdyUnknownFrame) o;
        return frameType == that.frameType
            && flags == that.flags
            && super.equals(that);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + frameType;
        result = 31 * result + flags;
        return result;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(" + "frameType=" + frameType +
            ", flags=" + flags + ", content=" + contentToString() +
            ')';
    }
}

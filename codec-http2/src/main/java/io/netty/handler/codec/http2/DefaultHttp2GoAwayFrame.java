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
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;

/**
 * The default {@link Http2GoAwayFrame} implementation.
 */
public final class DefaultHttp2GoAwayFrame extends DefaultByteBufHolder implements Http2GoAwayFrame {
    private final long errorCode;
    private int extraStreamIds;

    /**
     * Equivalent to {@code new DefaultHttp2GoAwayFrame(error.code())}.
     *
     * @param error non-{@code null} reason for the go away
     */
    public DefaultHttp2GoAwayFrame(Http2Error error) {
        this(error.code());
    }

    /**
     * Equivalent to {@code new DefaultHttp2GoAwayFrame(content, Unpooled.EMPTY_BUFFER)}.
     *
     * @param error reason for the go away
     */
    public DefaultHttp2GoAwayFrame(long errorCode) {
        this(errorCode, Unpooled.EMPTY_BUFFER);
    }

    /**
     * Equivalent to {@code new DefaultHttp2GoAwayFrame(error.code(), content)}.
     *
     * @param error non-{@code null} reason for the go away
     * @param content non-{@code null} debug data
     */
    public DefaultHttp2GoAwayFrame(Http2Error error, ByteBuf content) {
        this(error.code(), content);
    }

    /**
     * Construct a new GOAWAY message.
     *
     * @param error reason for the go away
     * @param content non-{@code null} debug data
     */
    public DefaultHttp2GoAwayFrame(long errorCode, ByteBuf content) {
        super(content);
        this.errorCode = errorCode;
    }

    @Override
    public long errorCode() {
        return errorCode;
    }

    @Override
    public int extraStreamIds() {
        return extraStreamIds;
    }

    @Override
    public DefaultHttp2GoAwayFrame setExtraStreamIds(int extraStreamIds) {
        if (extraStreamIds < 0) {
            throw new IllegalArgumentException("extraStreamIds must be non-negative");
        }
        this.extraStreamIds = extraStreamIds;
        return this;
    }

    @Override
    public DefaultHttp2GoAwayFrame copy() {
        return new DefaultHttp2GoAwayFrame(errorCode, content().copy()).setExtraStreamIds(extraStreamIds);
    }

    @Override
    public DefaultHttp2GoAwayFrame duplicate() {
        return new DefaultHttp2GoAwayFrame(errorCode, content().duplicate()).setExtraStreamIds(extraStreamIds);
    }

    @Override
    public DefaultHttp2GoAwayFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultHttp2GoAwayFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        return "DefaultHttp2GoAwayFrame(errorCode=" + errorCode + ", content=" + content()
            + ", extraStreamIds=" + extraStreamIds + ")";
    }

    @Override
    public DefaultHttp2GoAwayFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultHttp2GoAwayFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2GoAwayFrame)) {
            return false;
        }
        DefaultHttp2GoAwayFrame other = (DefaultHttp2GoAwayFrame) o;
        return super.equals(o) && errorCode == other.errorCode && content().equals(other.content())
            && extraStreamIds == other.extraStreamIds;
    }

    @Override
    public int hashCode() {
        int hash = 237395317;
        hash = hash * 31 + (int) (errorCode ^ (errorCode >>> 32));
        hash = hash * 31 + content().hashCode();
        hash = hash * 31 + extraStreamIds;
        return hash;
    }
}

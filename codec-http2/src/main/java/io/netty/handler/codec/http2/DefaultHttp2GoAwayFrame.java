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
package io.netty.handler.codec.http2;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default {@link Http2GoAwayFrame} implementation.
 */
@UnstableApi
public final class DefaultHttp2GoAwayFrame extends DefaultByteBufHolder implements Http2GoAwayFrame {

    private final long errorCode;
    private final int lastStreamId;
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
     * @param errorCode reason for the go away
     */
    public DefaultHttp2GoAwayFrame(long errorCode) {
        this(errorCode, Unpooled.EMPTY_BUFFER);
    }

    /**
     *
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
     * @param errorCode reason for the go away
     * @param content non-{@code null} debug data
     */
    public DefaultHttp2GoAwayFrame(long errorCode, ByteBuf content) {
        this(-1, errorCode, content);
    }

    /**
     * Construct a new GOAWAY message.
     *
     * This constructor is for internal use only. A user should not have to specify a specific last stream identifier,
     * but use {@link #setExtraStreamIds(int)} instead.
     */
    DefaultHttp2GoAwayFrame(int lastStreamId, long errorCode, ByteBuf content) {
        super(content);
        this.errorCode = errorCode;
        this.lastStreamId = lastStreamId;
    }

    @Override
    public String name() {
        return "GOAWAY";
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
    public Http2GoAwayFrame setExtraStreamIds(int extraStreamIds) {
        checkPositiveOrZero(extraStreamIds, "extraStreamIds");
        this.extraStreamIds = extraStreamIds;
        return this;
    }

    @Override
    public int lastStreamId() {
        return lastStreamId;
    }

    @Override
    public Http2GoAwayFrame copy() {
        return new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, content().copy());
    }

    @Override
    public Http2GoAwayFrame duplicate() {
        return (Http2GoAwayFrame) super.duplicate();
    }

    @Override
    public Http2GoAwayFrame retainedDuplicate() {
        return (Http2GoAwayFrame) super.retainedDuplicate();
    }

    @Override
    public Http2GoAwayFrame replace(ByteBuf content) {
        return new DefaultHttp2GoAwayFrame(errorCode, content).setExtraStreamIds(extraStreamIds);
    }

    @Override
    public Http2GoAwayFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public Http2GoAwayFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Http2GoAwayFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public Http2GoAwayFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttp2GoAwayFrame)) {
            return false;
        }
        DefaultHttp2GoAwayFrame other = (DefaultHttp2GoAwayFrame) o;
        return errorCode == other.errorCode && extraStreamIds == other.extraStreamIds && super.equals(other);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + (int) (errorCode ^ (errorCode >>> 32));
        hash = hash * 31 + extraStreamIds;
        return hash;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(errorCode=" + errorCode + ", content=" + content()
               + ", extraStreamIds=" + extraStreamIds + ", lastStreamId=" + lastStreamId + ')';
    }
}

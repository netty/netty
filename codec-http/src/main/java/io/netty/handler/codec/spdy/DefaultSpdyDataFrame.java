/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyDataFrame} implementation.
 */
public class DefaultSpdyDataFrame extends DefaultSpdyStreamFrame implements SpdyDataFrame {

    private final ByteBuf data;

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public DefaultSpdyDataFrame(int streamId) {
        this(streamId, Unpooled.buffer(0));
    }

    /**
     * Creates a new instance.
     *
     * @param streamId  the Stream-ID of this frame
     * @param data      the payload of the frame. Can not exceed {@link SpdyCodecUtil#SPDY_MAX_LENGTH}
     */
    public DefaultSpdyDataFrame(int streamId, ByteBuf data) {
        super(streamId);
        this.data = validate(
                ObjectUtil.checkNotNull(data, "data"));
    }

    private static ByteBuf validate(ByteBuf data) {
        if (data.readableBytes() > SpdyCodecUtil.SPDY_MAX_LENGTH) {
            throw new IllegalArgumentException("data payload cannot exceed "
                    + SpdyCodecUtil.SPDY_MAX_LENGTH + " bytes");
        }
        return data;
    }

    @Override
    public SpdyDataFrame setStreamId(int streamId) {
        super.setStreamId(streamId);
        return this;
    }

    @Override
    public SpdyDataFrame setLast(boolean last) {
        super.setLast(last);
        return this;
    }

    @Override
    public ByteBuf content() {
        if (data.refCnt() <= 0) {
            throw new IllegalReferenceCountException(data.refCnt());
        }
        return data;
    }

    @Override
    public SpdyDataFrame copy() {
        return replace(content().copy());
    }

    @Override
    public SpdyDataFrame duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public SpdyDataFrame retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public SpdyDataFrame replace(ByteBuf content) {
        SpdyDataFrame frame = new DefaultSpdyDataFrame(streamId(), content);
        frame.setLast(isLast());
        return frame;
    }

    @Override
    public int refCnt() {
        return data.refCnt();
    }

    @Override
    public SpdyDataFrame retain() {
        data.retain();
        return this;
    }

    @Override
    public SpdyDataFrame retain(int increment) {
        data.retain(increment);
        return this;
    }

    @Override
    public SpdyDataFrame touch() {
        data.touch();
        return this;
    }

    @Override
    public SpdyDataFrame touch(Object hint) {
        data.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return data.release();
    }

    @Override
    public boolean release(int decrement) {
        return data.release(decrement);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append("(last: ")
            .append(isLast())
            .append(')')
            .append(StringUtil.NEWLINE)
            .append("--> Stream-ID = ")
            .append(streamId())
            .append(StringUtil.NEWLINE)
            .append("--> Size = ");
        if (refCnt() == 0) {
            buf.append("(freed)");
        } else {
            buf.append(content().readableBytes());
        }
        return buf.toString();
    }
}

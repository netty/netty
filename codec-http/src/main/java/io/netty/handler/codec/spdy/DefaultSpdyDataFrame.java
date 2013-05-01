/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyDataFrame} implementation.
 */
public class DefaultSpdyDataFrame extends DefaultByteBufHolder implements SpdyDataFrame {

    private int streamId;
    private boolean last;

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
        super(validate(data));
        setStreamId(streamId);
    }

    private static ByteBuf validate(ByteBuf data) {
        if (data.readableBytes() > SpdyCodecUtil.SPDY_MAX_LENGTH) {
            throw new IllegalArgumentException("data payload cannot exceed "
                    + SpdyCodecUtil.SPDY_MAX_LENGTH + " bytes");
        }
        return data;
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public SpdyDataFrame setStreamId(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        this.streamId = streamId;
        return this;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public SpdyDataFrame setLast(boolean last) {
        this.last = last;
        return this;
    }

    @Override
    public DefaultSpdyDataFrame copy() {
        DefaultSpdyDataFrame frame = new DefaultSpdyDataFrame(getStreamId(), content().copy());
        frame.setLast(isLast());
        return frame;
    }

    @Override
    public SpdyDataFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public SpdyDataFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(last: ");
        buf.append(isLast());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(streamId);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Size = ");
        if (refCnt() == 0) {
            buf.append("(freed)");
        } else {
            buf.append(content().readableBytes());
        }
        return buf.toString();
    }
}

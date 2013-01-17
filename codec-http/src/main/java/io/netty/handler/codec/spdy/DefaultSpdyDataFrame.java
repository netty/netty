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

    private final int streamId;
    private final boolean last;

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public DefaultSpdyDataFrame(int streamId) {
        this(streamId, false, Unpooled.buffer(0));
    }

    /**
     * Creates a new instance.
     *
     * @param streamId  the Stream-ID of this frame
     * @param last      if this is the last frame in the stream
     * @param data      the payload of the frame. Can not exceed {@link SpdyCodecUtil#SPDY_MAX_LENGTH}
     */
    public DefaultSpdyDataFrame(int streamId, boolean last, ByteBuf data) {
        super(validate(data));
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        this.streamId = streamId;
        this.last = last;
    }

    private static ByteBuf validate(ByteBuf data) {
        if (data.readableBytes() > SpdyCodecUtil.SPDY_MAX_LENGTH) {
            throw new IllegalArgumentException("data payload cannot exceed "
                    + SpdyCodecUtil.SPDY_MAX_LENGTH + " bytes");
        }
        return data;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public DefaultSpdyDataFrame copy() {
        return new DefaultSpdyDataFrame(streamId(), isLast(), data().copy());
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
        if (isFreed()) {
            buf.append("(freed)");
        } else {
            buf.append(data().readableBytes());
        }
        return buf.toString();
    }
}

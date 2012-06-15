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
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyDataFrame} implementation.
 */
public class DefaultSpdyDataFrame implements SpdyDataFrame {

    private int streamId;
    private boolean last;
    private boolean compressed;
    private ByteBuf data = Unpooled.EMPTY_BUFFER;

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    public DefaultSpdyDataFrame(int streamId) {
        setStreamId(streamId);
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public void setStreamId(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        this.streamId = streamId;
    }

    @Override
    public boolean isLast() {
        return last;
    }

    @Override
    public void setLast(boolean last) {
        this.last = last;
    }

    @Override
    @Deprecated
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    @Deprecated
    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    @Override
    public ByteBuf getData() {
        return data;
    }

    @Override
    public void setData(ByteBuf data) {
        if (data == null) {
            data = Unpooled.EMPTY_BUFFER;
        }
        if (data.readableBytes() > SpdyCodecUtil.SPDY_MAX_LENGTH) {
            throw new IllegalArgumentException("data payload cannot exceed "
                    + SpdyCodecUtil.SPDY_MAX_LENGTH + " bytes");
        }
        this.data = data;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(last: ");
        buf.append(isLast());
        buf.append("; compressed: ");
        buf.append(isCompressed());
        buf.append(')');
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(streamId);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Size = ");
        buf.append(data.readableBytes());
        return buf.toString();
    }
}

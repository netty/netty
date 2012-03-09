/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyDataFrame} implementation.
 */
public class DefaultSpdyDataFrame implements SpdyDataFrame {

    private int streamID;
    private boolean last;
    private boolean compressed;
    private ChannelBuffer data = ChannelBuffers.EMPTY_BUFFER;

    /**
     * Creates a new instance.
     *
     * @param streamID the Stream-ID of this frame
     */
    public DefaultSpdyDataFrame(int streamID) {
        setStreamID(streamID);
    }

    public int getStreamID() {
        return streamID;
    }

    public void setStreamID(int streamID) {
        if (streamID <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamID);
        }
        this.streamID = streamID;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public ChannelBuffer getData() {
        return data;
    }

    public void setData(ChannelBuffer data) {
        if (data == null) {
            data = ChannelBuffers.EMPTY_BUFFER;
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
        buf.append(streamID);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Size = ");
        buf.append(data.readableBytes());
        return buf.toString();
    }
}

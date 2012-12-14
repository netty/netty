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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyWindowUpdateFrame} implementation.
 */
public class DefaultSpdyWindowUpdateFrame implements SpdyWindowUpdateFrame {

    private int streamId;
    private int deltaWindowSize;

    /**
     * Creates a new instance.
     *
     * @param streamId        the Stream-ID of this frame
     * @param deltaWindowSize the Delta-Window-Size of this frame
     */
    public DefaultSpdyWindowUpdateFrame(int streamId, int deltaWindowSize) {
        setStreamId(streamId);
        setDeltaWindowSize(deltaWindowSize);
    }

    @Deprecated
    public int getStreamID() {
        return getStreamId();
    }

    public int getStreamId() {
        return streamId;
    }

    @Deprecated
    public void setStreamID(int streamId) {
        setStreamId(streamId);
    }

    public void setStreamId(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException(
                    "Stream-ID must be positive: " + streamId);
        }
        this.streamId = streamId;
    }

    public int getDeltaWindowSize() {
        return deltaWindowSize;
    }

    public void setDeltaWindowSize(int deltaWindowSize) {
        if (deltaWindowSize <= 0) {
            throw new IllegalArgumentException(
                    "Delta-Window-Size must be positive: " +
                    deltaWindowSize);
        }
        this.deltaWindowSize = deltaWindowSize;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Stream-ID = ");
        buf.append(streamId);
        buf.append(StringUtil.NEWLINE);
        buf.append("--> Delta-Window-Size = ");
        buf.append(deltaWindowSize);
        return buf.toString();
    }
}

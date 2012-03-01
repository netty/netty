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

/**
 * A SPDY Protocol Data Frame
 */
public interface SpdyDataFrame {

    /**
     * Returns the Stream-ID of this frame.
     */
    int getStreamID();

    /**
     * Sets the Stream-ID of this frame.  The Stream-ID must be positive.
     */
    void setStreamID(int streamID);

    /**
     * Returns {@code true} if this frame is the last frame to be transmitted
     * on the stream.
     */
    boolean isLast();

    /**
     * Sets if this frame is the last frame to be transmitted on the stream.
     */
    void setLast(boolean last);

    /**
     * Returns {@code true} if the data in this frame has been compressed.
     */
    boolean isCompressed();

    /**
     * Sets if the data in this frame has been compressed.
     */
    void setCompressed(boolean compressed);

    /**
     * Returns the data payload of this frame.  If there is no data payload
     * {@link ChannelBuffers#EMPTY_BUFFER} is returned.
     */
    ChannelBuffer getData();

    /**
     * Sets the data payload of this frame.  If {@code null} is specified,
     * the data payload will be set to {@link ChannelBuffers#EMPTY_BUFFER}.
     * The data payload cannot exceed 16777215 bytes.
     */
    void setData(ChannelBuffer data);
}

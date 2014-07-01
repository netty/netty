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

/**
 * A SPDY Protocol Frame that is associated with an individual SPDY Stream
 */
public interface SpdyStreamFrame extends SpdyFrame {

    /**
     * Returns the Stream-ID of this frame.
     */
    int streamId();

    /**
     * Sets the Stream-ID of this frame.  The Stream-ID must be positive.
     */
    SpdyStreamFrame setStreamId(int streamID);

    /**
     * Returns {@code true} if this frame is the last frame to be transmitted
     * on the stream.
     */
    boolean isLast();

    /**
     * Sets if this frame is the last frame to be transmitted on the stream.
     */
    SpdyStreamFrame setLast(boolean last);
}

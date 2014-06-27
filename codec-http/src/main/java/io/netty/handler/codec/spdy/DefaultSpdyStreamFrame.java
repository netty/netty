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
 * The default {@link SpdyStreamFrame} implementation.
 */
public abstract class DefaultSpdyStreamFrame implements SpdyStreamFrame {

    private int streamId;
    private boolean last;

    /**
     * Creates a new instance.
     *
     * @param streamId the Stream-ID of this frame
     */
    protected DefaultSpdyStreamFrame(int streamId) {
        setStreamId(streamId);
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public SpdyStreamFrame setStreamId(int streamId) {
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
    public SpdyStreamFrame setLast(boolean last) {
        this.last = last;
        return this;
    }
}

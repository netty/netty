/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.util.internal.StringUtil;

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

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public SpdyWindowUpdateFrame setStreamId(int streamId) {
        checkPositiveOrZero(streamId, "streamId");
        this.streamId = streamId;
        return this;
    }

    @Override
    public int deltaWindowSize() {
        return deltaWindowSize;
    }

    @Override
    public SpdyWindowUpdateFrame setDeltaWindowSize(int deltaWindowSize) {
        checkPositive(deltaWindowSize, "deltaWindowSize");
        this.deltaWindowSize = deltaWindowSize;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append(StringUtil.NEWLINE)
            .append("--> Stream-ID = ")
            .append(streamId())
            .append(StringUtil.NEWLINE)
            .append("--> Delta-Window-Size = ")
            .append(deltaWindowSize())
            .toString();
    }
}

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

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.util.internal.StringUtil;

/**
 * The default {@link SpdyGoAwayFrame} implementation.
 */
public class DefaultSpdyGoAwayFrame implements SpdyGoAwayFrame {

    private int lastGoodStreamId;
    private SpdySessionStatus status;

    /**
     * Creates a new instance.
     *
     * @param lastGoodStreamId the Last-good-stream-ID of this frame
     */
    public DefaultSpdyGoAwayFrame(int lastGoodStreamId) {
        this(lastGoodStreamId, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param lastGoodStreamId the Last-good-stream-ID of this frame
     * @param statusCode       the Status code of this frame
     */
    public DefaultSpdyGoAwayFrame(int lastGoodStreamId, int statusCode) {
        this(lastGoodStreamId, SpdySessionStatus.valueOf(statusCode));
    }

    /**
     * Creates a new instance.
     *
     * @param lastGoodStreamId the Last-good-stream-ID of this frame
     * @param status           the status of this frame
     */
    public DefaultSpdyGoAwayFrame(int lastGoodStreamId, SpdySessionStatus status) {
        setLastGoodStreamId(lastGoodStreamId);
        setStatus(status);
    }

    @Override
    public int lastGoodStreamId() {
        return lastGoodStreamId;
    }

    @Override
    public SpdyGoAwayFrame setLastGoodStreamId(int lastGoodStreamId) {
        checkPositiveOrZero(lastGoodStreamId, "lastGoodStreamId");
        this.lastGoodStreamId = lastGoodStreamId;
        return this;
    }

    @Override
    public SpdySessionStatus status() {
        return status;
    }

    @Override
    public SpdyGoAwayFrame setStatus(SpdySessionStatus status) {
        this.status = status;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append(StringUtil.NEWLINE)
            .append("--> Last-good-stream-ID = ")
            .append(lastGoodStreamId())
            .append(StringUtil.NEWLINE)
            .append("--> Status: ")
            .append(status())
            .toString();
    }
}

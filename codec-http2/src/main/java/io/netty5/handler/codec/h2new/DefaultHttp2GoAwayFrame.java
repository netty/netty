/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.handler.codec.h2new;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferHolder;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2GoAwayFrame}.
 */
public final class DefaultHttp2GoAwayFrame extends BufferHolder<DefaultHttp2GoAwayFrame> implements Http2GoAwayFrame {
    private final int lastStreamId;
    private final long errorCode;

    /**
     * Creates a new instance.
     * @param lastStreamId last stream ID specified in this frame.
     * @param debugData {@link Buffer} containing data for this frame.
     */
    public DefaultHttp2GoAwayFrame(int lastStreamId, long errorCode, Buffer debugData) {
        super(debugData);
        this.lastStreamId = checkPositiveOrZero(lastStreamId, "lastStreamId");
        this.errorCode = errorCode;
    }

    @Override
    public Type frameType() {
        return Type.GoAway;
    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public int lastStreamId() {
        return lastStreamId;
    }

    @Override
    public Buffer debugData() {
        return super.getBuffer();
    }

    @Override
    public long errorCode() {
        return errorCode;
    }

    @Override
    protected DefaultHttp2GoAwayFrame receive(Buffer buf) {
        return new DefaultHttp2GoAwayFrame(lastStreamId, errorCode, buf);
    }
}

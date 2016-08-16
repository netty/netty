/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Abstract implementation of {@link Http2StreamFrame}.
 */
@UnstableApi
public abstract class AbstractHttp2StreamFrame implements Http2StreamFrame {

    // Volatile as parent and child channel may be on different eventloops.
    private volatile int streamId = -1;

    @Override
    public AbstractHttp2StreamFrame streamId(int streamId) {
        if (this.streamId != -1) {
            throw new IllegalStateException("Stream identifier may only be set once.");
        }
        this.streamId = ObjectUtil.checkPositiveOrZero(streamId, "streamId");
        return this;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    /**
     * Returns {@code true} if {@code o} has equal {@code stream} to this object.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Http2StreamFrame)) {
            return false;
        }
        Http2StreamFrame other = (Http2StreamFrame) o;
        return streamId == other.streamId();
    }

    @Override
    public int hashCode() {
        return streamId;
    }
}

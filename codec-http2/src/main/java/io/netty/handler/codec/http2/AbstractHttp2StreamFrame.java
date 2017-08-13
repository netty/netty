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

import io.netty.util.internal.UnstableApi;

/**
 * Abstract implementation of {@link Http2StreamFrame}.
 */
@UnstableApi
public abstract class AbstractHttp2StreamFrame implements Http2StreamFrame {

    private Http2FrameStream stream;

    @Override
    public AbstractHttp2StreamFrame stream(Http2FrameStream stream) {
        this.stream = stream;
        return this;
    }

    @Override
    public Http2FrameStream stream() {
        return stream;
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
        return stream == other.stream() || (stream != null && stream.equals(other.stream()));
    }

    @Override
    public int hashCode() {
        Http2FrameStream stream = this.stream;
        if (stream == null) {
            return super.hashCode();
        }
        return stream.hashCode();
    }
}

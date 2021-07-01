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

import io.netty5.handler.codec.http2.Http2Headers;

import static io.netty5.util.internal.ObjectUtil.checkInRange;
import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;
import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2PushPromiseFrame}.
 */
public final class DefaultHttp2PushPromiseFrame implements Http2PushPromiseFrame {
    private final Http2Headers headers;
    private final int streamId;
    private final int promisedStreamId;
    private final int padding;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param promisedStreamId stream ID of the promised stream. Must be positive.
     */
    public DefaultHttp2PushPromiseFrame(int streamId, Http2Headers headers, int promisedStreamId) {
        this.headers = checkNotNullWithIAE(headers, "headers");
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.promisedStreamId = checkPositive(promisedStreamId, "promisedStreamId");
        this.padding = -1;
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param promisedStreamId stream ID of the promised stream. Must be positive.
     * @param padding for the frame. Must be non-negative and less than 256.
     */
    public DefaultHttp2PushPromiseFrame(int streamId, Http2Headers headers, int promisedStreamId, int padding) {
        this.headers = checkNotNullWithIAE(headers, "headers");
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.promisedStreamId = checkPositive(promisedStreamId, "promisedStreamId");
        this.padding = checkInRange(padding, 0, 255, "padding");
    }

    @Override
    public Type frameType() {
        return Type.Headers;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public Http2Headers headers() {
        return headers;
    }

    @Override
    public boolean isPadded() {
        return padding >= 0;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public int promisedStreamId() {
        return promisedStreamId;
    }
}

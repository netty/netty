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

package io.netty.handler.codec.h2new;

import io.netty.handler.codec.http2.Http2Headers;

import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2HeadersFrame}.
 */
public final class DefaultHttp2HeadersFrame implements Http2HeadersFrame {
    private final Http2Headers headers;
    private final int streamId;
    private final boolean endStream;
    private final int padding;
    private final int streamDependency;
    private final boolean isExclusive;
    private final short weight;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     */
    public DefaultHttp2HeadersFrame(int streamId, Http2Headers headers) {
        this(streamId, headers, false);
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     */
    public DefaultHttp2HeadersFrame(int streamId, Http2Headers headers, boolean endStream) {
        this.headers = headers;
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.endStream = endStream;
        padding = -1;
        streamDependency = -1;
        isExclusive = false;
        weight = -1;
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     * @param streamDependency positive number indicating the stream dependency.
     * @param isExclusive {@code true} if the stream dependency is exclusive.
     * @param weight number between 1 and 256 (both inclusive) indicating the stream weight.
     */
    public DefaultHttp2HeadersFrame(int streamId, Http2Headers headers, boolean endStream,
                                    int streamDependency, boolean isExclusive, short weight) {
        this.headers = headers;
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.endStream = endStream;
        padding = -1;
        this.streamDependency = checkPositive(streamDependency, "streamDependency");
        this.isExclusive = isExclusive;
        this.weight = checkInRange(weight, (short) 1, (short) 256, "weight");
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     * @param padding for the frame. Must be non-negative and less than 256.
     */
    public DefaultHttp2HeadersFrame(int streamId, Http2Headers headers, boolean endStream, int padding) {
        this.headers = headers;
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.endStream = endStream;
        this.padding = checkInRange(padding, 0, 255, "padding");
        streamDependency = -1;
        isExclusive = false;
        weight = -1;
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param headers {@link Http2Headers} for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     * @param streamDependency positive number indicating the stream dependency.
     * @param isExclusive {@code true} if the stream dependency is exclusive.
     * @param weight number between 1 and 256 (both inclusive) indicating the stream weight.
     * @param padding for the frame.
     */
    public DefaultHttp2HeadersFrame(int streamId, Http2Headers headers, boolean endStream,
                                    int streamDependency, boolean isExclusive, short weight, int padding) {
        this.headers = headers;
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.endStream = endStream;
        this.padding = checkPositive(padding, "padding");
        this.streamDependency = checkPositive(streamDependency, "streamDependency");
        this.isExclusive = isExclusive;
        this.weight = checkInRange(weight, (short) 1, (short) 256, "weight");
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
    public boolean isPrioritySet() {
        return streamDependency > 0;
    }

    @Override
    public boolean isExclusiveDependency() {
        return isExclusive;
    }

    @Override
    public int streamDependency() {
        return streamDependency;
    }

    @Override
    public short weight() {
        return weight;
    }

    @Override
    public boolean isEndStream() {
        return endStream;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public String toString() {
        return "DefaultHttp2HeadersFrame{" +
                "headers=" + headers +
                ", streamId=" + streamId +
                ", endStream=" + endStream +
                ", padding=" + padding +
                ", streamDependency=" + streamDependency +
                ", isExclusive=" + isExclusive +
                ", weight=" + weight +
                '}';
    }
}

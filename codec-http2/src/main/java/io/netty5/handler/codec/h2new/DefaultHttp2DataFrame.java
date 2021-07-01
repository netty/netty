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

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferHolder;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Default implementation of {@link Http2DataFrame}.
 */
public final class DefaultHttp2DataFrame extends BufferHolder<DefaultHttp2DataFrame> implements Http2DataFrame {
    private final Buffer data;
    private final int initialFlowControlledBytes;
    private final boolean endStream;
    private final int streamId;
    private final int padding;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param data {@link Buffer} containing data for this frame.
     */
    public DefaultHttp2DataFrame(int streamId, Buffer data) {
        this(streamId, data, false);
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param data {@link Buffer} containing data for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     */
    public DefaultHttp2DataFrame(int streamId, Buffer data, boolean endStream) {
        super(data);
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.data = data;
        initialFlowControlledBytes = data.readableBytes();
        this.endStream = endStream;
        padding = -1;
    }

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param data {@link Buffer} containing data for this frame.
     * @param endStream {@code true} if {@code END_STREAM} flag is set for this frame.
     * @param padding for the frame. Must be non-negative and less than 256.
     */
    public DefaultHttp2DataFrame(int streamId, Buffer data, boolean endStream, int padding) {
        super(data);
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.data = data;
        initialFlowControlledBytes = data.readableBytes();
        this.endStream = endStream;
        this.padding = checkPositive(padding, "padding");
    }

    @Override
    public Type frameType() {
        return Type.Data;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    protected DefaultHttp2DataFrame receive(Buffer buf) {
        return new DefaultHttp2DataFrame(streamId, buf);
    }

    @Override
    public Buffer data() {
        return data;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public int initialFlowControlledBytes() {
        return initialFlowControlledBytes;
    }

    @Override
    public boolean isEndStream() {
        return endStream;
    }

    @Override
    public String toString() {
        return "DefaultHttp2DataFrame{" +
                ", initialFlowControlledBytes=" + initialFlowControlledBytes +
                ", endStream=" + endStream +
                ", streamId=" + streamId +
                ", padding=" + padding +
                '}';
    }
}

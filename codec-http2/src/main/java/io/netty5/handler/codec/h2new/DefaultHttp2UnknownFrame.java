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
 * Default implementation of {@link Http2UnknownFrame}.
 */
public final class DefaultHttp2UnknownFrame extends BufferHolder<DefaultHttp2UnknownFrame>
        implements Http2UnknownFrame {
    private final int streamId;
    private final byte type;
    private final short flags;

    /**
     * Creates a new instance.
     *
     * @param streamId the identifier for the stream on which this frame was sent/received.
     * @param type the type of the frame as a {@code byte}.
     * @param flags for the frame.
     * @param payload for the frame.
     */
    public DefaultHttp2UnknownFrame(int streamId, byte type, short flags, Buffer payload) {
        super(payload);
        this.streamId = checkPositiveOrZero(streamId, "streamId");
        this.type = type;
        this.flags = flags;
    }

    @Override
    public Type frameType() {
        return Type.Unknown;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public byte unknownFrameType() {
        return type;
    }

    @Override
    public short flags() {
        return flags;
    }

    @Override
    public Buffer payload() {
        return super.getBuffer();
    }

    @Override
    protected DefaultHttp2UnknownFrame receive(Buffer buf) {
        return new DefaultHttp2UnknownFrame(streamId, type, flags, buf);
    }
}

/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.frame.encoder;

import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

/**
 * Abstract base class for all {@link Http2FrameMarshaller}s.
 */
public abstract class AbstractHttp2FrameMarshaller<T extends Http2Frame> implements
        Http2FrameMarshaller {

    private final Class<T> frameType;

    protected AbstractHttp2FrameMarshaller(Class<T> frameType) {
        if (frameType == null) {
            throw new IllegalArgumentException("frameType must be non-null.");
        }
        this.frameType = frameType;
    }

    @Override
    public final void marshall(Http2Frame frame, ByteBuf out, ByteBufAllocator alloc)
            throws Http2Exception {
        if (frame == null) {
            throw new IllegalArgumentException("frame must be non-null.");
        }

        if (!frameType.isAssignableFrom(frame.getClass())) {
            throw protocolError("Unsupported frame type: %s", frame.getClass().getName());
        }

        @SuppressWarnings("unchecked")
        T frameT = (T) frame;
        doMarshall(frameT, out, alloc);
    }

    /**
     * Marshals the frame to the output buffer.
     *
     * @param frame the frame to be marshalled
     * @param out   the buffer to marshall the frame to.
     * @param alloc an allocator that this marshaller may use for creating intermediate buffers as
     *              needed.
     */
    protected abstract void doMarshall(T frame, ByteBuf out, ByteBufAllocator alloc)
            throws Http2Exception;
}

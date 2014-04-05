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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

/**
 * Marshalls {@link Http2Frame} objects to a {@link ByteBuf}.
 */
public interface Http2FrameMarshaller {

    /**
     * Marshalls the given frame to the output buffer.
     *
     * @param frame the frame to be marshalled.
     * @param out   the buffer to marshall the frame to.
     * @param alloc an allocator that this marshaller may use for creating intermediate buffers as
     *              needed.
     * @throws Http2Exception thrown if the given fram is not supported by this marshaller.
     */
    void marshall(Http2Frame frame, ByteBuf out, ByteBufAllocator alloc) throws Http2Exception;
}

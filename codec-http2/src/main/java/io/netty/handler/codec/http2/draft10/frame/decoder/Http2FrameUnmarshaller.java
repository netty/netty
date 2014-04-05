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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * Used by the {@link Http2FrameDecoder} to unmarshall {@link Http2Frame} objects from an input
 * {@link ByteBuf}.
 */
public interface Http2FrameUnmarshaller {

    /**
     * Prepares the unmarshaller for the next frame.
     *
     * @param header the header providing the detais of the frame to be unmarshalled.
     * @return this unmarshaller
     * @throws Http2Exception thrown if any of the information of the header violates the protocol.
     */
    Http2FrameUnmarshaller unmarshall(Http2FrameHeader header) throws Http2Exception;

    /**
     * Unmarshalls the frame from the payload.
     *
     * @param payload the payload from which the frame is to be unmarshalled.
     * @param alloc   the allocator for any new buffers required by the unmarshaller.
     * @return the frame or {@code null} if the unmarshall operation is processing is incomplete and
     * requires additional data.
     * @throws Http2Exception thrown if any protocol error was encountered while unmarshalling the
     *                        frame.
     */
    Http2Frame from(ByteBuf payload, ByteBufAllocator alloc) throws Http2Exception;
}

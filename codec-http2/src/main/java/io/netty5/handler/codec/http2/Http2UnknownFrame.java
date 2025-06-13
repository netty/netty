/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.util.Resource;
import io.netty5.util.internal.UnstableApi;

@UnstableApi
public interface Http2UnknownFrame extends Http2StreamFrame, Resource<Http2UnknownFrame> {

    /**
     * Get the data content associated with this unknown frame.
     * <p>
     * The buffer will be empty if there is no data with this unknown frame.
     *
     * @return The contents of this unknown frame.
     */
    Buffer content();

    @Override
    Http2FrameStream stream();

    @Override
    Http2UnknownFrame stream(Http2FrameStream stream);

    /**
     * Get the raw frame type.
     *
     * This is the type value that wasn't recognized and caused this to be captured as an unknown frame.
     *
     * @return The raw frame type.
     */
    short frameType();

    /**
     * Get the {@link Http2Flags} set on this unknown frame.
     *
     * @return The flags set on this frame.
     */
    Http2Flags flags();

    /**
     * Create a copy of this unknown frame, which in turn contain a copy of the frame {@linkplain #content() contents}.
     *
     * @return A copy of this unknown frame.
     */
    Http2UnknownFrame copy();
}

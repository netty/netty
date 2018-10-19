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
 * A frame whose meaning <em>may</em> apply to a particular stream, instead of the entire connection. It is still
 * possible for this frame type to apply to the entire connection. In such cases, the {@link #stream()} must return
 * {@code null}. If the frame applies to a stream, the {@link Http2FrameStream#id()} must be greater than zero.
 */
@UnstableApi
public interface Http2StreamFrame extends Http2Frame {

    /**
     * Set the {@link Http2FrameStream} object for this frame.
     */
    Http2StreamFrame stream(Http2FrameStream stream);

    /**
     * Returns the {@link Http2FrameStream} object for this frame, or {@code null} if the frame has yet to be associated
     * with a stream.
     */
    Http2FrameStream stream();
}

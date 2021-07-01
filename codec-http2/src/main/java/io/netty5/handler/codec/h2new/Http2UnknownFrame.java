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

/**
 * An unknown <a href="https://httpwg.org/specs/rfc7540.html#FrameHeader">HTTP/2 frame</a>.
 */
public interface Http2UnknownFrame extends Http2ControlStreamFrame, Http2RequestStreamFrame {

    /**
     * Returns the type of the frame as a {@code byte}.
     *
     * @return The type of the frame as a {@code byte}.
     */
    byte unknownFrameType();

    /**
     * Returns the flags for this frame.
     *
     * @return The flags for this frame.
     */
    short flags();

    /**
     * Returns the {@link Buffer} containing payload for this frame.
     *
     * @return The {@link Buffer} containing payload for this frame.
     */
    Buffer payload();
}

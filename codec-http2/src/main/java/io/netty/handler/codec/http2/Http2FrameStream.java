/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.handler.codec.http2.Http2Stream.State;

/**
 * A single stream within an HTTP/2 connection. To be used with the {@link Http2FrameCodec}.
 */
public interface Http2FrameStream {
    /**
     * Returns the stream identifier.
     *
     * <p>Use {@link Http2CodecUtil#isStreamIdValid(int)} to check if the stream has already been assigned an
     * identifier.
     */
    int id();

    /**
     * Returns the state of this stream.
     */
    State state();
}

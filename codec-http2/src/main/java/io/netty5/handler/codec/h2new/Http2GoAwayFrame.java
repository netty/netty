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

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#GOAWAY">HTTP/2 go away frame</a>.
 */
public interface Http2GoAwayFrame extends Http2ControlStreamFrame {

    /**
     * Returns the last stream ID specified in this frame.
     *
     * @return The last stream ID specified in this frame.
     */
    int lastStreamId();

    /**
     * Returns the debug data specified in this frame.
     *
     * @return The debug data specified in this frame.
     */
    Buffer debugData();

    /**
     * Returns the reason for go away, represented as an HTTP/2 error code.
     *
     * @return The reason for go away, represented as an HTTP/2 error code.
     */
    long errorCode();
}

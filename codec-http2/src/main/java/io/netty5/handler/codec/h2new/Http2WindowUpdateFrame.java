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

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#WINDOW_UPDATE">HTTP/2 window update frame</a>.
 */
public interface Http2WindowUpdateFrame extends Http2RequestStreamFrame, Http2ControlStreamFrame {
    /**
     * Returns the number of bytes to increment the flow control window.
     *
     * @return The number of bytes to increment the flow control window.
     */
    int windowSizeIncrement();
}

/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http2;

public interface Http2FrameSizePolicy {
    /**
     * Sets the maximum allowed frame size. Attempts to write frames longer than this maximum will fail.
     * <p>
     * This value is used to represent
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>. This method should
     * only be called by Netty (not users) as a result of a receiving a {@code SETTINGS} frame.
     */
    void maxFrameSize(int max) throws Http2Exception;

    /**
     * Gets the maximum allowed frame size.
     * <p>
     * This value is used to represent
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_FRAME_SIZE</a>. The initial value
     * defined by the RFC is unlimited but enforcing a lower limit is generally permitted.
     * {@link Http2CodecUtil#DEFAULT_MAX_FRAME_SIZE} can be used as a more conservative default.
     */
    int maxFrameSize();
}

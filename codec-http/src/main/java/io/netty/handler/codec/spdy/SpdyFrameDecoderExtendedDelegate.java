/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;

/**
 * {@link SpdyFrameDecoderDelegate} that also supports unknown frames.
 */
interface SpdyFrameDecoderExtendedDelegate extends SpdyFrameDecoderDelegate {

    /**
     * Called when an unknown frame is received.
     *
     * @param frameType the frame type from the spdy header.
     * @param flags the flags in the frame header.
     * @param payload the payload of the frame.
     */
    void readUnknownFrame(int frameType, byte flags, ByteBuf payload);
}

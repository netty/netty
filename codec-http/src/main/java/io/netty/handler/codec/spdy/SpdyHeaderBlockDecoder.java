/*
 * Copyright 2014 The Netty Project
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
import io.netty.buffer.ByteBufAllocator;

abstract class SpdyHeaderBlockDecoder {

    static SpdyHeaderBlockDecoder newInstance(SpdyVersion spdyVersion, int maxHeaderSize) {
        return new SpdyHeaderBlockZlibDecoder(spdyVersion, maxHeaderSize);
    }

    /**
     * Decodes a SPDY Header Block, adding the Name/Value pairs to the given Headers frame.
     * If the header block is malformed, the Headers frame will be marked as invalid.
     * A stream error with status code PROTOCOL_ERROR must be issued in response to an invalid frame.
     *
     * @param alloc the {@link ByteBufAllocator} which can be used to allocate new {@link ByteBuf}s
     * @param headerBlock the HeaderBlock to decode
     * @param frame the Headers frame that receives the Name/Value pairs
     * @throws Exception If the header block is malformed in a way that prevents any future
     *                   decoding of any other header blocks, an exception will be thrown.
     *                   A session error with status code PROTOCOL_ERROR must be issued.
     */
    abstract void decode(ByteBufAllocator alloc, ByteBuf headerBlock, SpdyHeadersFrame frame) throws Exception;

    abstract void endHeaderBlock(SpdyHeadersFrame frame) throws Exception;

    abstract void end();
}

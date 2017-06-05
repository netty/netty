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

package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * A decoder that mostly mimics {@link FixedLengthFrameDecoder} semantics. The only difference is this decoder batches
 * incoming {@link ByteBuf} messages and then decodes them at once, passing a big {@link ByteBuf} which can possibly
 * contain N messages (where N >= 1) through the pipeline at once.
 */
public class BulkFixedLengthFrameDecoder extends BulkFrameDecoder {

    final int frameLength;

    /**
     * Creates a new instance.
     *
     * @param frameLength the length of the frame
     */
    public BulkFixedLengthFrameDecoder(int frameLength) {
        this(0, frameLength);
    }

    /**
     * Creates a new instance.
     *
     * @param startBufferSize the start buffer size which is used for batching incoming {@link ByteBuf} messages. Size
     * of this buffer must be greater or equal to zero. If the size of this buffer is zero then the default size will be
     * used (= 256 bytes).
     * @param frameLength the length of the frame
     */
    public BulkFixedLengthFrameDecoder(int startBufferSize, int frameLength) {
        super(startBufferSize);

        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength must be a positive integer: " + frameLength);
        }

        this.frameLength = frameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        final int readableBytes = buffer.readableBytes();

        if (readableBytes < frameLength) {
            return;
        }

        final int chunkLength = buffer.readableBytes() / frameLength * frameLength;
        final ByteBuf chunk = ctx.alloc().buffer(chunkLength, chunkLength).writeBytes(buffer, chunkLength);

        out.add(chunk);
    }
}

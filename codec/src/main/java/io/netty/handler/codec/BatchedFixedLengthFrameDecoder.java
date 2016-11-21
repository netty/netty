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
import io.netty.util.internal.ObjectUtil;

import java.util.List;

/**
 * A decoder that mostly mimics {@link FixedLengthFrameDecoder} semantics. The only difference is this decoder batches
 * incoming {@link ByteBuf} messages and then decodes them at once, passing a big {@link ByteBuf} which can possibly
 * contain N messages (where N >= 1) through the pipeline at once.
 */
public class BatchedFixedLengthFrameDecoder extends BatchedByteToMessageDecoder {

    final int frameLength;

    /**
     * Creates a new instance.
     *
     * @param frameLength the length of the frame
     */
    public BatchedFixedLengthFrameDecoder(int frameLength) {
        this.frameLength = ObjectUtil.checkPositive(frameLength, "frameLength");
    }

    @Override
    protected void decodeBatched(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        final int readableBytes = in.readableBytes();

        if (readableBytes < frameLength) {
            return;
        }

        final int numberOfMessages = readableBytes / frameLength;
        final int bytesToCopy = numberOfMessages * frameLength;
        final ByteBuf chunk = in.readRetainedSlice(bytesToCopy);

        out.add(chunk);
    }
}

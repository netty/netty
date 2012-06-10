/*
 * Copyright 2012 The Netty Project
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
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelHandlerContext;

/**
 * A decoder that splits the received {@link ByteBuf}s by the fixed number
 * of bytes. For example, if you received the following four fragmented packets:
 * <pre>
 * +---+----+------+----+
 * | A | BC | DEFG | HI |
 * +---+----+------+----+
 * </pre>
 * A {@link FixedLengthFrameDecoder}{@code (3)} will decode them into the
 * following three packets with the fixed length:
 * <pre>
 * +-----+-----+-----+
 * | ABC | DEF | GHI |
 * +-----+-----+-----+
 * </pre>
 */
public class FixedLengthFrameDecoder extends ByteToMessageDecoder<Object> {

    private final int frameLength;
    private final boolean allocateFullBuffer;

    /**
     * Calls {@link #FixedLengthFrameDecoder(int, boolean)} with <code>false</code>
     */
    public FixedLengthFrameDecoder(int frameLength) {
        this(frameLength, false);
    }

    /**
     * Creates a new instance.
     *
     * @param frameLength
     *        the length of the frame
     * @param allocateFullBuffer
     *        <code>true</code> if the cumulative {@link ByteBuf} should use the
     *        {@link #frameLength} as its initial size
     */
    public FixedLengthFrameDecoder(int frameLength, boolean allocateFullBuffer) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                    "frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
        this.allocateFullBuffer = allocateFullBuffer;
    }

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        if (allocateFullBuffer) {
            return ChannelBufferHolders.byteBuffer(ChannelBuffers.dynamicBuffer(frameLength));
        } else {
            return super.newInboundBuffer(ctx);
        }
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (in.readableBytes() < frameLength) {
            return null;
        } else {
            return in.readBytes(frameLength);
        }
    }
}

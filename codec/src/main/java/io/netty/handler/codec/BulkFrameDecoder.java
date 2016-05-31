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
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.List;

public abstract class BulkFrameDecoder extends ChannelInboundHandlerAdapter {

    private final int startBufferSize;

    private ByteBuf buffer;

    /**
     * @param startBufferSize the start buffer size which is used for batching incoming {@link ByteBuf} messages. Size
     * of this buffer must be greater or equal to zero. If the size of this buffer is zero then the default size will be
     * used (= 256 bytes).
     */
    protected BulkFrameDecoder(int startBufferSize) {
        if (startBufferSize < 0) {
            throw new IllegalArgumentException("startBufferSize must be greater or equal to zero: " + startBufferSize);
        }

        this.startBufferSize = startBufferSize;

        CodecUtil.ensureNotSharable(this);
    }

    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        buffer = internalBuffer(ctx, startBufferSize);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        releaseInternalBuffer();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        releaseInternalBuffer();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            final ByteBuf frame = (ByteBuf) msg;

            try {
                buffer.writeBytes(frame);
            } finally {
                frame.release();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        CodecOutputList out = CodecOutputList.newInstance();

        try {
            decode(ctx, buffer, out);
        } finally {
            buffer.discardReadBytes();
            fireChannelRead(ctx, out);
            out.recycle();
        }
    }

    private static void fireChannelRead(ChannelHandlerContext ctx, List<Object> msgs) {
        for (int i = 0, size = msgs.size(); i < size; ++i) {
            ctx.fireChannelRead(msgs.get(i));
        }
    }

    private static ByteBuf internalBuffer(ChannelHandlerContext ctx, int bufferSize) {
        if (bufferSize == 0) {
            return ctx.alloc().buffer();
        }

        return ctx.alloc().buffer(bufferSize);
    }

    private void releaseInternalBuffer() {
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }
}

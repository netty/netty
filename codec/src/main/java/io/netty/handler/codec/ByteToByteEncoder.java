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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundByteHandlerAdapter;

public abstract class ByteToByteEncoder extends ChannelOutboundByteHandlerAdapter {

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ByteBuf in = ctx.outboundByteBuffer();
        ByteBuf out = ctx.nextOutboundByteBuffer();

        int oldOutSize = out.readableBytes();
        while (in.readable()) {
            int oldInSize = in.readableBytes();
            try {
                encode(ctx, in, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
            if (oldInSize == in.readableBytes()) {
                break;
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.flush(future);
        }
    }

    public abstract void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception;
}

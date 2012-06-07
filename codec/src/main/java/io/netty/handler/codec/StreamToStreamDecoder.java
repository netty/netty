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

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;

public abstract class StreamToStreamDecoder extends ChannelInboundStreamHandlerAdapter {

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        callDecode(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelBuffer in = ctx.inboundByteBuffer();
        if (!in.readable()) {
            callDecode(ctx);
        }

        ChannelBuffer out = ctx.nextInboundByteBuffer();
        int oldOutSize = out.readableBytes();
        try {
            decodeLast(ctx, in, out);
        } catch (Throwable t) {
            if (t instanceof CodecException) {
                ctx.fireExceptionCaught(t);
            } else {
                ctx.fireExceptionCaught(new DecoderException(t));
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }

        ctx.fireChannelInactive();
    }

    private void callDecode(ChannelHandlerContext ctx) {
        ChannelBuffer in = ctx.inboundByteBuffer();
        ChannelBuffer out = ctx.nextInboundByteBuffer();

        int oldOutSize = out.readableBytes();
        while (in.readable()) {
            int oldInSize = in.readableBytes();
            try {
                decode(ctx, in, out);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
            if (oldInSize == in.readableBytes()) {
                break;
            }
        }

        if (out.readableBytes() > oldOutSize) {
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }
    }

    public abstract void decode(ChannelHandlerContext ctx, ChannelBuffer in, ChannelBuffer out) throws Exception;

    public void decodeLast(ChannelHandlerContext ctx, ChannelBuffer in, ChannelBuffer out) throws Exception {
        decode(ctx, in, out);
    }
}

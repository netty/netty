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
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;

public abstract class StreamToStreamCodec
        extends ChannelHandlerAdapter
        implements ChannelInboundHandler<Byte>, ChannelOutboundHandler<Byte> {

    private final StreamToStreamEncoder encoder = new StreamToStreamEncoder() {
        @Override
        public void encode(
                ChannelHandlerContext ctx,
                ChannelBuffer in, ChannelBuffer out) throws Exception {
            StreamToStreamCodec.this.encode(ctx, in, out);
        }
    };

    private final StreamToStreamDecoder decoder = new StreamToStreamDecoder() {
        @Override
        public void decode(
                ChannelHandlerContext ctx,
                ChannelBuffer in, ChannelBuffer out) throws Exception {
            StreamToStreamCodec.this.decode(ctx, in, out);
        }
    };

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public ChannelBufferHolder<Byte> newOutboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void flush(
            ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        encoder.flush(ctx, future);
    }

    public abstract void encode(
            ChannelHandlerContext ctx,
            ChannelBuffer in, ChannelBuffer out) throws Exception;

    public abstract void decode(
            ChannelHandlerContext ctx,
            ChannelBuffer in, ChannelBuffer out) throws Exception;
}

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
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelOutboundHandlerContext;

public abstract class StreamToMessageCodec<INBOUND_OUT, OUTBOUND_IN>
        extends ChannelHandlerAdapter<Byte, OUTBOUND_IN> {

    private final MessageToStreamEncoder<OUTBOUND_IN> encoder =
            new MessageToStreamEncoder<OUTBOUND_IN>() {
        @Override
        public void encode(
                ChannelOutboundHandlerContext<OUTBOUND_IN> ctx,
                OUTBOUND_IN msg, ChannelBuffer out) throws Exception {
            StreamToMessageCodec.this.encode(ctx, msg, out);
        }
    };

    private final StreamToMessageDecoder<INBOUND_OUT> decoder =
            new StreamToMessageDecoder<INBOUND_OUT>() {
        @Override
        public INBOUND_OUT decode(
                ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception {
            return StreamToMessageCodec.this.decode(ctx, in);
        }
    };

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public ChannelBufferHolder<OUTBOUND_IN> newOutboundBuffer(
            ChannelOutboundHandlerContext<OUTBOUND_IN> ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void flush(
            ChannelOutboundHandlerContext<OUTBOUND_IN> ctx, ChannelFuture future) throws Exception {
        encoder.flush(ctx, future);
    }

    public abstract void encode(
            ChannelOutboundHandlerContext<OUTBOUND_IN> ctx,
            OUTBOUND_IN msg, ChannelBuffer out) throws Exception;

    public abstract INBOUND_OUT decode(
            ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in) throws Exception;
}

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
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.TypeParameterMatcher;

public abstract class ByteToMessageCodec<I> extends ChannelDuplexHandler
        implements ChannelInboundByteHandler, ChannelOutboundMessageHandler<I> {

    private final TypeParameterMatcher outboundMsgMatcher;
    private final MessageToByteEncoder<I> encoder  = new MessageToByteEncoder<I>() {
        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return ByteToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception {
            ByteToMessageCodec.this.encode(ctx, msg, out);
        }
    };

    private final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
        @Override
        public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            return ByteToMessageCodec.this.decode(ctx, in);
        }

        @Override
        protected Object decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            return ByteToMessageCodec.this.decodeLast(ctx, in);
        }
    };

    protected ByteToMessageCodec() {
        this(ByteToMessageCodec.class, 0);
    }

    protected ByteToMessageCodec(
            @SuppressWarnings("rawtypes")
            Class<? extends ByteToMessageCodec> parameterizedHandlerType,
            int messageTypeParamIndex) {
        outboundMsgMatcher = TypeParameterMatcher.find(this, parameterizedHandlerType, messageTypeParamIndex);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        decoder.beforeAdd(ctx);
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        decoder.discardInboundReadBytes(ctx);
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        decoder.freeInboundBuffer(ctx);
    }

    @Override
    public MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        encoder.freeOutboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        encoder.flush(ctx, promise);
    }

    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;
    protected abstract Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception;
    protected Object decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        return decode(ctx, in);
    }
}

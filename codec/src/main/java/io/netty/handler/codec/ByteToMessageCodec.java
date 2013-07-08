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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * A Codec for on-the-fly encoding/decoding of bytes to messages and vise-versa.
 *
 * This can be thought of as a combination of {@link ByteToMessageDecoder} and {@link MessageToByteEncoder}.
 *
 * Be aware that sub-classes of {@link ByteToMessageCodec} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 */
public abstract class ByteToMessageCodec<I> extends ChannelDuplexHandler {

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
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            ByteToMessageCodec.this.decode(ctx, in, out);
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            ByteToMessageCodec.this.decodeLast(ctx, in, out);
        }
    };

    protected ByteToMessageCodec() {
        checkForSharableAnnotation();
        outboundMsgMatcher = TypeParameterMatcher.find(this, ByteToMessageCodec.class, "I");
    }

    protected ByteToMessageCodec(Class<? extends I> outboundMessageType) {
        checkForSharableAnnotation();
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    private void checkForSharableAnnotation() {
        if (getClass().isAnnotationPresent(Sharable.class)) {
            throw new IllegalStateException("@Sharable annotation is not allowed");
        }
    }

    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.messageReceived(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg) throws Exception {
        encoder.write(ctx, msg);
    }

    /**
     * @see MessageToByteEncoder#encode(ChannelHandlerContext, Object, ByteBuf)
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;

    /**
     * @see ByteToMessageDecoder#decode(ChannelHandlerContext, ByteBuf, List)
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * @see ByteToMessageDecoder#decodeLast(ChannelHandlerContext, ByteBuf, List)
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);
    }
}

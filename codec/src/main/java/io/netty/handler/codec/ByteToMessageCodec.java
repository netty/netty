/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.ChannelPromise;
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
    private final MessageToByteEncoder<I> encoder;

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

    /**
     * see {@link #ByteToMessageCodec(boolean)} with {@code true} as boolean parameter.
     */
    protected ByteToMessageCodec() {
        this(true);
    }

    /**
     * see {@link #ByteToMessageCodec(Class, boolean)} with {@code true} as boolean value.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected ByteToMessageCodec(boolean preferDirect) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.find(this, ByteToMessageCodec.class, "I");
        encoder = new Encoder(preferDirect);
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType, boolean preferDirect) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
        encoder = new Encoder(preferDirect);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        encoder.write(ctx, msg, promise);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        decoder.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerAdded(ctx);
        } finally {
            encoder.handlerAdded(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerRemoved(ctx);
        } finally {
            encoder.handlerRemoved(ctx);
        }
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
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decode(ctx, in, out);
        }
    }

    private final class Encoder extends MessageToByteEncoder<I> {
        Encoder(boolean preferDirect) {
            super(preferDirect);
        }

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return ByteToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception {
            ByteToMessageCodec.this.encode(ctx, msg, out);
        }
    }
}

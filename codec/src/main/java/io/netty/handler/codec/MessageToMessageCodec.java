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

import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.TypeParameterFinder;

/**
 * A Codec for on-the-fly encoding/decoding of message.
 *
 * This can be though of an combination of {@link MessageToMessageDecoder} and {@link MessageToMessageEncoder}.
 *
 * Here is an example of a {@link MessageToMessageCodec} which just decode from {@link Integer} to {@link Long}
 * and encode from {@link Long} to {@link Integer}.
 *
 * <pre>
 *     public class NumberCodec extends
 *             {@link MessageToMessageCodec}&lt;{@link Integer}, {@link Long}, {@link Long}, {@link Integer}&gt; {
 *         {@code @Override}
 *         public {@link Long} decode({@link ChannelHandlerContext} ctx, {@link Integer} msg)
 *                 throws {@link Exception} {
 *             return msg.longValue();
 *         }
 *
 *         {@code @Overrride}
 *         public {@link Integer} encode({@link ChannelHandlerContext} ctx, {@link Long} msg)
 *                 throws {@link Exception} {
 *             return msg.intValue();
 *         }
 *     }
 * </pre>
 */
public abstract class MessageToMessageCodec<INBOUND_IN, OUTBOUND_IN>
        extends ChannelDuplexHandler
        implements ChannelInboundMessageHandler<INBOUND_IN>,
                   ChannelOutboundMessageHandler<OUTBOUND_IN> {

    private final MessageToMessageEncoder<Object> encoder =
            new MessageToMessageEncoder<Object>() {
        @Override
        public boolean isEncodable(Object msg) throws Exception {
            return MessageToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object encode(ChannelHandlerContext ctx, Object msg) throws Exception {
            return MessageToMessageCodec.this.encode(ctx, (OUTBOUND_IN) msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void freeOutboundMessage(Object msg) throws Exception {
            MessageToMessageCodec.this.freeOutboundMessage((OUTBOUND_IN) msg);
        }
    };

    private final MessageToMessageDecoder<Object> decoder =
            new MessageToMessageDecoder<Object>() {

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            return MessageToMessageCodec.this.acceptInboundMessage(msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object decode(ChannelHandlerContext ctx, Object msg) throws Exception {
            return MessageToMessageCodec.this.decode(ctx, (INBOUND_IN) msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void freeInboundMessage(Object msg) throws Exception {
            MessageToMessageCodec.this.freeInboundMessage((INBOUND_IN) msg);
        }
    };

    private final Class<?> acceptedInboundMsgType;
    private final Class<?> acceptedOutboundMsgType;

    protected MessageToMessageCodec() {
        acceptedInboundMsgType = TypeParameterFinder.findActualTypeParameter(this, MessageToMessageCodec.class, 0);
        acceptedOutboundMsgType = TypeParameterFinder.findActualTypeParameter(this, MessageToMessageCodec.class, 1);
    }

    protected MessageToMessageCodec(
            @SuppressWarnings("rawtypes")
            Class<? extends ChannelInboundMessageHandlerAdapter> parameterizedInboundHandlerType,
            int inboundMessageTypeParamIndex,
            @SuppressWarnings("rawtypes")
            Class<? extends ChannelOutboundMessageHandlerAdapter> parameterizedOutboundHandlerType,
            int outboundMessageTypeParamIndex) {

        acceptedInboundMsgType = TypeParameterFinder.findActualTypeParameter(
                this, parameterizedInboundHandlerType, inboundMessageTypeParamIndex);
        acceptedOutboundMsgType = TypeParameterFinder.findActualTypeParameter(
                this, parameterizedOutboundHandlerType, outboundMessageTypeParamIndex);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageBuf<INBOUND_IN> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return (MessageBuf<INBOUND_IN>) decoder.newInboundBuffer(ctx);
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        decoder.freeInboundBuffer(ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageBuf<OUTBOUND_IN> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return (MessageBuf<OUTBOUND_IN>) encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        encoder.freeOutboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(
            ChannelHandlerContext ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
        encoder.flush(ctx, future);
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return acceptedInboundMsgType.isInstance(msg);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return acceptedOutboundMsgType.isInstance(msg);
    }

    protected abstract Object encode(ChannelHandlerContext ctx, OUTBOUND_IN msg) throws Exception;
    protected abstract Object decode(ChannelHandlerContext ctx, INBOUND_IN msg) throws Exception;

    protected void freeInboundMessage(INBOUND_IN msg) throws Exception {
        ChannelHandlerUtil.freeMessage(msg);
    }

    protected void freeOutboundMessage(OUTBOUND_IN msg) throws Exception {
        ChannelHandlerUtil.freeMessage(msg);
    }
}

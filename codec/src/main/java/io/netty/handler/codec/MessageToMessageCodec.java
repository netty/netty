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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * A Codec for on-the-fly encoding/decoding of message.
 *
 * This can be thought of as a combination of {@link MessageToMessageDecoder} and {@link MessageToMessageEncoder}.
 *
 * Here is an example of a {@link MessageToMessageCodec} which just decode from {@link Integer} to {@link Long}
 * and encode from {@link Long} to {@link Integer}.
 *
 * <pre>
 *     public class NumberCodec extends
 *             {@link MessageToMessageCodec}&lt;{@link Integer}, {@link Long}&gt; {
 *         {@code @Override}
 *         public {@link Long} decode({@link ChannelHandlerContext} ctx, {@link Integer} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.longValue());
 *         }
 *
 *         {@code @Override}
 *         public {@link Integer} encode({@link ChannelHandlerContext} ctx, {@link Long} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.intValue());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageCodec} will call
 * {@link ReferenceCounted#release()} on encoded / decoded messages.
 */
public abstract class MessageToMessageCodec<INBOUND_IN, OUTBOUND_IN> extends ChannelDuplexHandler {

    private final MessageToMessageEncoder<Object> encoder = new MessageToMessageEncoder<Object>() {

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return MessageToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            MessageToMessageCodec.this.encode(ctx, (OUTBOUND_IN) msg, out);
        }
    };

    private final MessageToMessageDecoder<Object> decoder;
    private final boolean allowSharable;
    private final TypeParameterMatcher inboundMsgMatcher;
    private final TypeParameterMatcher outboundMsgMatcher;

    /**
     * Create a new instance which will try to detect the types to decode and encode out of the type parameter
     * of the class.
     */
    protected MessageToMessageCodec() {
        this(true);
    }

    /**
     * Create a new instance.
     *
     * @param inboundMessageType    The type of messages to decode
     * @param outboundMessageType   The type of messages to encode
     */
    protected MessageToMessageCodec(
            Class<? extends INBOUND_IN> inboundMessageType, Class<? extends OUTBOUND_IN> outboundMessageType) {
        this(true, inboundMessageType, outboundMessageType);
    }

    /**
     * Create a new instance which will try to detect the types to decode and encode out of the type parameter
     * of the class.
     *
     * @param allowSharable         if this decoder is not sharable it will take care of calling
     *                              {@link ChannelHandlerContext#read()} if no message was produced and
     *                              {@link io.netty.channel.ChannelOption#AUTO_READ} is set to {@code false}.
     */
    protected MessageToMessageCodec(boolean allowSharable) {
        inboundMsgMatcher = TypeParameterMatcher.find(this, MessageToMessageCodec.class, "INBOUND_IN");
        outboundMsgMatcher = TypeParameterMatcher.find(this, MessageToMessageCodec.class, "OUTBOUND_IN");
        this.allowSharable = allowSharable;
        if (!allowSharable) {
            ensureNotSharable();
        }
        decoder = newDecoder(allowSharable);
    }

    /**
     * Create a new instance.
     *
     * @param allowSharable         if this decoder is not sharable it will take care of calling
     *                              {@link ChannelHandlerContext#read()} if no message was produced and
     *                              {@link io.netty.channel.ChannelOption#AUTO_READ} is set to {@code false}.
     * @param inboundMessageType    The type of messages to decode
     * @param outboundMessageType   The type of messages to encode
     */
    protected MessageToMessageCodec(boolean allowSharable,
            Class<? extends INBOUND_IN> inboundMessageType, Class<? extends OUTBOUND_IN> outboundMessageType) {
        inboundMsgMatcher = TypeParameterMatcher.get(inboundMessageType);
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
        this.allowSharable = allowSharable;
        if (!allowSharable) {
            ensureNotSharable();
        }
        decoder = newDecoder(allowSharable);
    }

    private MessageToMessageDecoder<Object> newDecoder(boolean allowSharable) {
        return new MessageToMessageDecoder<Object>(allowSharable) {

            @Override
            public boolean acceptInboundMessage(Object msg) throws Exception {
                return MessageToMessageCodec.this.acceptInboundMessage(msg);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                MessageToMessageCodec.this.decode(ctx, (INBOUND_IN) msg, out);
            }
        };
    }

    @Override
    public boolean isSharable() {
        if (allowSharable) {
            return super.isSharable();
        }
        return false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        encoder.write(ctx, msg, promise);
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return inboundMsgMatcher.match(msg);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    /**
     * @see MessageToMessageEncoder#encode(ChannelHandlerContext, Object, List)
     */
    protected abstract void encode(ChannelHandlerContext ctx, OUTBOUND_IN msg, List<Object> out)
            throws Exception;

    /**
     * @see MessageToMessageDecoder#decode(ChannelHandlerContext, Object, List)
     */
    protected abstract void decode(ChannelHandlerContext ctx, INBOUND_IN msg, List<Object> out)
            throws Exception;
}

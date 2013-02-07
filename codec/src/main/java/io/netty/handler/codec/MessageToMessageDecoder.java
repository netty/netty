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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateHandlerAdapter;

/**
 * {@link ChannelInboundMessageHandler} which decodes from one message to an other message
 *
 * For example here is an implementation which decodes a {@link String} to an {@link Integer} which represent
 * the length of the {@link String}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String}&gt; {
 *         public StringToIntegerDecoder() {
 *             super(String.class);
 *         }
 *
 *         {@code @Override}
 *         public {@link Object} decode({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             return message.length());
 *         }
 *     }
 * </pre>
 *
 */
public abstract class MessageToMessageDecoder<I>
        extends ChannelStateHandlerAdapter implements ChannelInboundMessageHandler<I> {

    private final Class<?>[] acceptedMsgTypes;

    /**
     * The types which will be accepted by the decoder. If a received message is an other type it will be just forwarded
     * to the next {@link ChannelInboundMessageHandler} in the {@link ChannelPipeline}
     */
    protected MessageToMessageDecoder(Class<?>... acceptedMsgTypes) {
        this.acceptedMsgTypes = ChannelHandlerUtil.acceptedMessageTypes(acceptedMsgTypes);
    }

    @Override
    public MessageBuf<I> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        MessageBuf<I> in = ctx.inboundMessageBuffer();
        MessageBuf<Object> out = ctx.nextInboundMessageBuffer();
        boolean notify = false;
        for (;;) {
            try {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                if (!isDecodable(msg)) {
                    out.add(msg);
                    notify = true;
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                boolean free = true;
                try {
                    Object omsg = decode(ctx, imsg);
                    if (omsg == null) {
                        // Decoder consumed a message but returned null.
                        // Probably it needs more messages because it's an aggregator.
                        continue;
                    }
                    if (omsg == imsg) {
                        free = false;
                    }
                    if (ChannelHandlerUtil.unfoldAndAdd(ctx, omsg, true)) {
                        notify = true;
                    }
                } finally {
                    if (free) {
                        freeInboundMessage(imsg);
                    }
                }
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }
        if (notify) {
            ctx.fireInboundBufferUpdated();
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this decoder.
     */
    public boolean isDecodable(Object msg) throws Exception {
        return ChannelHandlerUtil.acceptMessage(acceptedMsgTypes, msg);
    }

    /**
     * Decode from one message to an other. This method will be called till either the {@link MessageBuf} has
     * nothing left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to an other one
     * @return message      the decoded message or {@code null} if more messages are needed be cause the implementation
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract Object decode(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called after a message was processed via {@link #decode(ChannelHandlerContext, Object)} to free
     * up any resources that is held by the inbound message. You may want to override this if your implementation
     * just pass-through the input message or need it for later usage.
     */
    protected void freeInboundMessage(I msg) throws Exception {
        ChannelHandlerUtil.freeMessage(msg);
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundMessageBuffer().free();
    }
}

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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

/**
 * {@link ChannelOutboundMessageHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer},{@link String}&gt; {
 *         public StringToIntegerDecoder() {
 *             super(String.class);
 *         }
 *
 *         {@code @Override}
 *         public {@link String} encode({@link ChannelHandlerContext} ctx, {@link Integer} message)
 *                 throws {@link Exception} {
 *             return message.toString();
 *         }
 *     }
 * </pre>
 */
public abstract class MessageToMessageEncoder<I, O> extends ChannelOutboundMessageHandlerAdapter<I> {

    private final Class<?>[] acceptedMsgTypes;

    /**
     * The types which will be accepted by the decoder. If a received message is an other type it will be just forwared
     * to the next {@link ChannelOutboundMessageHandler} in the {@link ChannelPipeline}
     */
    protected MessageToMessageEncoder(Class<?>... acceptedMsgTypes) {
        this.acceptedMsgTypes = ChannelHandlerUtil.acceptedMessageTypes(acceptedMsgTypes);
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        MessageBuf<I> in = ctx.outboundMessageBuffer();
        for (;;) {
            try {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                if (!isEncodable(msg)) {
                    ChannelHandlerUtil.addToNextOutboundBuffer(ctx, msg);
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                O omsg = encode(ctx, imsg);
                if (omsg == null) {
                    // encode() might be waiting for more inbound messages to generate
                    // an aggregated message - keep polling.
                    continue;
                }

                ChannelHandlerUtil.unfoldAndAdd(ctx, omsg, false);
            } catch (Throwable t) {
                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new EncoderException(t));
                }
            }
        }

        ctx.flush(promise);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this encoder.
     *
     * @param msg the message
     */
    public boolean isEncodable(Object msg) throws Exception {
        return ChannelHandlerUtil.acceptMessage(acceptedMsgTypes, msg);
    }

    /**
     * Encode from one message to an other. This method will be called till either the {@link MessageBuf} has nothing
     * left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @return message      the encoded message or {@code null} if more messages are needed be cause the implementation
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract O encode(ChannelHandlerContext ctx, I msg) throws Exception;
}

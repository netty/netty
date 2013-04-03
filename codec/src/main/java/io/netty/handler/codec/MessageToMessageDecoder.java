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
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

/**
 * {@link ChannelInboundMessageHandler} which decodes from one message to an other message
 *
 * For example here is an implementation which decodes a {@link String} to an {@link Integer} which represent
 * the length of the {@link String}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link String} message,
 *                 {@link MessageBuf} out) throws {@link Exception} {
 *             out.add(message.length());
 *         }
 *     }
 * </pre>
 *
 */
public abstract class MessageToMessageDecoder<I> extends ChannelInboundMessageHandlerAdapter<I> {

    private static final ThreadLocal<MessageBuf<Object>> decoderOutput =
            new ThreadLocal<MessageBuf<Object>>() {
                @Override
                protected MessageBuf<Object> initialValue() {
                    return Unpooled.messageBuffer();
                }
            };

    protected MessageToMessageDecoder() { }

    protected MessageToMessageDecoder(Class<? extends I> inboundMessageType) {
        super(inboundMessageType);
    }

    @Override
    public final void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception {
        MessageBuf<Object> out = decoderOutput.get();
        try {
            decode(ctx, msg, out);
        } finally {
            for (;;) {
                Object obj =  out.poll();
                if (obj == null) {
                    break;
                }
                ChannelHandlerUtil.addToNextInboundBuffer(ctx, obj);
            }
        }
    }

    /**
     * Decode from one message to an other. This method will be called till either the {@link MessageBuf} has
     * nothing left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to an other one
     * @param out           the {@link MessageBuf} to which decoded messages should be added
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void decode(ChannelHandlerContext ctx, I msg, MessageBuf<Object> out) throws Exception;
}

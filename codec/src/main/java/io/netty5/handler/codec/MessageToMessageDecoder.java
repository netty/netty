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
package io.netty5.handler.codec;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.Resource;
import io.netty5.util.internal.TypeParameterMatcher;

import java.util.List;

/**
 * {@link ChannelHandler} which decodes from one message to another message.
 *
 * For example here is an implementation which decodes a {@link String} to an {@link Integer} which represent
 * the length of the {@link String}.
 *
 * <pre>{@code
 *     public class StringToIntegerDecoder extends
 *             MessageToMessageDecoder<String> {
 *
 *         @Override
 *         public void decode(ChannelHandlerContext ctx, String message,
 *                            List<Object> out) throws Exception {
 *             out.add(message.length());
 *         }
 *     }
 * }</pre>
 *
 * Note that messages passed to {@link #decode(ChannelHandlerContext, Object)} will be
 * {@linkplain Resource#dispose(Object) disposed of} automatically.
 * <p>
 * To take control of the message lifetime, you should instead override the
 * {@link #decodeAndClose(ChannelHandlerContext, Object)} method.
 * <p>
 * Do not override both.
 */
public abstract class MessageToMessageDecoder<I> extends ChannelHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToMessageDecoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageDecoder.class, "I");
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType    The type of messages to match and so decode
     */
    protected MessageToMessageDecoder(Class<? extends I> inboundMessageType) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                decodeAndClose(ctx, cast);
            } else {
                ctx.fireChannelRead(msg);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        }
    }

    /**
     * Decode from one message to another. This method will be called for each written message that can be handled
     * by this decoder.
     * <p>
     * The message will be {@linkplain Resource#dispose(Object) disposed of} after this call.
     * <p>
     * Subclasses that wish to sometimes pass messages through, should instead override the
     * {@link #decodeAndClose(ChannelHandlerContext, Object)} method.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to another one
     * @throws Exception    is thrown if an error occurs
     */
    protected void decode(ChannelHandlerContext ctx, I msg) throws Exception {
        throw new CodecException(getClass().getName() + " must override either decode() or decodeAndClose().");
    }

    /**
     * Decode from one message to another. This method will be called for each written message that can be handled
     * by this decoder.
     * <p>
     * The message will not be automatically {@linkplain Resource#dispose(Object) disposed of} after this call.
     * Instead, the responsibility of ensuring that messages are disposed of falls upon the implementor of this method.
     * <p>
     * Subclasses that wish to have incoming messages automatically disposed of should instead override the
     * {@link #decodeAndClose(ChannelHandlerContext, Object)} method.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to another one
     * @throws Exception    is thrown if an error occurs
     */
    protected void decodeAndClose(ChannelHandlerContext ctx, I msg) throws Exception {
        try {
            decode(ctx, msg);
        } finally {
            Resource.dispose(msg);
        }
    }
}

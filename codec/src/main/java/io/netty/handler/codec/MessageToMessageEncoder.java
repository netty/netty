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
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;

/**
 * {@link ChannelOutboundMessageHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer}&gt; {
 *         public StringToIntegerDecoder() {
 *             super(String.class);
 *         }
 *
 *         {@code @Override}
 *         public {@link Object} encode({@link ChannelHandlerContext} ctx, {@link Integer} message)
 *                 throws {@link Exception} {
 *             return message.toString();
 *         }
 *     }
 * </pre>
 *
 */
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundMessageHandlerAdapter<I> {

    protected MessageToMessageEncoder() { }

    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        super(outboundMessageType);
    }

    @Override
    public final void flush(ChannelHandlerContext ctx, I msg) throws Exception {
        try {
            Object encoded = encode(ctx, msg);
            // Handle special case when the encoded output is a ByteBuf and the next handler in the pipeline
            // accept bytes. Related to #1222
            ChannelHandlerUtil.addToNextOutboundBuffer(ctx, encoded);

        } catch (CodecException e) {
            throw e;
        } catch (Exception e) {
            throw new CodecException(e);
        }
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
    protected abstract Object encode(ChannelHandlerContext ctx, I msg) throws Exception;
}

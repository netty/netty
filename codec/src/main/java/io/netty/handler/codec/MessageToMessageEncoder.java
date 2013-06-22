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

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelOutboundHandlerAdapter} which encodes from one message to an other message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer}&gt; {
 *
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} message, {@link MessageList} out)
 *                 throws {@link Exception} {
 *             out.add(message.toString());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageEncoder} will call
 * {@link ReferenceCounted#release()} on encoded messages.
 */
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    protected MessageToMessageEncoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageEncoder.class, "I");
    }

    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
    }

    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, MessageList<Object> msgs, ChannelPromise promise) throws Exception {
        MessageList<Object> out = MessageList.newInstance();
        boolean success = false;
        try {
            int size = msgs.size();
            for (int i = 0; i < size; i ++) {
                // handler was removed in the loop so now copy over all remaining messages
                if (ctx.isRemoved()) {
                    out.add(msgs, i, size - i);
                    break;
                }
                Object m = msgs.get(i);
                if (acceptOutboundMessage(m)) {
                    @SuppressWarnings("unchecked")
                    I cast = (I) m;
                    try {
                        encode(ctx, cast, out);
                    } finally {
                        ByteBufUtil.release(cast);
                    }
                } else {
                    out.add(m);
                }
            }
            success = true;
        } catch (CodecException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            msgs.recycle();
            if (success) {
                ctx.write(out, promise);
            } else {
                out.releaseAllAndRecycle();
            }
        }
    }

    /**
     * Encode from one message to an other. This method will be called till either the {@link MessageList} has nothing
     * left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @param out           the {@link MessageList} into which the encoded msg should be added
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, MessageList<Object> out) throws Exception;
}

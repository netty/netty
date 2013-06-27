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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;


/**
 * {@link ChannelOutboundHandlerAdapter} which encodes message in a stream-like fashion from one message to an
 * {@link ByteBuf}.
 *
 *
 * Example implementation which encodes {@link Integer}s to a {@link ByteBuf}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToByteEncoder}&lt;{@link Integer}&gt; {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} msg, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 */
public abstract class MessageToByteEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;
    private final boolean preferDirect;

    protected MessageToByteEncoder() {
        this(true);
    }

    protected MessageToByteEncoder(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    protected MessageToByteEncoder(boolean preferDirect) {
        matcher = TypeParameterMatcher.find(this, MessageToByteEncoder.class, "I");
        this.preferDirect = preferDirect;
    }

    protected MessageToByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
        this.preferDirect = preferDirect;
    }

    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, MessageList<Object> msgs, ChannelPromise promise) throws Exception {
        MessageList<Object> out = MessageList.newInstance();
        boolean success = false;
        ByteBuf buf = null;
        try {
            int size = msgs.size();
            for (int i = 0; i < size; i ++) {
                // handler was removed in the loop so now copy over all remaining messages
                if (ctx.isRemoved()) {
                    if (buf != null && buf.isReadable())  {
                        out.add(buf);
                        buf = null;
                    }
                    out.add(msgs, i, size - i);
                    break;
                }
                Object m = msgs.get(i);
                if (acceptOutboundMessage(m)) {
                    @SuppressWarnings("unchecked")
                    I cast = (I) m;
                    if (buf == null) {
                        if (preferDirect) {
                            buf = ctx.alloc().ioBuffer();
                        } else {
                            buf = ctx.alloc().heapBuffer();
                        }
                    }
                    try {
                        encode(ctx, cast, buf);
                    } finally {
                        ReferenceCountUtil.release(cast);
                    }
                } else {
                    if (buf != null && buf.isReadable()) {
                        out.add(buf);
                        buf = null;
                    }

                    out.add(m);
                }
            }

            if (buf != null && buf.isReadable()) {
                out.add(buf);
                buf = null;
            }

            success = true;
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            msgs.recycle();
            if (buf != null) {
                buf.release();
            }
            if (success) {
                ctx.write(out, promise);
            } else {
                out.releaseAllAndRecycle();
            }
        }
    }

    /**
     * Encode a message into a {@link ByteBuf}. This method will be called till the {@link MessageList} has
     * nothing left.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToByteEncoder} belongs to
     * @param msg           the message to encode
     * @param out           the {@link ByteBuf} into which the encoded message will be written
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;
}

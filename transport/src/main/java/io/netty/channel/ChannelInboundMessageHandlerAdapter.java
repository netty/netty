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
package io.netty.channel;

import io.netty.buffer.BufUtil;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelHandler} which handles inbound messages of a specific type.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link ChannelInboundMessageHandlerAdapter}&lt;{@link String}&gt; {
 *         public StringToIntegerDecoder() {
 *             super(String.class);
 *         }
 *
 *         {@code @Override}
 *         public void messageReceived({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             // Do something with the String
 *             ...
 *             ...
 *         }
 *     }
 * </pre>
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelInboundMessageHandlerAdapter<I>
        extends ChannelStateHandlerAdapter implements ChannelInboundMessageHandler<I> {

    private final TypeParameterMatcher msgMatcher;

    protected ChannelInboundMessageHandlerAdapter() {
        this(ChannelInboundMessageHandlerAdapter.class, 0);
    }

    protected ChannelInboundMessageHandlerAdapter(
            @SuppressWarnings("rawtypes")
            Class<? extends ChannelInboundMessageHandlerAdapter> parameterizedHandlerType,
            int messageTypeParamIndex) {
        msgMatcher = TypeParameterMatcher.find(this, parameterizedHandlerType, messageTypeParamIndex);
    }

    @Override
    public MessageBuf<I> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundMessageBuffer().free();
    }

    @Override
    public final void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        if (!beginMessageReceived(ctx)) {
            return;
        }

        MessageBuf<Object> in = ctx.inboundMessageBuffer();
        MessageBuf<Object> out = ctx.nextInboundMessageBuffer();
        int oldOutSize = out.size();
        try {
            boolean unsupportedFound = false;
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                try {
                    if (!acceptInboundMessage(msg)) {
                        out.add(msg);
                        unsupportedFound = true;
                        continue;
                    }

                    if (unsupportedFound) {
                        // the last message were unsupported, but now we received one that is supported.
                        // So reset the flag and notify the next context
                        unsupportedFound = false;
                        ctx.fireInboundBufferUpdated();
                        oldOutSize = out.size();
                    }

                    @SuppressWarnings("unchecked")
                    I imsg = (I) msg;
                    try {
                        messageReceived(ctx, imsg);
                    } finally {
                        freeInboundMessage(imsg);
                    }
                } catch (Throwable t) {
                    exceptionCaught(ctx, t);
                }
            }
        } finally {
            if (oldOutSize != out.size()) {
                ctx.fireInboundBufferUpdated();
            }

            endMessageReceived(ctx);
        }
    }

    /**
     * Returns {@code true} if and only if the specified message can be handled by this handler.
     *
     * @param msg the message
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msgMatcher.match(msg);
    }

    /**
     * Will get notified once {@link #inboundBufferUpdated(ChannelHandlerContext)} was called.
     *
     * If this method returns {@code false} no further processing of the {@link MessageBuf}
     * will be done until the next call of {@link #inboundBufferUpdated(ChannelHandlerContext)}.
     *
     * This will return {@code true} by default, and may get overriden by sub-classes for
     * special handling.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     */
    protected boolean beginMessageReceived(ChannelHandlerContext ctx) throws Exception {
        return true;
    }

    /**
     * Is called once a message was received.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @param msg           the message to handle
     * @throws Exception    thrown when an error accour
     */
    protected abstract void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called after all messages of the {@link MessageBuf} was consumed.
     *
     * Super-classes may-override this for special handling.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @throws Exception    thrown when an error accour
     */
    protected void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Is called after a message was processed via {@link #messageReceived(ChannelHandlerContext, Object)} to free
     * up any resources that is held by the inbound message. You may want to override this if your implementation
     * just pass-through the input message or need it for later usage.
     */
    protected void freeInboundMessage(I msg) throws Exception {
        BufUtil.free(msg);
    }
}

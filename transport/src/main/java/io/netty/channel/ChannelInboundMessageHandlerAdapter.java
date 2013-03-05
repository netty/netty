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
import io.netty.util.Signal;
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

    /**
     * Thrown by {@link #messageReceived(ChannelHandlerContext, Object)} to abort message processing.
     */
    protected static final Signal ABORT = new Signal(ChannelInboundMessageHandlerAdapter.class.getName() + ".ABORT");

    private final TypeParameterMatcher msgMatcher;

    protected ChannelInboundMessageHandlerAdapter() {
        msgMatcher = TypeParameterMatcher.find(this, ChannelInboundMessageHandlerAdapter.class, "I");
    }

    @Override
    public MessageBuf<I> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundMessageBuffer().release();
    }

    @Override
    public final void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        MessageBuf<Object> in = ctx.inboundMessageBuffer();
        if (in.isEmpty() || !beginMessageReceived(ctx)) {
            return;
        }

        MessageBuf<Object> out = ctx.nextInboundMessageBuffer();
        int oldOutSize = out.size();
        try {
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                if (!acceptInboundMessage(msg)) {
                    out.add(msg);
                    continue;
                }

                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                try {
                    messageReceived(ctx, imsg);
                } finally {
                    BufUtil.release(imsg);
                }
            }
        } catch (Signal abort) {
            abort.expect(ABORT);
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
    protected boolean acceptInboundMessage(Object msg) throws Exception {
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
    protected boolean beginMessageReceived(
            @SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx) throws Exception {
        return true;
    }

    /**
     * Is called once a message was received.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @param msg           the message to handle
     */
    protected abstract void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called when {@link #messageReceived(ChannelHandlerContext, Object)} returns.
     *
     * Super-classes may-override this for special handling.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     */
    protected void endMessageReceived(
            @SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}

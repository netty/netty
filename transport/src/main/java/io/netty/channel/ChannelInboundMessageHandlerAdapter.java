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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerUtil.SingleInboundMessageHandler;
import io.netty.util.Signal;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelHandler} which handles inbound messages of a specific type.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link ChannelInboundMessageHandlerAdapter}&lt;{@link String}&gt; {
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
 * If your {@link ChannelInboundMessageHandlerAdapter} handles messages of type {@link ByteBuf} or {@link Object}
 * and you want to add a {@link ByteBuf} to the next buffer in the {@link ChannelPipeline} use
 * {@link ChannelHandlerUtil#addToNextInboundBuffer(ChannelHandlerContext, Object)}.
 *
 * <p>
 * One limitation to keep in mind is that it is not possible to detect the handled message type of you specify
 * {@code I} while instance your class. Because of this Netty does not allow to do so and will throw an Exception
 * if you try. For this cases you should handle the type detection by your self by override the
 * {@link #acceptInboundMessage(Object)} method and use {@link Object} as type parameter.
 *
 * <pre>
 *    public class GenericHandler&lt;I&gt; extends
 *             {@link ChannelInboundMessageHandlerAdapter}&lt;{@link Object}&gt; {
 *
 *         {@code @Override}
 *         public void messageReceived({@link ChannelHandlerContext} ctx, {@link Object} message)
 *                 throws {@link Exception} {
 *             I msg = (I) message;
 *             // Do something with the msg
 *             ...
 *             ...
 *         }
 *
 *         {@code @Override}
 *         public boolean acceptInboundMessage(Object msg) throws Exception {
 *             // Add your check here
 *         }
 *     }
 * </pre>
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelInboundMessageHandlerAdapter<I>
        extends ChannelStateHandlerAdapter
        implements ChannelInboundMessageHandler<I>, SingleInboundMessageHandler<I> {

    /**
     * Thrown by {@link #messageReceived(ChannelHandlerContext, Object)} to abort message processing.
     */
    protected static final Signal ABORT = ChannelHandlerUtil.ABORT;

    private final TypeParameterMatcher msgMatcher;

    protected ChannelInboundMessageHandlerAdapter() {
        msgMatcher = TypeParameterMatcher.find(this, ChannelInboundMessageHandlerAdapter.class, "I");
    }

    protected ChannelInboundMessageHandlerAdapter(Class<? extends I> inboundMessageType) {
        msgMatcher = TypeParameterMatcher.get(inboundMessageType);
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
        ChannelHandlerUtil.handleInboundBufferUpdated(ctx, this);
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msgMatcher.match(msg);
    }

    @Override
    public boolean beginMessageReceived(ChannelHandlerContext ctx) throws Exception {
        return true;
    }

    @Override
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}

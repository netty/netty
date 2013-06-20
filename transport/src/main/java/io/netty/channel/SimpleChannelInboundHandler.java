/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelInboundHandlerAdapter} which allows to explicit only handle a specific type of messages.
 *
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link SimpleChannelInboundHandler}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         public void messageReceived({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *     }
 * </pre>
 *
 */
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {

    private static final Object UNRELEASABLE = new Object();

    private final TypeParameterMatcher matcher;
    private final boolean autoRelease;

    protected SimpleChannelInboundHandler() {
        this(true);
    }

    protected SimpleChannelInboundHandler(boolean autoRelease) {
        matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
        this.autoRelease = autoRelease;
    }

    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType) {
        this(inboundMessageType, true);
    }

    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType, boolean autoRelease) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
        this.autoRelease = autoRelease;
    }

    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        MessageList<Object> unaccepted = MessageList.newInstance();
        int size = msgs.size();
        try {
            beginMessageReceived(ctx);

            for (int i = 0; i < size; i++) {
                Object msg = msgs.get(i);
                if (!ctx.isRemoved() && acceptInboundMessage(msg)) {
                    if (!unaccepted.isEmpty()) {
                        ctx.fireMessageReceived(unaccepted);
                        unaccepted = MessageList.newInstance();
                    }

                    @SuppressWarnings("unchecked")
                    I imsg = (I) msg;
                    messageReceived(ctx, imsg);
                } else {
                    if (autoRelease) {
                        msgs.set(i, UNRELEASABLE); // Prevent the message added to 'unaccepted' from being released.
                    }
                    unaccepted.add(msg);
                }
            }
        } finally {
            try {
                if (autoRelease) {
                    msgs.releaseAllAndRecycle();
                } else {
                    msgs.recycle();
                }
            } finally {
                try {
                    endMessageReceived(ctx);
                } finally {
                    ctx.fireMessageReceived(unaccepted);
                }
            }
        }
    }

    /**
     * Is called for each message of type {@link I}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link SimpleChannelInboundHandler}
     *                      belongs to
     * @param msg           the message to handle
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Is called before the first {@link #messageReceived(ChannelHandlerContext, Object)} of the current
     * {@link MessageList} is handled.
     *
     * @param ctx           the {@link ChannelHandlerContext} which is bound to this handler
     */
    @SuppressWarnings("UnusedParameters")
    protected void beginMessageReceived(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Is called after the last {@link #messageReceived(ChannelHandlerContext, Object)} of the current
     * {@link MessageList} is handled.
     *
     * @param ctx           the {@link ChannelHandlerContext} which is bound to this handler
     */
    @SuppressWarnings("UnusedParameters")
    protected void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}

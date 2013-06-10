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

    private final TypeParameterMatcher matcher;

    protected SimpleChannelInboundHandler() {
        matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
    }

    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
    }

    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        MessageList<Object> unaccepted = MessageList.newInstance();
        int size = msgs.size();
        try {
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
                    unaccepted.add(msg);
                }
            }
        } finally {
            msgs.recycle();
            ctx.fireMessageReceived(unaccepted);
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
}

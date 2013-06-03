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
 *             {@link SingleMessageChannelInboundHandler}&lt;{@link String}&gt; {
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
public abstract class SingleMessageChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    protected SingleMessageChannelInboundHandler() {
        matcher = TypeParameterMatcher.find(this, SingleMessageChannelInboundHandler.class, "I");
    }

    protected SingleMessageChannelInboundHandler(Class<? extends I> inboundMessageType) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
    }

    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        MessageList<Object> messageList = MessageList.newInstance();
        try {
            for (int i = 0; i < msgs.size(); i++) {
                Object msg = msgs.get(i);
                if (acceptInboundMessage(msg)) {
                    if (!messageList.isEmpty()) {
                        ctx.fireMessageReceived(messageList);
                        messageList = MessageList.newInstance();
                    }

                    @SuppressWarnings("unchecked")
                    I imsg = (I) msg;
                    messageReceived(ctx, imsg);
                } else {
                    messageList.add(msg);
                }
            }
        } finally {
            if (!messageList.isEmpty()) {
                ctx.fireMessageReceived(messageList);
            } else {
                MessageList.recycle(messageList);
            }
        }
    }

    /**
     * Is called for each message of type {@link I}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link SingleMessageChannelInboundHandler}
     *                      belongs to
     * @param msg           the message to handle
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void messageReceived(ChannelHandlerContext ctx, I msg) throws Exception;
}

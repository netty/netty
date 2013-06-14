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

import io.netty.util.ReferenceCounted;
import io.netty.util.ReferenceCountUtil;


/**
 * Abstract base class for {@link ChannelInboundHandler} that would like to consume messages. This means they will
 * actually handle them and not pass them to the next handler in the {@link ChannelPipeline}.
 *
 * If you need to pass them throught the {@link ChannelPipeline} use {@link ChannelInboundHandlerAdapter}.
 */
public abstract class ChannelInboundConsumingHandler extends ChannelInboundHandlerAdapter {

    @Override
    public final void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        try {
            consume(ctx, msgs);
        } finally {
            msgs.releaseAllAndRecycle();
        }
    }

    /**
     * Consume the received {@link MessageList}. After this method is executed
     * {@link MessageList#releaseAllAndRecycle()} is called and so the {@link MessageList} is recycled and
     * {@link ReferenceCounted#release()} is called on all messages that implement {@link ReferenceCounted}.
     *
     * Be aware that because of this you must not hold a reference to a message or to the {@link MessageList} after
     * this method returns. If you really need to hold a reference to a message, use
     * {@link ReferenceCountUtil#retain(Object)} on it to increment the reference count and so make sure its not
     * released.
     *
     *
     * @param ctx           The {@link ChannelHandlerContext} which is bound to this
     *                      {@link ChannelInboundConsumingHandler}
     * @param msgs          The {@link .MessageList} to consume and handle
     * @throws Exception    thrown if an error accours
     */
    protected abstract void consume(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception;
}

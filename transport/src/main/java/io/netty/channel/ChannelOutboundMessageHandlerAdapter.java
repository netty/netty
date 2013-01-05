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

import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;

/**
 * Abstract base class which handles messages of a specific type.
 *
 * @param <I>   The type of the messages to handle
 */
public abstract class ChannelOutboundMessageHandlerAdapter<I>
        extends ChannelOutboundHandlerAdapter implements ChannelOutboundMessageHandler<I> {

    @Override
    public MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundMessageBuffer().free();
    }
}

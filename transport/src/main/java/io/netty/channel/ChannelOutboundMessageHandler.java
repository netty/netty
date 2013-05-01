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

/**
 * ChannelOutboundHandler implementation which operates on messages of a specific type
 * by pass them in a {@link MessageBuf} and consume then from there.
 *
 * If your {@link ChannelOutboundMessageHandler} handles messages of type {@link ByteBuf} or {@link Object}
 * and you want to add a {@link ByteBuf} to the next buffer in the {@link ChannelPipeline} use
 * {@link ChannelHandlerUtil#addToNextOutboundBuffer(ChannelHandlerContext, Object)}.
 *
 * @param <I>   the message type
 */
public interface ChannelOutboundMessageHandler<I> extends ChannelOutboundHandler {
    @Override
    MessageBuf<I> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception;
}

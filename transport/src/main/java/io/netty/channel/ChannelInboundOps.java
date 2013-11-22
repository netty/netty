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


/**
 * Interface which is shared by others which need to fire inbound events
 */
interface ChannelInboundOps {

    /**
     * A {@link Channel} was registered to its {@link EventLoop}.
     *
     * This will result in having the  {@link ChannelHandler#channelRegistered(ChannelHandlerContext)} method
     * called of the next  {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireChannelRegistered();

    /**
     * A {@link Channel} is active now, which means it is connected.
     *
     * This will result in having the  {@link ChannelHandler#channelActive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireChannelActive();

    /**
     * A {@link Channel} is inactive now, which means it is closed.
     *
     * This will result in having the  {@link ChannelHandler#channelInactive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireChannelInactive();

    /**
     * A {@link Channel} received an {@link Throwable} in one of its inbound operations.
     *
     * This will result in having the  {@link ChannelHandler#exceptionCaught(ChannelHandlerContext, Throwable)}
     * method  called of the next  {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireExceptionCaught(Throwable cause);

    /**
     * A {@link Channel} received an user defined event.
     *
     * This will result in having the  {@link ChannelHandler#userEventTriggered(ChannelHandlerContext, Object)}
     * method  called of the next  {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireUserEventTriggered(Object event);

    /**
     * A {@link Channel} received a message.
     *
     * This will result in having the {@link ChannelHandler#channelRead(ChannelHandlerContext, Object)}
     * method  called of the next {@link ChannelHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundOps fireChannelRead(Object msg);

    ChannelInboundOps fireChannelReadComplete();

    /**
     * Triggers an {@link ChannelHandler#channelWritabilityChanged(ChannelHandlerContext)}
     * event to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     */
    ChannelInboundOps fireChannelWritabilityChanged();
}

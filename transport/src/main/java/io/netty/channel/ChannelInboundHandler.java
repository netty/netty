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


public interface ChannelInboundHandler<T> extends ChannelHandler {

    ChannelBufferHolder<T> newInboundBuffer(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void channelRegistered(ChannelInboundHandlerContext<T> ctx) throws Exception;
    void channelUnregistered(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void channelActive(ChannelInboundHandlerContext<T> ctx) throws Exception;
    void channelInactive(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void inboundBufferUpdated(ChannelInboundHandlerContext<T> ctx) throws Exception;

    void exceptionCaught(ChannelInboundHandlerContext<T> ctx, Throwable cause) throws Exception;
    void userEventTriggered(ChannelInboundHandlerContext<T> ctx, Object evt) throws Exception;

}

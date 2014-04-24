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

import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;

/**
 * Invokes the event handler methods of {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}.
 * A user can specify a {@link ChannelHandlerInvoker} to implement a custom thread model unsupported by the default
 * implementation.
 */
public interface ChannelHandlerInvoker {

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * Invokes {@link ChannelInboundHandler#channelRegistered(ChannelHandlerContext)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelRegistered(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelInboundHandler#channelUnregistered(ChannelHandlerContext)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelUnregistered(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelInboundHandler#channelActive(ChannelHandlerContext)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelActive(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelInboundHandler#channelInactive(ChannelHandlerContext)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelInactive(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelHandler#exceptionCaught(ChannelHandlerContext, Throwable)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeExceptionCaught(ChannelHandlerContext ctx, Throwable cause);

    /**
     * Invokes {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}. This method is not for
     * a user but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeUserEventTriggered(ChannelHandlerContext ctx, Object event);

    /**
     * Invokes {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelRead(ChannelHandlerContext ctx, Object msg);

    /**
     * Invokes {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)}. This method is not for a user
     * but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelReadComplete(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelInboundHandler#channelWritabilityChanged(ChannelHandlerContext)}. This method is not for
     * a user but for the internal {@link ChannelHandlerContext} implementation. To trigger an event, use the methods in
     * {@link ChannelHandlerContext} instead.
     */
    void invokeChannelWritabilityChanged(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeBind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise);

    /**
     * Invokes
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeConnect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * Invokes {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeDisconnect(ChannelHandlerContext ctx, ChannelPromise promise);

    /**
     * Invokes {@link ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeClose(ChannelHandlerContext ctx, ChannelPromise promise);

    /**
     * Invokes {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeDeregister(ChannelHandlerContext ctx, ChannelPromise promise);

    /**
     * Invokes {@link ChannelOutboundHandler#read(ChannelHandlerContext)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeRead(ChannelHandlerContext ctx);

    /**
     * Invokes {@link ChannelOutboundHandler#write(ChannelHandlerContext, Object, ChannelPromise)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise);

    /**
     * Invokes {@link ChannelOutboundHandler#flush(ChannelHandlerContext)}.
     * This method is not for a user but for the internal {@link ChannelHandlerContext} implementation.
     * To trigger an event, use the methods in {@link ChannelHandlerContext} instead.
     */
    void invokeFlush(ChannelHandlerContext ctx);
}

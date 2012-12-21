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

import java.net.SocketAddress;

/**
 * {@link ChannelHandler} which will get notified for IO-outbound-operations.
 */
public interface ChannelOperationHandler extends ChannelHandler {
    /**
     * Called once a bind operation is made.
     *
     * @param ctx           the {@link ChannelHandlerContext} for which the bind operation is made
     * @param localAddress  the {@link SocketAddress} to which it should bound
     * @param future        the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception    thrown if an error accour
     */
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;

    /**
     * Called once a connect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the connect operation is made
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param localAddress      the {@link SocketAddress} which is used as source on connect
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelFuture future) throws Exception;

    /**
     * Called once a disconnect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the disconnect operation is made
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void disconnect(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;

    /**
     * Called once a close operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void close(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;

    /**
     * Called once a deregister operation is made from the current registered {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void deregister(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;

    /**
     * Called once a flush operation is made and so the outbound data should be written.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the flush operation is made
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;

    /**
     * Called once a sendFile operation is made and so the {@link FileRegion} should be transfered.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the flush operation is made
     * @param region            the {@link FileRegion} to transfer
     * @param future            the {@link ChannelFuture} to notify once the operation completes
     * @throws Exception        thrown if an error accour
     */
    void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelFuture future) throws Exception;

}

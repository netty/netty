/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * {@link ChannelHandler} which will get notified for IO-outbound-operations.
 */
public interface ChannelOutboundHandler extends ChannelHandler {

    /**
     * The number of the outbound bytes that are buffered / queued in this {@link ChannelOutboundHandler}.
     * This number will affect the writability of the {@link Channel} together the buffered / queued bytes in the
     * {@link Channel} itself.
     * By default, this method returns {@code 0}. If the {@link ChannelOutboundHandler} implementation buffers / queues
     * outbound data this methods should be implemented to return the correct value.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the operation is made.
     * @return                  the number of buffered / queued bytes.
     */
    @ChannelHandlerMask.Skip
    default long pendingOutboundBytes(ChannelHandlerContext ctx) {
        return 0;
    }

    /**
     * Called once a register operation is made for an {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void register(ChannelHandlerContext ctx, Promise<Void> promise) {
        ctx.register(promise);
    }

    /**
     * Called once a bind operation is made.
     *
     * @param ctx           the {@link ChannelHandlerContext} for which the bind operation is made
     * @param localAddress  the {@link SocketAddress} to which it should bound
     * @param promise       the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void bind(ChannelHandlerContext ctx, SocketAddress localAddress, Promise<Void> promise)  {
        ctx.bind(localAddress, promise);
    }

    /**
     * Called once a connect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the connect operation is made
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param localAddress      the {@link SocketAddress} which is used as source on connect
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, Promise<Void> promise) {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    /**
     * Called once a disconnect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the disconnect operation is made
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void disconnect(ChannelHandlerContext ctx, Promise<Void> promise) {
        ctx.disconnect(promise);
    }

    /**
     * Called once a close operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void close(ChannelHandlerContext ctx, Promise<Void> promise) {
        ctx.close(promise);
    }

    /**
     * Called once a deregister operation is made from the current registered {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the deregister operation is made
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void deregister(ChannelHandlerContext ctx, Promise<Void> promise) {
        ctx.deregister(promise);
    }

    /**
     * Intercepts {@link ChannelHandlerContext#read()}.
     */
    @ChannelHandlerMask.Skip
    default void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    /**
    * Called once a write operation is made. The write operation will write the messages through the
     * {@link ChannelPipeline}. Those are then ready to be flushed to the actual {@link Channel} once
     * {@link Channel#flush()} is called
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg               the message to write
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void write(ChannelHandlerContext ctx, Object msg, Promise<Void> promise) {
        ctx.write(msg, promise);
    }

    /**
     * Called once a flush operation is made. The flush operation will try to flush out all previous written messages
     * that are pending.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the flush operation is made
     */
    @ChannelHandlerMask.Skip
    default void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * Called once a shutdown operation was requested and should be executed.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the shutdown operation is made
     * @param type              the {@link ChannelShutdownType} that is used.
     * @param promise           the {@link Promise} to notify once the operation completes
     */
    @ChannelHandlerMask.Skip
    default void shutdown(ChannelHandlerContext ctx, ChannelShutdownType type,
                          Promise<Void> promise) {
        ctx.shutdown(type, promise);
    }
}

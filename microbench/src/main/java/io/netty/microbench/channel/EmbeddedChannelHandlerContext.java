/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbench.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public abstract class EmbeddedChannelHandlerContext implements ChannelHandlerContext {
    private static final String HANDLER_NAME = "microbench-delegator-ctx";
    private final EventLoop eventLoop;
    private final Channel channel;
    private final ByteBufAllocator alloc;
    private final ChannelHandler handler;
    private SocketAddress localAddress;

    protected EmbeddedChannelHandlerContext(ByteBufAllocator alloc, ChannelHandler handler, EmbeddedChannel channel) {
        this.alloc = checkNotNull(alloc, "alloc");
        this.channel = checkNotNull(channel, "channel");
        this.handler = checkNotNull(handler, "handler");
        this.eventLoop = checkNotNull(channel.executor(), "eventLoop");
    }

    protected abstract void handleException(Throwable t);

    @Override
    public final Channel channel() {
        return channel;
    }

    @Override
    public final EventExecutor executor() {
        return eventLoop;
    }

    @Override
    public final String name() {
        return HANDLER_NAME;
    }

    @Override
    public final ChannelHandler handler() {
        return handler;
    }

    @Override
    public final boolean isRemoved() {
        return false;
    }

    @Override
    public final void fireChannelRegistered() {
        // NOOP
    }

    @Override
    public final void fireChannelUnregistered() {
        // NOOP
    }

    @Override
    public final void fireChannelActive() {
        // NOOP
    }

    @Override
    public final void fireChannelInactive() {
        // NOOP
    }

    @Override
    public final void fireExceptionCaught(Throwable cause) {
        if (handler() instanceof ChannelInboundHandler) {
            try {
                ((ChannelInboundHandler) handler()).exceptionCaught(this, cause);
            } catch (Exception e) {
                handleException(e);
            }
        }
    }

    @Override
    public final void fireUserEventTriggered(Object event) {
        ReferenceCountUtil.release(event);
    }

    @Override
    public final void fireChannelRead(Object msg) {
        ReferenceCountUtil.release(msg);
    }

    @Override
    public final void fireChannelReadComplete() {
        // NOOP
    }

    @Override
    public final void fireChannelWritabilityChanged() {
        // NOOP
    }

    @Override
    public void register(Promise<Void> promise) {
        channel.register(promise);
    }

    @Override
    public Future<Void> register() {
        return channel.register();
    }

    @Override
    public final void bind(SocketAddress localAddress, Promise<Void> promise) {
        try {
            channel().bind(localAddress, promise);
            this.localAddress = localAddress;
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void connect(SocketAddress remoteAddress, Promise<Void> promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void connect(SocketAddress remoteAddress, SocketAddress localAddress,
                              Promise<Void> promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void disconnect(Promise<Void> promise) {
        try {
            channel().disconnect(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void close(Promise<Void> promise) {
        try {
            channel().close(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void deregister(Promise<Void> promise) {
        try {
            channel().deregister(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
    }

    @Override
    public final void read() {
        try {
            channel().read();
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public Future<Void> write(Object msg) {
        return channel().write(msg);
    }

    @Override
    public void write(Object msg, Promise<Void> promise) {
        channel().write(msg, promise);
    }

    @Override
    public final void flush() {
        channel().flush();
    }

    @Override
    public void writeAndFlush(Object msg, Promise<Void> promise) {
        channel().writeAndFlush(msg, promise);
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        return channel().writeAndFlush(msg);
    }

    @Override
    public final ChannelPipeline pipeline() {
        return channel().pipeline();
    }

    @Override
    public final ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public final <T> Promise<T> newPromise() {
        return channel().newPromise();
    }

    @Override
    public final <T> Future<T> newSucceededFuture(T result) {
        return channel().newSucceededFuture(result);
    }

    @Override
    public final <T> Future<T> newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }

    @Override
    public void fireChannelShutdown(ChannelShutdownType type) {
        // NOOP
    }

    @Override
    public void shutdown(ChannelShutdownType type, Promise<Void> promise) {
        channel().shutdown(type, promise);
    }
}

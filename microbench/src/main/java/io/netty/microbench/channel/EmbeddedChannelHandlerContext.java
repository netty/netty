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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

public abstract class EmbeddedChannelHandlerContext implements ChannelHandlerContext {
    private static final String HANDLER_NAME = "microbench-delegator-ctx";
    private final EventLoop eventLoop;
    private final Channel channel;
    private final ByteBufAllocator alloc;
    private final ChannelHandler handler;
    private SocketAddress localAddress;

    protected EmbeddedChannelHandlerContext(ByteBufAllocator alloc, ChannelHandler handler, EmbeddedChannel channel) {
        this.alloc = requireNonNull(alloc, "alloc");
        this.channel = requireNonNull(channel, "channel");
        this.handler = requireNonNull(handler, "handler");
        eventLoop = requireNonNull(channel.eventLoop(), "eventLoop");
    }

    protected abstract void handleException(Throwable t);

    @Override
    public final <T> Attribute<T> attr(AttributeKey<T> key) {
        return null;
    }

    @Override
    public final <T> boolean hasAttr(AttributeKey<T> key) {
        return false;
    }

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
    public final ChannelHandlerContext fireChannelRegistered() {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelUnregistered() {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelActive() {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelInactive() {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        try {
            handler().exceptionCaught(this, cause);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext fireUserEventTriggered(Object event) {
        ReferenceCountUtil.release(event);
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelRead(Object msg) {
        ReferenceCountUtil.release(msg);
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelReadComplete() {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelWritabilityChanged() {
        return this;
    }

    @Override
    public final Future<Void> bind(SocketAddress localAddress) {
        Promise<Void> promise = newPromise();
        bind(localAddress, promise);
        return promise;
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress) {
        Promise<Void> promise = newPromise();
        connect(remoteAddress, promise);
        return promise;
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        Promise<Void> promise = newPromise();
        connect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public final Future<Void> disconnect() {
        Promise<Void> promise = newPromise();
        disconnect(promise);
        return promise;
    }

    @Override
    public final Future<Void> close() {
        Promise<Void> promise = newPromise();
        close(promise);
        return promise;
    }

    @Override
    public Future<Void> register() {
        Promise<Void> promise = newPromise();
        register(promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext register(Promise<Void> promise) {
        try {
            channel().register(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final Future<Void> deregister() {
        Promise<Void> promise = newPromise();
        deregister(promise);
        return promise;
    }

    @Override
    public final ChannelHandlerContext bind(SocketAddress localAddress, Promise<Void> promise) {
        try {
            channel().bind(localAddress, promise);
            this.localAddress = localAddress;
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext connect(SocketAddress remoteAddress, Promise<Void> promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext connect(SocketAddress remoteAddress, SocketAddress localAddress,
                                                Promise<Void> promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext disconnect(Promise<Void> promise) {
        try {
            channel().disconnect(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext close(Promise<Void> promise) {
        try {
            channel().close(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext deregister(Promise<Void> promise) {
        try {
            channel().deregister(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext read() {
        try {
            channel().read();
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public Future<Void> write(Object msg) {
        return channel().write(msg);
    }

    @Override
    public ChannelHandlerContext write(Object msg, Promise<Void> promise) {
        channel().write(msg, promise);
        return this;
    }

    @Override
    public final ChannelHandlerContext flush() {
        channel().flush();
        return this;
    }

    @Override
    public ChannelHandlerContext writeAndFlush(Object msg, Promise<Void> promise) {
        channel().writeAndFlush(msg, promise);
        return this;
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        Promise<Void> promise = newPromise();
        writeAndFlush(msg, promise);
        return promise;
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
    public final Promise<Void> newPromise() {
        return channel().newPromise();
    }

    @Override
    public final Future<Void> newSucceededFuture() {
        return channel().newSucceededFuture();
    }

    @Override
    public final Future<Void> newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }
}

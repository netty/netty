/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbench.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;

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
        this.eventLoop = checkNotNull(channel.eventLoop(), "eventLoop");
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
        return null;
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
    public final ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public final ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public final ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public final ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        try {
            channel().bind(localAddress, promise);
            this.localAddress = localAddress;
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
                                 ChannelPromise promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        try {
            channel().disconnect(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        try {
            channel().close(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public final ChannelFuture deregister(ChannelPromise promise) {
        try {
            channel().deregister(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
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
    public ChannelFuture write(Object msg) {
        return channel().write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return channel().write(msg, promise);
    }

    @Override
    public final ChannelHandlerContext flush() {
        channel().flush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return channel().writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
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
    public final ChannelPromise newPromise() {
        return channel().newPromise();
    }

    @Override
    public final ChannelProgressivePromise newProgressivePromise() {
        return channel().newProgressivePromise();
    }

    @Override
    public final ChannelFuture newSucceededFuture() {
        return channel().newSucceededFuture();
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }

    @Override
    public final ChannelPromise voidPromise() {
        return channel().voidPromise();
    }
}

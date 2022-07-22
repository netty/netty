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
package io.netty5.microbench.channel;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

public abstract class EmbeddedChannelHandlerContext implements ChannelHandlerContext {
    private static final String HANDLER_NAME = "microbench-delegator-ctx";
    private final EventLoop eventLoop;
    private final Channel channel;
    private final BufferAllocator bufferAllocator;
    private final ChannelHandler handler;
    private SocketAddress localAddress;

    protected EmbeddedChannelHandlerContext(BufferAllocator bufferAllocator, ChannelHandler handler,
                                            EmbeddedChannel channel) {
        this.bufferAllocator = requireNonNull(bufferAllocator, "bufferAllocator");
        this.channel = requireNonNull(channel, "channel");
        this.handler = requireNonNull(handler, "handler");
        eventLoop = requireNonNull(channel.executor(), "eventLoop");
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
    public final ChannelHandlerContext fireChannelShutdown(ChannelShutdownDirection direction) {
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelExceptionCaught(Throwable cause) {
        try {
            handler().channelExceptionCaught(this, cause);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelInboundEvent(Object event) {
        Resource.dispose(event);
        return this;
    }

    @Override
    public final ChannelHandlerContext fireChannelRead(Object msg) {
        Resource.dispose(msg);
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
    public final Future<Void> register() {
        try {
            return channel().register();
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> deregister() {
        try {
            return channel().deregister();
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> bind(SocketAddress localAddress) {
        try {
            this.localAddress = localAddress;
            return channel().bind(localAddress);
        } catch (Exception e) {
            this.localAddress = null;
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress) {
        try {
            return channel().connect(remoteAddress, localAddress);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        try {
            return channel().connect(remoteAddress, localAddress);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> disconnect() {
        try {
            return channel().disconnect();
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> close() {
        try {
            return channel().close();
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> shutdown(ChannelShutdownDirection direction) {
        try {
            return channel().shutdown(direction);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final ChannelHandlerContext read(ReadBufferAllocator readBufferAllocator) {
        try {
            channel().read(readBufferAllocator);
        } catch (Exception e) {
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
    public ChannelHandlerContext flush() {
        channel().flush();
        return this;
    }

    @Override
    public Future<Void> sendOutboundEvent(Object event) {
        return channel().sendOutboundEvent(event);
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
    public BufferAllocator bufferAllocator() {
        return bufferAllocator;
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

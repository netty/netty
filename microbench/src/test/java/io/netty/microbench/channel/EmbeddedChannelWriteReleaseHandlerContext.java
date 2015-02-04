/*
 * Copyright 2015 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;

public abstract class EmbeddedChannelWriteReleaseHandlerContext implements ChannelHandlerContext {
    private static final String HANDLER_NAME = "microbench-delegator-ctx";
    private final EventExecutor executor;
    private final Channel channel;
    private final ByteBufAllocator alloc;
    private final ChannelHandler handler;
    private SocketAddress localAddress;

    public EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler) {
        this(alloc, handler, new EmbeddedChannel());
    }

    public EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler,
            EmbeddedChannel channel) {
        this.alloc = checkNotNull(alloc, "alloc");
        this.channel = checkNotNull(channel, "channel");
        this.handler = checkNotNull(handler, "handler");
        this.executor = checkNotNull(channel.eventLoop(), "executor");
    }

    protected abstract void handleException(Throwable t);

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return false;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public ChannelHandlerInvoker invoker() {
        return null;
    }

    @Override
    public String name() {
        return HANDLER_NAME;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    @Override
    public boolean isRemoved() {
        return false;
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        try {
            handler().channelRegistered(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        try {
            handler().channelUnregistered(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        try {
            handler().channelActive(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        try {
            handler().channelInactive(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        try {
            handler().exceptionCaught(this, cause);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        try {
            handler().userEventTriggered(this, event);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        try {
            handler().channelRead(this, msg);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        try {
            handler().channelReadComplete(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        try {
            handler().channelWritabilityChanged(this);
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
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
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        try {
            channel().connect(remoteAddress, localAddress, promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
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
    public ChannelFuture disconnect(ChannelPromise promise) {
        try {
            channel().disconnect(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        try {
            channel().close(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        try {
            channel().deregister(promise);
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public ChannelHandlerContext read() {
        try {
            channel().read();
        } catch (Exception e) {
            handleException(e);
        }
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        try {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
                promise.setSuccess();
            } else {
                channel().write(msg, promise);
            }
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return promise;
    }

    @Override
    public ChannelHandlerContext flush() {
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
    public ChannelPipeline pipeline() {
        return channel().pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ChannelPromise newPromise() {
        return channel().newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return channel().newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return channel().newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel().voidPromise();
    }
}

/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelProgressivePromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FailedChannelFuture;
import io.netty.channel.SucceededChannelFuture;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ThreadLocalRandom;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public abstract class AbstractPooledChannel<C extends Channel, K extends ChannelPoolKey>
        implements PooledChannel<C, K>  {
    private final long hashCode = ThreadLocalRandom.current().nextLong();
    private final C channel;
    private final K key;
    private final ChannelPool<C, K> pool;
    private final PooledChannelPipeline pipeline;
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this, null);
    private final CloseFuture closeFuture = new CloseFuture(this);
    private final AtomicBoolean acquired = new AtomicBoolean(false);

    protected AbstractPooledChannel(C channel, K key, ChannelPool<C, K> pool) {
        this.channel = checkNotNull(channel, "channel");
        this.key = checkNotNull(key, "key");
        this.pool = checkNotNull(pool, "pool");
        pipeline = new PooledChannelPipeline(this, channel.pipeline());
        channel.closeFuture().addListener(closeFuture);
    }

    /**
     * Mark the {@link AbstractPooledChannel} as acquired. This methods needs to be called by the
     * {@link ChannelPool} implementation before return this {@link PooledChannel}.
     */
    public void acquired() {
        if (!acquired.compareAndSet(false, true)) {
            throw new IllegalStateException("PooledChannel already in use");
        }
    }

    @Override
    public final C unwrap() {
        return channel;
    }

    @Override
    public final ChannelPool<C, K> pool() {
        return pool;
    }

    @Override
    public final K key() {
        return key;
    }

    @Override
    public final Future<Void> release() {
        return release(newPromise());
    }

    @Override
    public final Future<Void> release(Promise<Void> promise) {
        if (!acquired.compareAndSet(true, false)) {
            return promise.setFailure(new IllegalStateException("PooledChannel was already released"));
        }
        return releaseToPool(promise);
    }

    /**
     * Release this {@link PooledChannel} back to the pool from which it was acquired.
     */
    protected abstract Future<Void> releaseToPool(Promise<Void> promise);

    @Override
    public final EventLoop eventLoop() {
        return channel.eventLoop();
    }

    @Override
    public final Channel parent() {
        return channel.parent();
    }

    @Override
    public final ChannelConfig config() {
        return channel.config();
    }

    @Override
    public final boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public final boolean isRegistered() {
        return channel.isRegistered();
    }

    @Override
    public final boolean isActive() {
        return channel.isActive();
    }

    @Override
    public final ChannelMetadata metadata() {
        return channel.metadata();
    }

    @Override
    public final SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public final SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public final ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public final boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public final Unsafe unsafe() {
        return channel.unsafe();
    }

    @Override
    public final ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return channel.alloc();
    }

    @Override
    public final ChannelPromise newPromise() {
        return new DefaultChannelPromise(this, eventLoop());
    }

    @Override
    public final ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(this, eventLoop());
    }

    @Override
    public final ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(this, eventLoop(), cause);
    }

    @Override
    public final ChannelPromise voidPromise() {
        return newPromise();
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress) {
        ChannelPromise promise = newPromise();
        channel.bind(localAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress) {
        ChannelPromise promise = newPromise();
        channel.connect(remoteAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        ChannelPromise promise = newPromise();
        channel.connect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture disconnect() {
        ChannelPromise promise = newPromise();
        channel.disconnect(promise);
        return promise;
    }

    @Override
    public final ChannelFuture close() {
        ChannelPromise promise = newPromise();
        channel.close(promise);
        return promise;
    }

    @Override
    public final ChannelFuture deregister() {
        ChannelPromise promise = newPromise();
        channel.deregister(promise);
        return promise;
    }

    private void validatePromise(ChannelPromise promise) {
        if (promise.channel() != this) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), this));
        }
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        validatePromise(promise);
        channel.bind(localAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        validatePromise(promise);
        channel.connect(remoteAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture connect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        validatePromise(promise);
        channel.connect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        validatePromise(promise);
        channel.disconnect(promise);
        return promise;
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        validatePromise(promise);
        channel.close(promise);
        return promise;
    }

    @Override
    public final ChannelFuture deregister(ChannelPromise promise) {
        validatePromise(promise);
        channel.deregister(promise);
        return promise;
    }

    @Override
    public final Channel read() {
        return channel.read();
    }

    @Override
    public final ChannelFuture write(Object msg) {
        ChannelPromise promise = newPromise();
        channel.write(msg, promise);
        return promise;
    }

    @Override
    public final ChannelFuture write(Object msg, ChannelPromise promise) {
        validatePromise(promise);
        channel.write(msg, promise);
        return promise;
    }

    @Override
    public final Channel flush() {
        return channel.flush();
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        validatePromise(promise);
        channel.writeAndFlush(msg, promise);
        return promise;
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg) {
        ChannelPromise promise = newPromise();
        channel.writeAndFlush(msg, promise);
        return promise;
    }

    @Override
    public final <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel.attr(key);
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    @Override
    public final int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        long ret = hashCode - o.hashCode();
        if (ret > 0) {
            return 1;
        }
        if (ret < 0) {
            return -1;
        }

        ret = System.identityHashCode(this) - System.identityHashCode(o);
        if (ret != 0) {
            return (int) ret;
        }

        // Jackpot! - different objects with same hashes
        throw new Error();
    }

    @Override
    public final int hashCode() {
        return (int) hashCode;
    }

    // We need to wrap the existing Channel and ChannelPipeline to ensure that we always return the PooledChannel.
    private final class PooledChannelPipeline implements ChannelPipeline {
        private final Channel channel;
        private final ChannelPipeline pipeline;

        PooledChannelPipeline(Channel channel, ChannelPipeline pipeline) {
            this.channel = channel;
            this.pipeline = pipeline;
        }

        // Wrap the ChannelHandler so we can pass in our own PooledChannelHandlerContext and PooledChannel.
        private ChannelHandler wrap(ChannelHandler handler) {
            boolean inbound = handler instanceof ChannelInboundHandler;
            boolean outbound = handler instanceof ChannelOutboundHandler;

            if (inbound) {
                if (outbound) {
                    return new PooledChannelDuplexHandler(channel, (ChannelOutboundHandler) handler);
                }
                return new PooledChannelInboundHandler(channel, (ChannelInboundHandler) handler);
            }
            if (outbound) {
                return new PooledChannelOutboundHandler(channel, (ChannelOutboundHandler) handler);
            }
            return new PooledChannelHandler(channel, handler);
        }

        private ChannelHandler[] wrap(ChannelHandler[] handlers) {
            ChannelHandler[] wrapped = new ChannelHandler[handlers.length];
            for (int i = 0; i < wrapped.length; i++) {
                wrapped[i] = wrap(handlers[i]);
            }
            return wrapped;
        }

        @Override
        public ChannelPipeline addFirst(String name, ChannelHandler handler) {
            pipeline.addFirst(name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
            pipeline.addFirst(group, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addLast(String name, ChannelHandler handler) {
            pipeline.addLast(name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
            pipeline.addLast(group, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
            pipeline.addBefore(baseName, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name,
                                         ChannelHandler handler) {
            pipeline.addBefore(group, baseName, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
            pipeline.addAfter(baseName, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name,
                                        ChannelHandler handler) {
            pipeline.addAfter(group, baseName, name, wrap(handler));
            return this;
        }

        @Override
        public ChannelPipeline addFirst(ChannelHandler... handlers) {
            pipeline.addFirst(wrap(handlers));
            return this;
        }

        @Override
        public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) {
            pipeline.addFirst(group, wrap(handlers));
            return this;
        }

        @Override
        public ChannelPipeline addLast(ChannelHandler... handlers) {
            pipeline.addLast(wrap(handlers));
            return this;
        }

        @Override
        public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) {
            pipeline.addLast(group, wrap(handlers));
            return this;
        }

        @Override
        public ChannelPipeline remove(ChannelHandler handler) {
            ChannelHandlerContext ctx = context(handler);
            if (ctx == null) {
                throw new NoSuchElementException();
            }
            pipeline.remove(ctx.handler());
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler remove(String name) {
            ChannelHandler handler = pipeline.remove(name);
            return ((PooledChannelHandler) handler).handler;
        }

        @Override
        public <T extends ChannelHandler> T remove(Class<T> handlerType) {
            T handler = get(handlerType);
            if (handler == null) {
                throw new NoSuchElementException();
            }
            remove(handler);
            return handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler removeFirst() {
            ChannelHandler handler = pipeline.removeFirst();
            if (handler == null) {
                throw new NoSuchElementException();
            }
            return ((PooledChannelHandler) handler).handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler removeLast() {
            ChannelHandler handler = pipeline.removeLast();
            if (handler == null) {
                throw new NoSuchElementException();
            }
            return ((PooledChannelHandler) handler).handler;
        }

        @Override
        public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
            ChannelHandlerContext context = context(oldHandler);
            if (context == null) {
                throw new NoSuchElementException();
            }
            replace(context.name(), newName, newHandler);

            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
            ChannelHandler handler = pipeline.replace(oldName, newName, newHandler);
            if (handler == null) {
                throw new NoSuchElementException();
            }
            return ((PooledChannelHandler) handler).handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                                    ChannelHandler newHandler) {
            ChannelHandlerContext context = context(oldHandlerType);
            if (context == null) {
                throw new NoSuchElementException();
            }
            return (T) replace(context.name(), newName, newHandler);
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler first() {
            ChannelHandler handler = pipeline.first();
            if (handler != null) {
                return ((PooledChannelHandler) handler).handler;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandlerContext firstContext() {
            ChannelHandler handler = pipeline.first();
            if (handler != null) {
                return ((PooledChannelHandler) handler).context;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler last() {
            ChannelHandler handler = pipeline.last();
            if (handler != null) {
                return ((PooledChannelHandler) handler).handler;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandlerContext lastContext() {
            ChannelHandler handler = pipeline.last();
            if (handler != null) {
                return ((PooledChannelHandler) handler).context;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandler get(String name) {
            ChannelHandler handler = pipeline.get(name);
            if (handler != null) {
                return ((PooledChannelHandler) handler).handler;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends ChannelHandler> T get(Class<T> handlerType) {
            for (Entry<String, ChannelHandler> entry: pipeline) {
                PooledChannelHandler handler = (PooledChannelHandler) entry.getValue();
                if (handlerType.isAssignableFrom(handler.handler.getClass())) {
                    return (T) handler.handler;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandlerContext context(ChannelHandler h) {
            for (Entry<String, ChannelHandler> entry: pipeline) {
                PooledChannelHandler handler = (PooledChannelHandler) entry.getValue();
                if (h == handler.handler) {
                    return handler.context;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandlerContext context(String name) {
            ChannelHandler handler = pipeline.get(name);
            if (handler != null) {
                return ((PooledChannelHandler) handler).context;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
            for (Entry<String, ChannelHandler> entry: pipeline) {
                PooledChannelHandler handler = (PooledChannelHandler) entry.getValue();
                if (handlerType.isAssignableFrom(handler.handler.getClass())) {
                    return handler.context;
                }
            }
            return null;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public List<String> names() {
            return pipeline.names();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, ChannelHandler> toMap() {
            Map<String, ChannelHandler> map = pipeline.toMap();
            int size = map.size();
            if (size == 0) {
                return map;
            }
            Map<String, ChannelHandler> unwrapped = new HashMap<String, ChannelHandler>(size);
            for (Entry<String, ChannelHandler> entries: map.entrySet()) {
                unwrapped.put(entries.getKey(), ((PooledChannelHandler) entries.getValue()).handler);
            }
            return unwrapped;
        }

        @Override
        public ChannelPipeline fireChannelRegistered() {
            pipeline.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelPipeline fireChannelUnregistered() {
            pipeline.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelPipeline fireChannelActive() {
            pipeline.fireChannelActive();
            return this;
        }

        @Override
        public ChannelPipeline fireChannelInactive() {
            pipeline.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelPipeline fireExceptionCaught(Throwable cause) {
            pipeline.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelPipeline fireUserEventTriggered(Object event) {
            pipeline.fireUserEventTriggered(event);
            return this;
        }

        @Override
        public ChannelPipeline fireChannelRead(Object msg) {
            pipeline.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelPipeline fireChannelReadComplete() {
            pipeline.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelPipeline fireChannelWritabilityChanged() {
            pipeline.fireChannelWritabilityChanged();
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
            validatePromise(promise);
            pipeline.bind(localAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            validatePromise(promise);
            pipeline.connect(remoteAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            validatePromise(promise);
            pipeline.connect(remoteAddress, localAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            validatePromise(promise);
            pipeline.disconnect(promise);
            return promise;
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            validatePromise(promise);
            pipeline.close(promise);
            return promise;
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            validatePromise(promise);
            pipeline.deregister(promise);
            return promise;
        }

        @Override
        public ChannelPipeline read() {
            pipeline.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
           return  write(msg, newPromise());
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            validatePromise(promise);
            pipeline.write(msg, promise);
            return promise;
        }

        @Override
        public ChannelPipeline flush() {
            pipeline.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            validatePromise(promise);
            pipeline.writeAndFlush(msg, promise);
            return promise;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return writeAndFlush(msg, newPromise());
        }

        @Override
        public Iterator<Entry<String, ChannelHandler>> iterator() {
            return toMap().entrySet().iterator();
        }
    }

    private class PooledChannelHandler implements ChannelHandler, ChannelHandlerContext {
        protected ChannelHandlerContext context;
        private final Channel channel;
        final ChannelHandler handler;

        PooledChannelHandler(Channel channel, ChannelHandler handler) {
            this.channel = channel;
            this.handler = handler;
        }

        @Override
        public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            context = ctx;
            handler.handlerAdded(this);
        }

        @Override
        public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            handler.handlerRemoved(this);
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            assert ctx == context;
            handler.exceptionCaught(this, cause);
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public EventExecutor executor() {
            return context.executor();
        }

        @Override
        public String name() {
            return context.name();
        }

        @Override
        public ChannelHandler handler() {
            return context.handler();
        }

        @Override
        public boolean isRemoved() {
            return context.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            context.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            context.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            context.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            context.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            context.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            context.fireUserEventTriggered(event);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            context.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            context.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            context.fireChannelWritabilityChanged();
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
            validatePromise(promise);
            context.bind(localAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            validatePromise(promise);
            context.connect(remoteAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            validatePromise(promise);
            context.connect(remoteAddress, localAddress, promise);
            return promise;
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            validatePromise(promise);
            context.disconnect(promise);
            return promise;
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            validatePromise(promise);
            context.close(promise);
            return promise;
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return context.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            context.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return write(msg, newPromise());
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            validatePromise(promise);
            context.write(msg, promise);
            return promise;
        }

        @Override
        public ChannelHandlerContext flush() {
            context.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            validatePromise(promise);
            context.writeAndFlush(msg, promise);
            return promise;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return writeAndFlush(msg, newPromise());
        }

        @Override
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return context.alloc();
        }

        @Override
        public ChannelPromise newPromise() {
            return new DefaultChannelPromise(channel, executor());
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return new DefaultChannelProgressivePromise(channel, executor());
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return succeededFuture;
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return new FailedChannelFuture(channel, executor(), cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return newPromise();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return context.attr(key);
        }
    }

    private class PooledChannelInboundHandler extends PooledChannelHandler implements ChannelInboundHandler {

        public PooledChannelInboundHandler(Channel channel, ChannelInboundHandler handler) {
            super(channel, handler);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelRegistered(this);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelUnregistered(this);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelActive(this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelInactive(this);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelRead(this, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelReadComplete(this);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).userEventTriggered(this, evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelWritabilityChanged(this);
        }
    }

    private class PooledChannelOutboundHandler extends PooledChannelHandler implements ChannelOutboundHandler {
        public PooledChannelOutboundHandler(Channel channel, ChannelOutboundHandler handler) {
            super(channel, handler);
        }

        @Override
        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).bind(this, localAddress, promise);
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                            ChannelPromise promise) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).connect(this, remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).disconnect(this, promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).close(this, promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).deregister(context, promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).read(this);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).write(this, msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelOutboundHandler) handler).flush(this);
        }
    }

    private final class PooledChannelDuplexHandler extends PooledChannelOutboundHandler
            implements ChannelInboundHandler {
        public PooledChannelDuplexHandler(Channel channel, ChannelOutboundHandler handler) {
            super(channel, handler);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelRegistered(this);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelUnregistered(this);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelActive(this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelInactive(this);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelRead(this, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelReadComplete(this);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).userEventTriggered(this, evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            assert ctx == context;
            ((ChannelInboundHandler) handler).channelWritabilityChanged(this);
        }
    }

    private static final class CloseFuture extends DefaultChannelPromise implements ChannelFutureListener {

        CloseFuture(Channel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            setClosed();
        }
    }
}

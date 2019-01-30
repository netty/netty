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

import io.netty.channel.Channel.Unsafe;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * The default {@link ChannelPipeline} implementation.  It is usually created
 * by a {@link Channel} implementation when the {@link Channel} is created.
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    private static final String HEAD_NAME = generateName0(HeadContext.class);
    private static final String TAIL_NAME = generateName0(TailContext.class);

    private static final FastThreadLocal<Map<Class<?>, String>> nameCaches =
            new FastThreadLocal<Map<Class<?>, String>>() {
        @Override
        protected Map<Class<?>, String> initialValue() throws Exception {
            return new WeakHashMap<>();
        }
    };

    private static final AtomicReferenceFieldUpdater<DefaultChannelPipeline, MessageSizeEstimator.Handle> ESTIMATOR =
            AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelPipeline.class, MessageSizeEstimator.Handle.class, "estimatorHandle");
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;

    private final Channel channel;
    private final ChannelFuture succeededFuture;
    private final VoidChannelPromise voidPromise;
    private final boolean touch = ResourceLeakDetector.isEnabled();
    private final List<AbstractChannelHandlerContext> handlers = new ArrayList<>(4);

    private volatile MessageSizeEstimator.Handle estimatorHandle;

    public DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, channel.eventLoop());
        voidPromise =  new VoidChannelPromise(channel, true);

        tail = new TailContext(this);
        head = new HeadContext(this);

        head.next = tail;
        tail.prev = head;
    }

    final MessageSizeEstimator.Handle estimatorHandle() {
        MessageSizeEstimator.Handle handle = estimatorHandle;
        if (handle == null) {
            handle = channel.config().getMessageSizeEstimator().newHandle();
            if (!ESTIMATOR.compareAndSet(this, null, handle)) {
                handle = estimatorHandle;
            }
        }
        return handle;
    }

    final Object touch(Object msg, AbstractChannelHandlerContext next) {
        return touch ? ReferenceCountUtil.touch(msg, next) : msg;
    }

    private AbstractChannelHandlerContext newContext(String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, name, handler);
    }

    @Override
    public final Channel channel() {
        return channel;
    }

    @Override
    public final EventExecutor executor() {
        return channel().eventLoop();
    }

    @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        checkMultiplicity(handler);
        if (name == null) {
            name = generateName(handler);
        }

        AbstractChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            if (context(name) != null) {
                throw new IllegalArgumentException("Duplicate handler name: " + name);
            }
            handlers.add(0, newCtx);
            if (!inEventLoop) {
                try {
                    executor.execute(() -> addFirst0(newCtx));
                    return this;
                } catch (Throwable cause) {
                    handlers.remove(0);
                    throw cause;
                }
            }
        }

        addFirst0(newCtx);
        return this;
    }

    private void addFirst0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        checkMultiplicity(handler);
        if (name == null) {
            name = generateName(handler);
        }

        AbstractChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (this) {
            if (context(name) != null) {
                throw new IllegalArgumentException("Duplicate handler name: " + name);
            }
            handlers.add(newCtx);
            if (!inEventLoop) {
                try {
                    executor.execute(() -> addLast0(newCtx));
                    return this;
                } catch (Throwable cause) {
                    handlers.remove(handlers.size() - 1);
                    throw cause;
                }
            }
        }

        addLast0(newCtx);
        return this;
    }

    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext ctx;

        checkMultiplicity(handler);
        if (name == null) {
            name = generateName(handler);
        }

        AbstractChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int i = findCtxIdx(context -> context.name().equals(baseName));

            if (i == -1) {
                throw new NoSuchElementException(baseName);
            }

            if (context(name) != null) {
                throw new IllegalArgumentException("Duplicate handler name: " + name);
            }
            ctx = handlers.get(i);
            handlers.add(i, newCtx);
            if (!inEventLoop) {
                try {
                    executor.execute(() -> addBefore0(ctx, newCtx));
                    return this;
                } catch (Throwable cause) {
                    handlers.remove(i);
                    throw cause;
                }
            }
        }

        addBefore0(ctx, newCtx);
        return this;
    }

    private void addBefore0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx.prev;
        newCtx.next = ctx;
        ctx.prev.next = newCtx;
        ctx.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext ctx;

        checkMultiplicity(handler);
        if (name == null) {
            name = generateName(handler);
        }

        AbstractChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int i = findCtxIdx(context -> context.name().equals(baseName));

            if (i == -1) {
                throw new NoSuchElementException(baseName);
            }

            if (context(name) != null) {
                throw new IllegalArgumentException("Duplicate handler name: " + name);
            }
            ctx = handlers.get(i);
            handlers.add(i + 1, newCtx);
            if (!inEventLoop) {
                try {
                    executor.execute(() -> addAfter0(ctx, newCtx));
                    return this;
                } catch (Throwable cause) {
                    handlers.remove(i + 1);
                    throw cause;
                }
            }
        }

        addAfter0(ctx, newCtx);
        return this;
    }

    private void addAfter0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx;
        newCtx.next = ctx.next;
        ctx.next.prev = newCtx;
        ctx.next = newCtx;
        callHandlerAdded0(newCtx);
    }

    public final ChannelPipeline addFirst(ChannelHandler handler) {
        return addFirst(null, handler);
    }

    @Override
    public final ChannelPipeline addFirst(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        if (handlers.length == 0 || handlers[0] == null) {
            return this;
        }

        int size;
        for (size = 1; size < handlers.length; size ++) {
            if (handlers[size] == null) {
                break;
            }
        }

        for (int i = size - 1; i >= 0; i --) {
            ChannelHandler h = handlers[i];
            addFirst(null, h);
        }

        return this;
    }

    public final ChannelPipeline addLast(ChannelHandler handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(null, h);
        }

        return this;
    }

    private String generateName(ChannelHandler handler) {
        Map<Class<?>, String> cache = nameCaches.get();
        Class<?> handlerType = handler.getClass();
        String name = cache.get(handlerType);
        if (name == null) {
            name = generateName0(handlerType);
            cache.put(handlerType, name);
        }

        synchronized (handlers) {
            // It's not very likely for a user to put more than one handler of the same type, but make sure to avoid
            // any name conflicts.  Note that we don't cache the names generated here.
            if (context(name) != null) {
                String baseName = name.substring(0, name.length() - 1); // Strip the trailing '0'.
                for (int i = 1;; i ++) {
                    String newName = baseName + i;
                    if (context(newName) == null) {
                        name = newName;
                        break;
                    }
                }
            }
        }

        return name;
    }

    private static String generateName0(Class<?> handlerType) {
        return StringUtil.simpleClassName(handlerType) + "#0";
    }

    private int findCtxIdx(Predicate<AbstractChannelHandlerContext> predicate) {
        for (int i = 0; i < handlers.size(); i++) {
            if (predicate.test(handlers.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public final ChannelPipeline remove(ChannelHandler handler) {
        final AbstractChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = findCtxIdx(context -> context.handler() == handler);
            if (idx == -1) {
                throw new NoSuchElementException();
            }
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                try {
                    executor.execute(() -> remove0(ctx));
                    return this;
                } catch (Throwable cause) {
                    handlers.add(idx, ctx);
                    throw cause;
                }
            }
        }

        remove0(ctx);
        return this;
    }

    @Override
    public final ChannelHandler remove(String name) {
        final AbstractChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = findCtxIdx(context -> context.name().equals(name));
            if (idx == -1) {
                throw new NoSuchElementException();
            }
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                try {
                    executor.execute(() -> remove0(ctx));
                    return ctx.handler();
                } catch (Throwable cause) {
                    handlers.add(idx, ctx);
                    throw cause;
                }
            }
        }

        remove0(ctx);
        return ctx.handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T remove(Class<T> handlerType) {
        final AbstractChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = findCtxIdx(context -> handlerType.isAssignableFrom(context.handler().getClass()));
            if (idx == -1) {
                throw new NoSuchElementException();
            }
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                try {
                    executor.execute(() -> remove0(ctx));
                    return (T) ctx.handler();
                } catch (Throwable cause) {
                    handlers.add(idx, ctx);
                    throw cause;
                }
            }
        }

        remove0(ctx);
        return (T) ctx.handler();
    }

    public final <T extends ChannelHandler> T removeIfExists(String name) {
        return removeIfExists(() -> findCtxIdx(context -> name.equals(context.name())));
    }

    public final <T extends ChannelHandler> T removeIfExists(Class<T> handlerType) {
        return removeIfExists(() -> findCtxIdx(
                context -> handlerType.isAssignableFrom(context.handler().getClass())));
    }

    public final <T extends ChannelHandler> T removeIfExists(ChannelHandler handler) {
        return removeIfExists(() -> findCtxIdx(context -> handler == context.handler()));
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelHandler> T removeIfExists(IntSupplier idxSupplier) {
        final AbstractChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = idxSupplier.getAsInt();
            if (idx == -1) {
                return null;
            }
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                try {
                    executor.execute(() -> remove0(ctx));
                    return (T) ctx.handler();
                } catch (Throwable cause) {
                    handlers.add(idx, ctx);
                    throw cause;
                }
            }
        }
        remove0(ctx);
        return (T) ctx.handler();
    }

    private void unlink(AbstractChannelHandlerContext ctx) {
        assert ctx != head && ctx != tail;
        AbstractChannelHandlerContext prev = ctx.prev;
        AbstractChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
    }

    private void remove0(AbstractChannelHandlerContext ctx) {
        unlink(ctx);
        callHandlerRemoved0(ctx);
    }

    @Override
    public final ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(ctx -> ctx.handler() == oldHandler, newName, newHandler);
        return this;
    }

    @Override
    public final ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(ctx -> ctx.name().equals(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(ctx -> oldHandlerType.isAssignableFrom(ctx.handler().getClass()), newName, newHandler);
    }

    private ChannelHandler replace(
            Predicate<AbstractChannelHandlerContext> predicate, String newName, ChannelHandler newHandler) {
        checkMultiplicity(newHandler);

        if (newName == null) {
            newName = generateName(newHandler);
        }
        AbstractChannelHandlerContext oldCtx;
        AbstractChannelHandlerContext newCtx = newContext(newName, newHandler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = findCtxIdx(predicate);
            if (idx == -1) {
                throw new NoSuchElementException();
            }
            oldCtx = handlers.get(idx);
            assert oldCtx != head && oldCtx != tail && oldCtx != null;

            if (!oldCtx.name().equals(newName)) {
                if (context(newName) != null) {
                    throw new IllegalArgumentException("Duplicate handler name: " + newName);
                }
            }
            AbstractChannelHandlerContext removed = handlers.set(idx, newCtx);
            assert removed != null;

            if (!inEventLoop) {
                try {
                    executor.execute(() -> replace0(oldCtx, newCtx));
                    return oldCtx.handler();
                } catch (Throwable cause) {
                    handlers.set(idx, oldCtx);
                    throw cause;
                }
            }
        }

        replace0(oldCtx, newCtx);
        return oldCtx.handler();
    }

    private void replace0(AbstractChannelHandlerContext oldCtx, AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = oldCtx.prev;
        AbstractChannelHandlerContext next = oldCtx.next;
        newCtx.prev = prev;
        newCtx.next = next;

        // Finish the replacement of oldCtx with newCtx in the linked list.
        // Note that this doesn't mean events will be sent to the new handler immediately
        // because we are currently at the event handler thread and no more than one handler methods can be invoked
        // at the same time (we ensured that in replace().)
        prev.next = newCtx;
        next.prev = newCtx;

        // update the reference to the replacement so forward of buffered content will work correctly
        oldCtx.prev = newCtx;
        oldCtx.next = newCtx;

        // Invoke newHandler.handlerAdded() first (i.e. before oldHandler.handlerRemoved() is invoked)
        // because callHandlerRemoved() will trigger channelRead() or flush() on newHandler and those
        // event handlers must be called after handlerAdded().
        callHandlerAdded0(newCtx);
        callHandlerRemoved0(oldCtx);
    }

    private static void checkMultiplicity(ChannelHandler handler) {
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(
                        h.getClass().getName() +
                        " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
    }

    private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
        try {
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                synchronized (handlers) {
                    handlers.remove(ctx);
                }

                unlink(ctx);
                ctx.callHandlerRemoved();

                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }

            if (removed) {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    private void callHandlerRemoved0(final AbstractChannelHandlerContext ctx) {
        // Notify the complete removal.
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new ChannelPipelineException(
                    ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }

    @Override
    public final ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        return ctx == null ? null : ctx.handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        return ctx == null ? null : (T) ctx.handler();
    }

    private AbstractChannelHandlerContext findCtx(Predicate<AbstractChannelHandlerContext> predicate) {
        for (int i = 0; i < handlers.size(); i++) {
            AbstractChannelHandlerContext ctx = handlers.get(i);
            if (predicate.test(ctx)) {
                return ctx;
            }
        }
        return null;
    }

    @Override
    public final ChannelHandlerContext context(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        synchronized (handlers) {
            return findCtx(ctx -> ctx.name().equals(name));
        }
    }

    @Override
    public final ChannelHandlerContext context(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        synchronized (handlers) {
            return findCtx(ctx -> ctx.handler() == handler);
        }
    }

    @Override
    public final ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }

        synchronized (handlers) {
            return findCtx(ctx -> handlerType.isAssignableFrom(ctx.handler().getClass()));
        }
    }

    @Override
    public final List<String> names() {
        synchronized (handlers) {
            List<String> names = new ArrayList<>(handlers.size());
            for (int i = 0; i < handlers.size(); i++) {
                names.add(handlers.get(i).name());
            }
            return names;
        }
    }

    /**
     * Returns the {@link String} representation of this pipeline.
     */
    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('{');
        synchronized (handlers) {
            if (!handlers.isEmpty())  {
                for (int i = 0; i < handlers.size(); i++) {
                    AbstractChannelHandlerContext ctx = handlers.get(i);

                    buf.append('(')
                            .append(ctx.name())
                            .append(" = ")
                            .append(ctx.handler().getClass().getName())
                            .append("), ");
                }
                buf.setLength(buf.length() - 2);
            }
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public ChannelHandler removeFirst() {
        synchronized (handlers) {
            if (handlers.isEmpty()) {
                throw new NoSuchElementException();
            }
            return handlers.remove(0).handler();
        }
    }

    @Override
    public ChannelHandler removeLast() {
        synchronized (handlers) {
            if (handlers.isEmpty()) {
                throw new NoSuchElementException();
            }
            return handlers.remove(handlers.size() - 1).handler();
        }
    }

    @Override
    public ChannelHandler first() {
        ChannelHandlerContext ctx = firstContext();
        return ctx == null ? null : ctx.handler();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        synchronized (handlers) {
            return handlers.isEmpty() ? null : handlers.get(0);
        }
    }

    @Override
    public ChannelHandler last() {
        ChannelHandlerContext ctx = lastContext();
        return ctx == null ? null : ctx.handler();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        synchronized (handlers) {
            return handlers.isEmpty() ? null : handlers.get(handlers.size() - 1);
        }
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map;
        synchronized (handlers) {
            if (handlers.isEmpty()) {
                return Collections.emptyMap();
            }
            map = new LinkedHashMap<>(handlers.size());
            for (int i = 0; i < handlers.size(); i++) {
                ChannelHandlerContext ctx = handlers.get(i);
                map.put(ctx.name(), ctx.handler());
            }
            return map;
        }
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }

    @Override
    public final ChannelPipeline fireChannelRegistered() {
        head.invokeChannelRegistered();
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelUnregistered() {
        head.invokeChannelUnregistered();
        return this;
    }

    /**
     * Removes all handlers from the pipeline one by one from tail (exclusive) to head (exclusive) to trigger
     * handlerRemoved().
     */
    private void destroy() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            destroy0();
        } else {
            executor.execute(this::destroy0);
        }
    }

    private void destroy0() {
        assert executor().inEventLoop();
        AbstractChannelHandlerContext ctx = this.tail.prev;
        while (ctx != head) {
            synchronized (handlers) {
                handlers.remove(ctx);
            }
            remove0(ctx);

            ctx = ctx.prev;
        }
    }

    @Override
    public final ChannelPipeline fireChannelActive() {
        head.invokeChannelActive();
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelInactive() {
        head.invokeChannelInactive();
        return this;
    }

    @Override
    public final ChannelPipeline fireExceptionCaught(Throwable cause) {
        head.invokeExceptionCaught(cause);
        return this;
    }

    @Override
    public final ChannelPipeline fireUserEventTriggered(Object event) {
        head.invokeUserEventTriggered(event);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelRead(Object msg) {
        head.invokeChannelRead(msg);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelReadComplete() {
        head.invokeChannelReadComplete();
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelWritabilityChanged() {
        head.invokeChannelWritabilityChanged();
        return this;
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public final ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public final ChannelFuture close() {
        return tail.close();
    }

    @Override
    public final ChannelFuture register() {
        return tail.register();
    }

    @Override
    public final ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public final ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public final ChannelFuture connect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public final ChannelFuture register(final ChannelPromise promise) {
        return tail.register(promise);
    }

    @Override
    public final ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }

    @Override
    public final ChannelPipeline read() {
        tail.read();
        return this;
    }

    @Override
    public final ChannelFuture write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public final ChannelFuture write(Object msg, ChannelPromise promise) {
        return tail.write(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return tail.writeAndFlush(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }

    @Override
    public final ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public final ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel(), executor());
    }

    @Override
    public final ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel(), executor(), cause);
    }

    @Override
    public final ChannelPromise voidPromise() {
        return voidPromise;
    }

    /**
     * Called once a {@link Throwable} hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelHandler#exceptionCaught(ChannelHandlerContext, Throwable)}.
     */
    protected void onUnhandledInboundException(Throwable cause) {
        try {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.",
                    cause);
        } finally {
            ReferenceCountUtil.release(cause);
        }
    }

    /**
     * Called once the {@link ChannelInboundHandler#channelActive(ChannelHandlerContext)}event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelActive() {
    }

    /**
     * Called once the {@link ChannelInboundHandler#channelInactive(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelInactive() {
    }

    /**
     * Called once a message hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}. This method is responsible
     * to call {@link ReferenceCountUtil#release(Object)} on the given msg at some point.
     */
    protected void onUnhandledInboundMessage(Object msg) {
        try {
            logger.debug(
                    "Discarded inbound message {} that reached at the tail of the pipeline. " +
                            "Please check your pipeline configuration.", msg);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * Called once the {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelReadComplete() {
    }

    /**
     * Called once an user event hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}. This method is responsible
     * to call {@link ReferenceCountUtil#release(Object)} on the given event at some point.
     */
    protected void onUnhandledInboundUserEventTriggered(Object evt) {
        // This may not be a configuration error and so don't log anything.
        // The event may be superfluous for the current pipeline configuration.
        ReferenceCountUtil.release(evt);
    }

    /**
     * Called once the {@link ChannelInboundHandler#channelWritabilityChanged(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledChannelWritabilityChanged() {
    }

    @UnstableApi
    protected void incrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.incrementPendingOutboundBytes(size);
        }
    }

    @UnstableApi
    protected void decrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.decrementPendingOutboundBytes(size);
        }
    }

    // A special catch-all handler that handles both bytes and messages.
    final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {

        TailContext(DefaultChannelPipeline pipeline) {
            super(pipeline, TAIL_NAME, TailContext.class);
            setAddComplete();
        }

        @Override
        public EventExecutor executor() {
            return pipeline().executor();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            onUnhandledInboundChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            onUnhandledInboundChannelInactive();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            onUnhandledChannelWritabilityChanged();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            onUnhandledInboundUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            onUnhandledInboundException(cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            onUnhandledInboundMessage(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            onUnhandledInboundChannelReadComplete();
        }
    }

    final class HeadContext extends AbstractChannelHandlerContext
            implements ChannelOutboundHandler, ChannelInboundHandler {

        private final Unsafe unsafe;

        HeadContext(DefaultChannelPipeline pipeline) {
            super(pipeline, HEAD_NAME, HeadContext.class);
            unsafe = pipeline.channel().unsafe();
            setAddComplete();
        }

        @Override
        public EventExecutor executor() {
            return pipeline().executor();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            unsafe.bind(localAddress, promise);
        }

        @Override
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.close(promise);
        }

        @Override
        public void register(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.register(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.deregister(promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            unsafe.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            unsafe.flush();
        }

        @Skip
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }

        @Skip
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelUnregistered();

            // Remove all handlers sequentially if channel is closed and unregistered.
            if (!channel.isOpen()) {
                destroy();
            }
        }

        @Skip
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
        }

        @Skip
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelInactive();
        }

        @Skip
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg);
        }

        @Skip
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelReadComplete();
        }

        @Skip
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            ctx.fireUserEventTriggered(evt);
        }

        @Skip
        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelWritabilityChanged();
        }
    }
}

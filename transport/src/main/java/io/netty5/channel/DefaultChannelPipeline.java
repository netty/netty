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
package io.netty5.channel;

import io.netty5.util.Resource;
import io.netty5.util.ResourceLeakDetector;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static io.netty5.channel.DefaultChannelHandlerContext.safeExecute;

/**
 * The default {@link ChannelPipeline} implementation.  It is usually created
 * by a {@link Channel} implementation when the {@link Channel} is created.
 */
public abstract class DefaultChannelPipeline implements ChannelPipeline {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);
    private static final String HEAD_NAME = generateName0(HeadHandler.class);
    private static final String TAIL_NAME = generateName0(TailHandler.class);

    private static final ChannelHandler HEAD_HANDLER = new HeadHandler();
    private static final ChannelHandler TAIL_HANDLER = new TailHandler();

    private static final FastThreadLocal<Map<Class<?>, String>> nameCaches =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<?>, String> initialValue() {
                    return new WeakHashMap<>();
                }
            };

    private static final AtomicReferenceFieldUpdater<DefaultChannelPipeline, MessageSizeEstimator.Handle> ESTIMATOR =
            AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelPipeline.class, MessageSizeEstimator.Handle.class, "estimatorHandle");
    private final DefaultChannelHandlerContext head;
    private final DefaultChannelHandlerContext tail;

    private final Channel channel;
    private final Future<Void> succeededFuture;
    private final boolean touch = ResourceLeakDetector.isEnabled();
    private final List<DefaultChannelHandlerContext> handlers = new ArrayList<>(4);

    private volatile MessageSizeEstimator.Handle estimatorHandle;

    private static final AtomicLongFieldUpdater<DefaultChannelPipeline> TOTAL_PENDING_OUTBOUND_BYTES_UPDATER =
            AtomicLongFieldUpdater.newUpdater(DefaultChannelPipeline.class, "pendingOutboundBytes");

    private volatile long pendingOutboundBytes;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = requireNonNull(channel, "channel");
        succeededFuture = channel.executor().newSucceededFuture(null);

        tail = new DefaultChannelHandlerContext(this, TAIL_NAME, TAIL_HANDLER);
        head = new DefaultChannelHandlerContext(this, HEAD_NAME, HEAD_HANDLER);

        head.next = tail;
        tail.prev = head;
        head.setAddComplete();
        tail.setAddComplete();
    }

    final MessageSizeEstimator.Handle estimatorHandle() {
        MessageSizeEstimator.Handle handle = estimatorHandle;
        if (handle == null) {
            handle = channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR).newHandle();
            if (!ESTIMATOR.compareAndSet(this, null, handle)) {
                handle = estimatorHandle;
            }
        }
        return handle;
    }

    final Object touch(Object msg, DefaultChannelHandlerContext next) {
        if (touch) {
            Resource.touch(msg, next);
        }
        return msg;
    }

    private DefaultChannelHandlerContext newContext(String name, ChannelHandler handler) {
        checkMultiplicity(handler);
        if (name == null) {
            name = generateName(handler);
        }
        return new DefaultChannelHandlerContext(this, name, handler);
    }

    private void checkDuplicateName(String name) {
        if (context(name) != null) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    private static int checkNoSuchElement(int idx, String name) {
        if (idx == -1) {
            throw new NoSuchElementException(name);
        }
        return idx;
    }

    @Override
    public final Channel channel() {
        return channel;
    }

    @Override
    public final EventExecutor executor() {
        return channel().executor();
    }

    @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        DefaultChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            checkDuplicateName(newCtx.name());

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

    private void addFirst0(DefaultChannelHandlerContext newCtx) {
        DefaultChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        DefaultChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            checkDuplicateName(newCtx.name());

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

    private void addLast0(DefaultChannelHandlerContext newCtx) {
        DefaultChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext ctx;

        DefaultChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int i = checkNoSuchElement(findCtxIdx(context -> context.name().equals(baseName)), baseName);
            checkDuplicateName(newCtx.name());

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

    private void addBefore0(DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
        newCtx.prev = ctx.prev;
        newCtx.next = ctx;
        ctx.prev.next = newCtx;
        ctx.prev = newCtx;
        callHandlerAdded0(newCtx);
    }

    @Override
    public final ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext ctx;

        if (name == null) {
            name = generateName(handler);
        }

        DefaultChannelHandlerContext newCtx = newContext(name, handler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int i = checkNoSuchElement(findCtxIdx(context -> context.name().equals(baseName)), baseName);

            checkDuplicateName(newCtx.name());

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

    private void addAfter0(DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
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
        requireNonNull(handlers, "handlers");

        for (int i = handlers.length - 1; i >= 0; i--) {
            ChannelHandler h = handlers[i];
            if (h != null) {
                addFirst(null, h);
            }
        }

        return this;
    }

    public final ChannelPipeline addLast(ChannelHandler handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        requireNonNull(handlers, "handlers");

        for (ChannelHandler h : handlers) {
            if (h != null) {
                addLast(null, h);
            }
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

    private int findCtxIdx(Predicate<DefaultChannelHandlerContext> predicate) {
        for (int i = 0; i < handlers.size(); i++) {
            if (predicate.test(handlers.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public final ChannelPipeline remove(ChannelHandler handler) {
        final DefaultChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = checkNoSuchElement(findCtxIdx(context -> context.handler() == handler), null);
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                scheduleRemove(idx, ctx);
                return this;
            }
        }

        remove0(ctx);
        return this;
    }

    @Override
    public final ChannelHandler remove(String name) {
        final DefaultChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = checkNoSuchElement(findCtxIdx(context -> context.name().equals(name)), name);
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                return scheduleRemove(idx, ctx);
            }
        }

        remove0(ctx);
        return ctx.handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T remove(Class<T> handlerType) {
        final DefaultChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = checkNoSuchElement(findCtxIdx(
                    context -> handlerType.isAssignableFrom(context.handler().getClass())), null);
            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                return scheduleRemove(idx, ctx);
            }
        }

        remove0(ctx);
        return (T) ctx.handler();
    }

    @Override
    public final <T extends ChannelHandler> T removeIfExists(String name) {
        return removeIfExists(() -> findCtxIdx(context -> name.equals(context.name())));
    }

    @Override
    public final <T extends ChannelHandler> T removeIfExists(Class<T> handlerType) {
        return removeIfExists(() -> findCtxIdx(
                context -> handlerType.isAssignableFrom(context.handler().getClass())));
    }

    @Override
    public final <T extends ChannelHandler> T removeIfExists(ChannelHandler handler) {
        return removeIfExists(() -> findCtxIdx(context -> handler == context.handler()));
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelHandler> T removeIfExists(IntSupplier idxSupplier) {
        final DefaultChannelHandlerContext ctx;
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
                return scheduleRemove(idx, ctx);
            }
        }
        remove0(ctx);
        return (T) ctx.handler();
    }

    private void remove0(DefaultChannelHandlerContext ctx) {
        try {
            callHandlerRemoved0(ctx);
        } finally {
            ctx.remove(true);
        }
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
            Predicate<DefaultChannelHandlerContext> predicate, String newName, ChannelHandler newHandler) {
        DefaultChannelHandlerContext oldCtx;
        DefaultChannelHandlerContext newCtx = newContext(newName, newHandler);
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            int idx = checkNoSuchElement(findCtxIdx(predicate), null);
            oldCtx = handlers.get(idx);
            assert oldCtx != head && oldCtx != tail && oldCtx != null;

            if (!oldCtx.name().equals(newCtx.name())) {
                checkDuplicateName(newCtx.name());
            }
            DefaultChannelHandlerContext removed = handlers.set(idx, newCtx);
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

    private void replace0(DefaultChannelHandlerContext oldCtx, DefaultChannelHandlerContext newCtx) {
        DefaultChannelHandlerContext prev = oldCtx.prev;
        DefaultChannelHandlerContext next = oldCtx.next;
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

        try {
            // Invoke newHandler.handlerAdded() first (i.e. before oldHandler.handlerRemoved() is invoked)
            // because callHandlerRemoved() will trigger channelRead() or flush() on newHandler and those
            // event handlers must be called after handlerAdded().
            callHandlerAdded0(newCtx);
            callHandlerRemoved0(oldCtx);
        } finally {
            oldCtx.remove(false);
        }
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

    private void callHandlerAdded0(final DefaultChannelHandlerContext ctx) {
        try {
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                synchronized (handlers) {
                    handlers.remove(ctx);
                }

                ctx.callHandlerRemoved();

                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            } finally {
                ctx.remove(true);
            }

            if (removed) {
                fireChannelExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireChannelExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    private void callHandlerRemoved0(final DefaultChannelHandlerContext ctx) {
        // Notify the complete removal.
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireChannelExceptionCaught(new ChannelPipelineException(
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

    private DefaultChannelHandlerContext findCtx(Predicate<DefaultChannelHandlerContext> predicate) {
        for (int i = 0; i < handlers.size(); i++) {
            DefaultChannelHandlerContext ctx = handlers.get(i);
            if (predicate.test(ctx)) {
                return ctx;
            }
        }
        return null;
    }

    @Override
    public final ChannelHandlerContext context(String name) {
        requireNonNull(name, "name");

        synchronized (handlers) {
            return findCtx(ctx -> ctx.name().equals(name));
        }
    }

    @Override
    public final ChannelHandlerContext context(ChannelHandler handler) {
        requireNonNull(handler, "handler");

        synchronized (handlers) {
            return findCtx(ctx -> ctx.handler() == handler);
        }
    }

    @Override
    public final ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        requireNonNull(handlerType, "handlerType");

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
                    DefaultChannelHandlerContext ctx = handlers.get(i);

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
        final DefaultChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            if (handlers.isEmpty()) {
                throw new NoSuchElementException();
            }
            int idx = 0;

            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                return scheduleRemove(idx, ctx);
            }
        }
        remove0(ctx);
        return ctx.handler();
    }

    @Override
    public ChannelHandler removeLast() {
        final DefaultChannelHandlerContext ctx;
        EventExecutor executor = executor();
        boolean inEventLoop = executor.inEventLoop();
        synchronized (handlers) {
            if (handlers.isEmpty()) {
                return null;
            }
            int idx = handlers.size() - 1;

            ctx = handlers.remove(idx);
            assert ctx != null;

            if (!inEventLoop) {
                return scheduleRemove(idx, ctx);
            }
        }
        remove0(ctx);
        return ctx.handler();
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelHandler> T scheduleRemove(int idx, DefaultChannelHandlerContext ctx) {
        try {
            ctx.executor().execute(() -> remove0(ctx));
            return (T) ctx.handler();
        } catch (Throwable cause) {
            handlers.add(idx, ctx);
            throw cause;
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
    public final ChannelPipeline fireChannelShutdown(ChannelShutdownDirection direction) {
        head.invokeChannelShutdown(direction);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelExceptionCaught(Throwable cause) {
        head.invokeChannelExceptionCaught(cause);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelInboundEvent(Object event) {
        head.invokeChannelInboundEvent(event);
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
    public final Future<Void> bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public final Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public final Future<Void> disconnect() {
        return tail.disconnect();
    }

    @Override
    public final Future<Void> close() {
        return tail.close();
    }

    @Override
    public Future<Void> shutdown(ChannelShutdownDirection direction) {
        return tail.shutdown(direction);
    }

    @Override
    public final Future<Void> register() {
        return tail.register();
    }

    @Override
    public final Future<Void> deregister() {
        return tail.deregister();
    }

    @Override
    public final ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    @Override
    public final ChannelPipeline read() {
        tail.read();
        return this;
    }

    @Override
    public final Future<Void> write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public final Future<Void> writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }

    @Override
    public final Future<Void> sendOutboundEvent(Object event) {
        return tail.sendOutboundEvent(event);
    }

    @Override
    public final <V> Promise<V> newPromise() {
        return ChannelPipeline.super.newPromise();
    }

    @Override
    public final Future<Void> newSucceededFuture() {
        return succeededFuture;
    }
    @Override
    public final <V> Future<V> newSucceededFuture(V value) {
        return ChannelPipeline.super.newSucceededFuture(value);
    }

    @Override
    public final <V> Future<V> newFailedFuture(Throwable cause) {
        return ChannelPipeline.super.newFailedFuture(cause);
    }

    /**
     * Called once a {@link Throwable} hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelHandler#channelExceptionCaught(ChannelHandlerContext, Throwable)}.
     */
    protected void onUnhandledInboundException(Throwable cause) {
        try {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.",
                    cause);
        } finally {
            Resource.dispose(cause);
        }
    }

    /**
     * Called once the {@link ChannelHandler#channelActive(ChannelHandlerContext)}event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelActive() {
    }

    /**
     * Called once the {@link ChannelHandler#channelInactive(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelInactive() {
    }

    /**
     * Called once the {@link ChannelHandler#channelShutdown(ChannelHandlerContext, ChannelShutdownDirection)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelShutdown(ChannelShutdownDirection direction) {
    }

    /**
     * Called once a message hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelHandler#channelRead(ChannelHandlerContext, Object)}. This method is responsible
     * to call {@link Resource#dispose(Object)} on the given msg at some point.
     */
    protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
        try {
            logger.debug(
                    "Discarded inbound message {} that reached at the tail of the pipeline. " +
                            "Please check your pipeline configuration. Discarded message pipeline : {}. Channel : {}.",
                    msg, ctx.pipeline().names(), ctx.channel());
        } finally {
            Resource.dispose(msg);
        }
    }

    /**
     * Called once the {@link ChannelHandler#channelReadComplete(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledInboundChannelReadComplete() {
    }

    /**
     * Called once an user event hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelHandler#channelInboundEvent(ChannelHandlerContext, Object)}. This method is responsible
     * to call {@link Resource#dispose(Object)} on the given event at some point.
     */
    protected void onUnhandledChannelInboundEvent(Object evt) {
        // This may not be a configuration error and so don't log anything.
        // The event may be superfluous for the current pipeline configuration.
        Resource.dispose(evt);
    }

    /**
     * Called once the {@link ChannelHandler#channelWritabilityChanged(ChannelHandlerContext)} event hit
     * the end of the {@link ChannelPipeline}.
     */
    protected void onUnhandledChannelWritabilityChanged() {
    }

    /**
     * Called once the {@link #pendingOutboundBytes()} were changed.
     *
     * @param pendingOutboundBytes          the new {@link #pendingOutboundBytes()}.
     */
    protected abstract void pendingOutboundBytesUpdated(long pendingOutboundBytes);

    @Override
    public final long pendingOutboundBytes() {
        return TOTAL_PENDING_OUTBOUND_BYTES_UPDATER.get(this);
    }

    /**
     * Increments the {@link #pendingOutboundBytes()} of the pipeline by the given delta.
     *
     * @param delta the number of bytes to add.
     */
    final void incrementPendingOutboundBytes(long delta) {
        assert delta > 0;
        long pending = TOTAL_PENDING_OUTBOUND_BYTES_UPDATER.addAndGet(this, delta);
        if (pending < 0) {
            forceCloseTransport();
            throw new IllegalStateException("pendingOutboundBytes overflowed, force closed transport.");
        }
        pendingOutboundBytesUpdated(pending);
    }

    /**
     * Decrements the {@link #pendingOutboundBytes()} of the pipeline by the given delta.
     *
     * @param delta the number of bytes to remove.
     */
    final void decrementPendingOutboundBytes(long delta) {
        assert delta > 0;
        long pending = TOTAL_PENDING_OUTBOUND_BYTES_UPDATER.addAndGet(this, -delta);
        if (pending < 0) {
            forceCloseTransport();
            throw new IllegalStateException("pendingOutboundBytes underflowed, force closed transport.");
        }
        pendingOutboundBytesUpdated(pending);
    }

    private static DefaultChannelPipeline defaultChannelPipeline(ChannelHandlerContext ctx) {
        return (DefaultChannelPipeline) ctx.pipeline();
    }

    // A special catch-all handler that handles both bytes and messages.
    private static final class TailHandler implements ChannelHandler {

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            // Just swallow event
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            // Just swallow event
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            defaultChannelPipeline(ctx).onUnhandledInboundChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            defaultChannelPipeline(ctx).onUnhandledInboundChannelInactive();
        }

        @Override
        public void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) {
            defaultChannelPipeline(ctx).onUnhandledInboundChannelShutdown(direction);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            defaultChannelPipeline(ctx).onUnhandledChannelWritabilityChanged();
        }

        @Override
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            ((DefaultChannelPipeline) ctx.pipeline()).onUnhandledChannelInboundEvent(evt);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            defaultChannelPipeline(ctx).onUnhandledInboundException(cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            defaultChannelPipeline(ctx).onUnhandledInboundMessage(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            defaultChannelPipeline(ctx).onUnhandledInboundChannelReadComplete();
        }
    }

    private static final class HeadHandler implements ChannelHandler {

        @Override
        public Future<Void> bind(
                ChannelHandlerContext ctx, SocketAddress localAddress) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.bindTransport(localAddress, promise);
            } else {
                safeExecute(executor, () -> pipeline.bindTransport(localAddress, promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.connectTransport(remoteAddress, localAddress, promise);
            } else {
                safeExecute(executor, () ->
                        pipeline.connectTransport(remoteAddress, localAddress, promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> disconnect(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.disconnectTransport(promise);
            } else {
                safeExecute(executor, () -> pipeline.disconnectTransport(promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> close(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.closeTransport(promise);
            } else {
                safeExecute(executor, () -> pipeline.closeTransport(promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> shutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.shutdownTransport(direction, promise);
            } else {
                safeExecute(executor, () -> pipeline.shutdownTransport(direction, promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> register(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.registerTransport(promise);
            } else {
                safeExecute(executor, () -> pipeline.registerTransport(promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public Future<Void> deregister(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.deregisterTransport(promise);
            } else {
                safeExecute(executor, () -> pipeline.deregisterTransport(promise), promise, null);
            }
            return promise.asFuture();
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            if (executor.inEventLoop()) {
                pipeline.readTransport();
            } else {
                safeExecute(executor, pipeline::readTransport, null, null);
            }
        }

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.writeTransport(msg, promise);
            } else {
                safeExecute(executor, () -> pipeline.writeTransport(msg, promise), promise, msg);
            }
            return promise.asFuture();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            if (executor.inEventLoop()) {
                pipeline.flushTransport();
            } else {
                safeExecute(executor, pipeline::flushTransport, null, null);
            }
        }

        @Override
        public Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
            DefaultChannelPipeline pipeline = defaultChannelPipeline(ctx);
            EventExecutor executor = pipeline.transportExecutor();
            Promise<Void> promise = executor.newPromise();
            if (executor.inEventLoop()) {
                pipeline.sendOutboundEventTransport(event, promise);
            } else {
                safeExecute(executor, () -> pipeline.sendOutboundEventTransport(event, promise), promise, event);
            }
            return promise.asFuture();
        }
    }

    final void forceCloseTransport() {
        EventExecutor executor = transportExecutor();
        Promise<Void> promise = executor.newPromise();
        if (executor.inEventLoop()) {
            closeTransport(promise);
        } else {
            safeExecute(executor, () -> closeTransport(promise), promise, null);
        }
    }

    /**
     * Returns the {@link EventExecutor} that is used for all the abstract transport operations.
     *
     * @return executor.
     */
    protected abstract EventExecutor transportExecutor();

    /**
     * Register the transport and notify the {@link Promise} once the operation was completed. This method is
     * guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void registerTransport(Promise<Void> promise);

    /**
     * Bind the {@link SocketAddress} to the transport and notify the {@link Promise} once the operation was completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void bindTransport(SocketAddress localAddress, Promise<Void> promise);

    /**
     * Connect the transport with the given remote {@link SocketAddress}. If a specific local {@link SocketAddress}
     * should be used it needs to be given as argument. Otherwise just pass {@code null} to it.
     *
     * The {@link Promise} will get notified once the operation was completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void connectTransport(
            SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise);

    /**
     * Disconnect the transport and notify the {@link Promise} once the operation was completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void disconnectTransport(Promise<Void> promise);

    /**
     * Close the transport and notify the {@link Promise} once the operation was completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void closeTransport(Promise<Void> promise);

    /**
     * Shutdown the given direction of the transport and notify the {@link Promise} once the operation was completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void shutdownTransport(ChannelShutdownDirection direction, Promise<Void> promise);

    /**
     * Deregister the transport of the {@link EventLoop} and notify the {@link Promise} once the operation was
     * completed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void deregisterTransport(Promise<Void> promise);

    /**
     * Schedules a read operation on the transport that fills the inbound buffer of the first {@link ChannelHandler}
     * in the {@link ChannelPipeline}. If there's already a pending read operation, this method does nothing.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void readTransport();

    /**
     * Schedules a write operation on the transport. The given {@link Promise} will be notified once the write was
     * either successful or failed.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void writeTransport(Object msg, Promise<Void> promise);

    /**
     * Flush out all (previous) scheduled write operations, scheduled via {@link #writeTransport(Object, Promise)}.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void flushTransport();

    /**
     * Send a custom outbound event on the transport.
     * This method is guaranteed to be called from the {@link #transportExecutor()}.
     */
    protected abstract void sendOutboundEventTransport(Object event, Promise<Void> promise);
}

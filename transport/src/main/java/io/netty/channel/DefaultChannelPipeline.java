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

import io.netty.channel.Channel.Unsafe;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;

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
        protected Map<Class<?>, String> initialValue() {
            return new WeakHashMap<Class<?>, String>();
        }
    };

    private static final AtomicReferenceFieldUpdater<DefaultChannelPipeline, MessageSizeEstimator.Handle> ESTIMATOR =
            AtomicReferenceFieldUpdater.newUpdater(
                    DefaultChannelPipeline.class, MessageSizeEstimator.Handle.class, "estimatorHandle");
    final HeadContext head;
    final TailContext tail;

    final StampedLock pipelineLock;
    int[] handlerMasks;
    AbstractChannelHandlerContext[] handlerContexts;

    private final Channel channel;
    private final ChannelFuture succeededFuture;
    private final VoidChannelPromise voidPromise;
    private final boolean touch = ResourceLeakDetector.isEnabled();

    private Map<EventExecutorGroup, EventExecutor> childExecutors;
    private volatile MessageSizeEstimator.Handle estimatorHandle;
    private boolean firstRegistration = true;

    /**
     * This is the head of a linked list that is processed by {@link #callHandlerAddedForAllHandlers()} and so process
     * all the pending {@link #callHandlerAdded0(AbstractChannelHandlerContext)}.
     * <p>
     * We only keep the head because it is expected that the list is used infrequently and its size is small.
     * Thus full iterations to do insertions is assumed to be a good compromised to saving memory and tail management
     * complexity.
     */
    private PendingHandlerCallback pendingHandlerCallbackHead;

    /**
     * Set to {@code true} once the {@link AbstractChannel} is registered.Once set to {@code true} the value will never
     * change.
     */
    private boolean registered;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise = new VoidChannelPromise(channel, true);

        pipelineLock = new StampedLock();
        handlerMasks = new int[2];
        handlerContexts = new AbstractChannelHandlerContext[2];
        tail = new TailContext(this);
        head = new HeadContext(this);
        handlerContexts[0] = head;
        handlerContexts[1] = tail;
        handlerMasks[0] = head.executionMask;
        handlerMasks[1] = tail.executionMask;
        head.pipelineIndex = 0;
        head.handlerMasks = handlerMasks;
        head.handlerContexts = handlerContexts;
        tail.pipelineIndex = 1;
        tail.handlerMasks = handlerMasks;
        tail.handlerContexts = handlerContexts;
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

    private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
    }

    private EventExecutor childExecutor(EventExecutorGroup group) {
        if (group == null) {
            return null;
        }
        Boolean pinEventExecutor = channel.config().getOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP);
        if (pinEventExecutor != null && !pinEventExecutor) {
            return group.next();
        }
        Map<EventExecutorGroup, EventExecutor> childExecutors = this.childExecutors;
        if (childExecutors == null) {
            // Use size of 4 as most people only use one extra EventExecutor.
            childExecutors = this.childExecutors = new IdentityHashMap<EventExecutorGroup, EventExecutor>(4);
        }
        // Pin one of the child executors once and remember it so that the same child executor
        // is used to fire events for the same channel.
        EventExecutor childExecutor = childExecutors.get(group);
        if (childExecutor == null) {
            childExecutor = group.next();
            childExecutors.put(group, childExecutor);
        }
        return childExecutor;
    }
    @Override
    public final Channel channel() {
        return channel;
    }

    @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    private enum AddStrategy {
        ADD_FIRST,
        ADD_LAST,
        ADD_BEFORE,
        ADD_AFTER
    }

    private enum RemoveStrategy {
        REMOVE_FIRST,
        REMOVE_LAST
    }

    private ChannelPipeline internalAdd(EventExecutorGroup group, String name,
                                        ChannelHandler handler, String baseName,
                                        AddStrategy addStrategy) {
        final AbstractChannelHandlerContext newCtx;
        final long stamp = pipelineLock.writeLock();
        try {
            checkMultiplicity(handler);
            name = filterName(name, handler);

            newCtx = newContext(group, name, handler);

            switch (addStrategy) {
                case ADD_FIRST:
                    insertIndex(1, newCtx);
                    break;
                case ADD_LAST:
                    insertIndex(handlerMasks.length - 1, newCtx);
                    break;
                case ADD_BEFORE:
                    insertIndex(indexOfOrDie(baseName), newCtx);
                    break;
                case ADD_AFTER:
                    insertIndex(indexOfOrDie(baseName) + 1, newCtx);
                    break;
                default:
                    throw new IllegalArgumentException("unknown add strategy: " + addStrategy);
            }

            // If the registered is false it means that the channel was not registered on an eventLoop yet.
            // In this case we add the context to the pipeline and add a task that will call
            // ChannelHandler.handlerAdded(...) once the channel is registered.
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        } finally {
            pipelineLock.unlockWrite(stamp);
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        return internalAdd(group, name, handler, null, AddStrategy.ADD_FIRST);
    }

    private void insertIndex(int index, AbstractChannelHandlerContext newCtx) {
        assert pipelineLock.isWriteLocked();
        int newLength = handlerMasks.length + 1;
        handlerMasks = Arrays.copyOf(handlerMasks, newLength);
        handlerContexts = Arrays.copyOf(handlerContexts, newLength);
        System.arraycopy(handlerMasks, index, handlerMasks, index + 1, newLength - index - 1);
        System.arraycopy(handlerContexts, index, handlerContexts, index + 1, newLength - index - 1);
        handlerMasks[index] = newCtx.executionMask;
        handlerContexts[index] = newCtx;
        for (int i = 0; i < handlerContexts.length; i++) {
            handlerContexts[i].pipelineIndex = i;
            handlerContexts[i].handlerMasks = handlerMasks;
            handlerContexts[i].handlerContexts = handlerContexts;
        }
    }

    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        return internalAdd(group, name, handler, null, AddStrategy.ADD_LAST);
    }

    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addBefore(
            EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        return internalAdd(group, name, handler, baseName, AddStrategy.ADD_BEFORE);
    }

    private String filterName(String name, ChannelHandler handler) {
        if (name == null) {
            return generateName(handler);
        }
        checkDuplicateName(name);
        return name;
    }

    @Override
    public final ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addAfter(
            EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        return internalAdd(group, name, handler, baseName, AddStrategy.ADD_AFTER);
    }

    public final ChannelPipeline addFirst(ChannelHandler handler) {
        return addFirst(null, handler);
    }

    @Override
    public final ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");
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
            addFirst(executor, null, h);
        }

        return this;
    }

    public final ChannelPipeline addLast(ChannelHandler handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, null, h);
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

        // It's not very likely for a user to put more than one handler of the same type, but make sure to avoid
        // any name conflicts.  Note that we don't cache the names generated here.
        if (context0(name) != null) {
            String baseName = name.substring(0, name.length() - 1); // Strip the trailing '0'.
            for (int i = 1;; i ++) {
                String newName = baseName + i;
                if (context0(newName) == null) {
                    name = newName;
                    break;
                }
            }
        }
        return name;
    }

    private static String generateName0(Class<?> handlerType) {
        return StringUtil.simpleClassName(handlerType) + "#0";
    }

    @Override
    public final ChannelPipeline remove(ChannelHandler handler) {
        remove(handler, null, null, null, false);
        return this;
    }

    @Override
    public final ChannelHandler remove(String name) {
        return remove(null, null, name, null, false).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(null, handlerType, null, null, false).handler();
    }

    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T removeIfExists(String name) {
        AbstractChannelHandlerContext ctx = remove(null, null, name, null, true);
        return ctx == null ? null : (T) ctx.handler();
    }

    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T removeIfExists(Class<T> handlerType) {
        AbstractChannelHandlerContext ctx = remove(null, handlerType, null, null, true);
        return ctx == null ? null : (T) ctx.handler();
    }

    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T removeIfExists(ChannelHandler handler) {
        AbstractChannelHandlerContext ctx = remove(handler, null, null, null, true);
        return ctx == null ? null : (T) ctx.handler();
    }

    private <T extends ChannelHandler> AbstractChannelHandlerContext remove(
            final ChannelHandler handler, final Class<T> handlerType, final String name, final RemoveStrategy strategy,
            final boolean ignoreMissing) {
        final AbstractChannelHandlerContext ctx;
        final long stamp = pipelineLock.writeLock();
        try {
            if (handler != null) {
                ctx = ignoreMissing ? context0(handler) : getContextOrDie(handler);
            } else if (handlerType != null) {
                ctx = ignoreMissing ? context0(handlerType) : getContextOrDie(handlerType);
            } else if (name != null) {
                ctx = ignoreMissing ? context0(name) : getContextOrDie(name);
            } else if (strategy != null) {
                if (handlerContexts.length == 2) {
                    if (!ignoreMissing) {
                        throw new NoSuchElementException();
                    }
                    return null;
                }
                ctx = strategy == RemoveStrategy.REMOVE_FIRST ? handlerContexts[1] :
                        handlerContexts[handlerContexts.length - 2];
            } else {
                throw new IllegalArgumentException("No context specified");
            }
            if (ctx == null) {
                return null;
            }
            removeIndex(ctx.pipelineIndex);

            // If the registered is false it means that the channel was not registered on an eventloop yet.
            // In this case we remove the context from the pipeline and add a task that will call
            // ChannelHandler.handlerRemoved(...) once the channel is registered.
            if (!registered) {
                callHandlerCallbackLater(ctx, false);
                return ctx;
            }

            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop()) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerRemoved0(ctx);
                    }
                });
                return ctx;
            }
        } finally {
            pipelineLock.unlockWrite(stamp);
        }
        callHandlerRemoved0(ctx);
        return ctx;
    }

    private void removeIndex(int index) {
        assert pipelineLock.isWriteLocked();
        assert handlerMasks.length > 2;
        int newLength = handlerMasks.length - 1;
        int[] oldMasks = handlerMasks;
        AbstractChannelHandlerContext[] oldContexts = handlerContexts;
        handlerMasks = Arrays.copyOf(handlerMasks, newLength);
        handlerContexts = Arrays.copyOf(handlerContexts, newLength);
        System.arraycopy(oldMasks, index + 1, handlerMasks, index, newLength - index);
        System.arraycopy(oldContexts, index + 1, handlerContexts, index, newLength - index);
        for (int i = 0; i < newLength; i++) {
            handlerContexts[i].pipelineIndex = i;
            handlerContexts[i].handlerMasks = handlerMasks;
            handlerContexts[i].handlerContexts = handlerContexts;
        }
    }

    @Override
    public final ChannelHandler removeFirst() {
        return remove(null, null, null, RemoveStrategy.REMOVE_FIRST, false).handler();
    }

    @Override
    public final ChannelHandler removeLast() {
        return remove(null, null, null, RemoveStrategy.REMOVE_LAST, false).handler();
    }

    @Override
    public final ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(oldHandler, null, null, newName, newHandler);
        return this;
    }

    @Override
    public final ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(null, null, oldName, newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(null, oldHandlerType, null, newName, newHandler);
    }

    private <T extends ChannelHandler> ChannelHandler replace(
            final ChannelHandler oldHandler, final Class<T> oldHandlerType, final String oldName,
            String newName, ChannelHandler newHandler) {
        final AbstractChannelHandlerContext oldCtx;
        final AbstractChannelHandlerContext newCtx;
        long stamp = pipelineLock.writeLock();
        try {
            if (oldHandler != null) {
                oldCtx = getContextOrDie(oldHandler);
            } else if (oldHandlerType != null) {
                oldCtx = getContextOrDie(oldHandlerType);
            } else if (oldName != null) {
                oldCtx = getContextOrDie(oldName);
            } else {
                throw new IllegalArgumentException("No context specified");
            }

            checkMultiplicity(newHandler);
            if (newName == null) {
                newName = generateName(newHandler);
            } else {
                boolean sameName = oldCtx.name().equals(newName);
                if (!sameName) {
                    checkDuplicateName(newName);
                }
            }

            newCtx = newContext(oldCtx.childExecutor, newName, newHandler);

            replaceIndex(oldCtx, newCtx);

            // If the registered is false it means that the channel was not registered on an eventloop yet.
            // In this case we replace the context in the pipeline
            // and add a task that will call ChannelHandler.handlerAdded(...) and
            // ChannelHandler.handlerRemoved(...) once the channel is registered.
            if (!registered) {
                callHandlerCallbackLater(newCtx, true);
                callHandlerCallbackLater(oldCtx, false);
                return oldCtx.handler();
            }
            EventExecutor executor = oldCtx.executor();
            if (!executor.inEventLoop()) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // Invoke newHandler.handlerAdded() first (i.e. before oldHandler.handlerRemoved() is invoked)
                        // because callHandlerRemoved() will trigger channelRead() or flush() on newHandler and
                        // those event handlers must be called after handlerAdded().
                        callHandlerAdded0(newCtx);
                        callHandlerRemoved0(oldCtx);
                    }
                });
                return oldCtx.handler();
            }
        } finally {
            pipelineLock.unlockWrite(stamp);
        }
        // Invoke newHandler.handlerAdded() first (i.e. before oldHandler.handlerRemoved() is invoked)
        // because callHandlerRemoved() will trigger channelRead() or flush() on newHandler and those
        // event handlers must be called after handlerAdded().
        callHandlerAdded0(newCtx);
        callHandlerRemoved0(oldCtx);
        return oldCtx.handler();
    }

    private void replaceIndex(AbstractChannelHandlerContext oldCtx, AbstractChannelHandlerContext newCtx) {
        assert pipelineLock.isWriteLocked();
        int index = oldCtx.pipelineIndex;
        // The arrays are copy-on-write
        int[] newMasks = Arrays.copyOf(handlerMasks, handlerMasks.length);
        AbstractChannelHandlerContext[]newContexts = Arrays.copyOf(handlerContexts, handlerContexts.length);
        newMasks[index] = newCtx.executionMask;
        newContexts[index] = newCtx;
        newCtx.pipelineIndex = index;
        handlerMasks = newMasks;
        handlerContexts = newContexts;
        for (AbstractChannelHandlerContext ctx : newContexts) {
            ctx.handlerMasks = newMasks;
            ctx.handlerContexts = newContexts;
        }

        // Bracket the replaced context so it can forward messages into the new pipeline:
        int[] bracketMasks = Arrays.copyOf(handlerMasks, handlerMasks.length + 2);
        AbstractChannelHandlerContext[] bracketContexts = Arrays.copyOf(handlerContexts, handlerContexts.length + 2);
        System.arraycopy(bracketMasks, index, bracketMasks, index + 2, bracketMasks.length - index - 2);
        System.arraycopy(bracketContexts, index, bracketContexts, index + 2, bracketContexts.length - index - 2);
        bracketMasks[index] = newCtx.executionMask;
        bracketMasks[index + 1] = oldCtx.executionMask;
        bracketMasks[index + 2] = newCtx.executionMask;
        bracketContexts[index] = newCtx;
        bracketContexts[index + 1] = oldCtx;
        bracketContexts[index + 2] = newCtx;
        oldCtx.handlerMasks = bracketMasks;
        oldCtx.handlerContexts = bracketContexts;
        oldCtx.pipelineIndex += 1;
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
                final long stamp = pipelineLock.writeLock();
                try {
                    removeIndex(ctx.pipelineIndex);
                } finally {
                    pipelineLock.unlockWrite(stamp);
                }
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

    final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop();
        if (firstRegistration) {
            firstRegistration = false;
            // We are now registered to the EventLoop. It's time to call the callbacks for the ChannelHandlers,
            // that were added before the registration was done.
            callHandlerAddedForAllHandlers();
        }
    }

    @Override
    public final ChannelHandler first() {
        ChannelHandlerContext first = firstContext();
        if (first == null) {
            return null;
        }
        return first.handler();
    }

    @Override
    public final ChannelHandlerContext firstContext() {
        AbstractChannelHandlerContext first;
        StampedLock lock = pipelineLock;
        long stamp = lock.tryOptimisticRead();
        first = handlerContexts[1];
        if (lock.validate(stamp)) {
            if (first == tail) {
                return null;
            }
            return first;
        }
        stamp = lock.readLock();
        try {
            first = handlerContexts[1];
            if (first == tail) {
                return null;
            }
            return first;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public final ChannelHandler last() {
        ChannelHandlerContext last = lastContext();
        if (last == null) {
            return null;
        }
        return last.handler();
    }

    @Override
    public final ChannelHandlerContext lastContext() {
        AbstractChannelHandlerContext last;
        StampedLock lock = pipelineLock;
        long stamp = lock.tryOptimisticRead();
        last = handlerContexts[handlerContexts.length - 2];
        if (lock.validate(stamp)) {
            if (last == head) {
                return null;
            }
            return last;
        }
        stamp = lock.readLock();
        try {
            last = handlerContexts[handlerContexts.length - 2];
            if (last == head) {
                return null;
            }
            return last;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public final ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public final ChannelHandlerContext context(String name) {
        return context0(ObjectUtil.checkNotNull(name, "name"));
    }

    @Override
    public final ChannelHandlerContext context(ChannelHandler handler) {
        ObjectUtil.checkNotNull(handler, "handler");

        StampedLock lock = pipelineLock;
        long stamp = lock.tryOptimisticRead();
        AbstractChannelHandlerContext ctx = context0(handler);
        if (lock.validate(stamp)) {
            return ctx;
        }
        stamp = lock.readLock();
        try {
            return context0(handler);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private AbstractChannelHandlerContext context0(ChannelHandler handler) {
        for (AbstractChannelHandlerContext ctx : handlerContexts) {
            if (ctx.handler() == handler) {
                return ctx;
            }
        }
        return null;
    }

    @Override
    public final ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        ObjectUtil.checkNotNull(handlerType, "handlerType");

        StampedLock lock = pipelineLock;
        long stamp = lock.tryOptimisticRead();
        AbstractChannelHandlerContext ctx = context0(handlerType);
        if (lock.validate(stamp)) {
            return ctx;
        }
        stamp = lock.readLock();
        try {
            return context0(handlerType);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private AbstractChannelHandlerContext context0(Class<? extends ChannelHandler> handlerType) {
        for (AbstractChannelHandlerContext ctx : handlerContexts) {
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }
        }
        return null;
    }

    @Override
    public final List<String> names() {
        final long stamp = pipelineLock.readLock();
        try {
            AbstractChannelHandlerContext[] ctxs = handlerContexts;
            List<String> list = new ArrayList<String>(ctxs.length);
            for (AbstractChannelHandlerContext ctx : ctxs) {
                if (ctx == head || ctx == tail) {
                    continue;
                }
                list.add(ctx.name());
            }
            return list;
        } finally {
            pipelineLock.unlockRead(stamp);
        }
    }

    @Override
    public final Map<String, ChannelHandler> toMap() {
        final long stamp = pipelineLock.readLock();
        try {
            AbstractChannelHandlerContext[] ctxs = handlerContexts;
            Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>((int) (ctxs.length * 1.25));
            for (AbstractChannelHandlerContext ctx : ctxs) {
                if (ctx == head || ctx == tail) {
                    continue;
                }
                map.put(ctx.name(), ctx.handler());
            }
            return map;
        } finally {
            pipelineLock.unlockRead(stamp);
        }
    }

    @Override
    public final Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }

    /**
     * Returns the {@link String} representation of this pipeline.
     */
    @Override
    public final String toString() {
        // NOTE: In toString we iterate without locking, because it might get called by debuggers while lock is held!
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('{');
        int headerLen = buf.length();

        AbstractChannelHandlerContext[] ctxs = handlerContexts; // Should be stable, as we mutate with copy-on-write.
        for (AbstractChannelHandlerContext ctx : ctxs) {
            if (ctx == head || ctx == tail) {
                continue;
            }
            buf.append('(')
                    .append(ctx.name())
                    .append(" = ")
                    .append(ctx.handler().getClass().getName())
                    .append(')');
            buf.append(", ");
        }
        if (buf.length() > headerLen) {
            buf.setLength(buf.length() - 2); // Remove last ", ".
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public final ChannelPipeline fireChannelRegistered() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelRegistered(head);
            } else {
                head.fireChannelRegistered();
            }
        } else {
            head.executor().execute(this::fireChannelRegistered);
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelUnregistered() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelUnregistered(head);
            } else {
                head.fireChannelUnregistered();
            }
        } else {
            head.executor().execute(this::fireChannelUnregistered);
        }
        return this;
    }

    /**
     * Removes all handlers from the pipeline one by one from tail (exclusive) to head (exclusive) to trigger
     * handlerRemoved().
     * <p>
     * Note that we remove the handlers from the tail-end toward the head, and call handlerRemoved after each
     * so that all events are handled.
     * <p>
     * See: https://github.com/netty/netty/issues/3156
     */
    private void destroy() {
        // It goes like this:
        // 1. Find the last non-tail context (if there are none, we're done)
        // 2. If we're in the event loop of that context:
        // 2.1 Remove it
        // 2.2 Schedule a task to call handlerRemoved(), followed by destroy().
        // 3. If we're not in the event loop of the last non-tail task, schedule destroy() on that event loop.
        final long stamp = pipelineLock.writeLock();
        try {
            AbstractChannelHandlerContext lastNonTail = handlerContexts[handlerContexts.length - 2];
            if (lastNonTail == head) {
                return; // We're done.
            }
            final EventExecutor executor = lastNonTail.executor();
            if (executor.inEventLoop(Thread.currentThread())) {
                removeIndex(lastNonTail.pipelineIndex);
                executor.execute(() -> {
                    callHandlerRemoved0(lastNonTail);
                    destroy();
                });
            } else {
                executor.execute(this::destroy);
            }
        } finally {
            pipelineLock.unlockWrite(stamp);
        }
    }

    @Override
    public final ChannelPipeline fireChannelActive() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelActive(head);
            } else {
                head.fireChannelActive();
            }
        } else {
            head.executor().execute(this::fireChannelActive);
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelInactive() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelInactive(head);
            } else {
                head.fireChannelInactive();
            }
        } else {
            head.executor().execute(this::fireChannelInactive);
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireExceptionCaught(Throwable cause) {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.exceptionCaught(head, cause);
            } else {
                head.fireExceptionCaught(cause);
            }
        } else {
            head.executor().execute(() -> fireExceptionCaught(cause));
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireUserEventTriggered(Object event) {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.userEventTriggered(head, event);
            } else {
                head.fireUserEventTriggered(event);
            }
        } else {
            head.executor().execute(() -> fireUserEventTriggered(event));
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelRead(Object msg) {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelRead(head, msg);
            } else {
                head.fireChannelRead(msg);
            }
        } else {
            head.executor().execute(() -> fireChannelRead(msg));
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelReadComplete() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelReadComplete(head);
            } else {
                head.fireChannelReadComplete();
            }
        } else {
            head.executor().execute(this::fireChannelReadComplete);
        }
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelWritabilityChanged() {
        if (head.executor().inEventLoop()) {
            if (head.invokeHandler()) {
                head.channelWritabilityChanged(head);
            } else {
                head.fireChannelWritabilityChanged();
            }
        } else {
            head.executor().execute(this::fireChannelWritabilityChanged);
        }
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
    public final ChannelFuture deregister() {
        try {
            return tail.deregister();
        } finally {
            final long stamp = pipelineLock.readLock(); // Read lock protects the context arrays while we iterate.
            try {
                for (AbstractChannelHandlerContext ctx : handlerContexts) {
                    ctx.contextExecutor = null; // Clear cached executors in case channel gets re-registered.
                }
            } finally {
                pipelineLock.unlockRead(stamp);
            }
        }
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
        return new DefaultChannelPromise(channel);
    }

    @Override
    public final ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel);
    }

    @Override
    public final ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel, null, cause);
    }

    @Override
    public final ChannelPromise voidPromise() {
        return voidPromise;
    }

    private void checkDuplicateName(String name) {
        if (context0(name) != null) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    private AbstractChannelHandlerContext context0(String name) {
        for (AbstractChannelHandlerContext ctx : handlerContexts) {
            if (ctx == head || ctx == tail) {
                continue;
            }
            if (ctx.name().equals(name)) {
                return ctx;
            }
        }
        return null;
    }

    private int indexOfOrDie(String name) {
        AbstractChannelHandlerContext ctx = context0(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx.pipelineIndex;
        }
    }

    private AbstractChannelHandlerContext getContextOrDie(String name) {
        AbstractChannelHandlerContext ctx = context0(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private AbstractChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        AbstractChannelHandlerContext ctx = context0(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private AbstractChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        AbstractChannelHandlerContext ctx = context0(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    private void callHandlerAddedForAllHandlers() {
        final PendingHandlerCallback pendingHandlerCallbackHead;
        final long stamp = pipelineLock.writeLock();
        try {
            assert !registered;

            // This Channel itself was registered.
            registered = true;

            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            // Null out so it can be GC'ed.
            this.pendingHandlerCallbackHead = null;
        } finally {
            pipelineLock.unlockWrite(stamp);
        }

        // This must happen outside of the synchronized(...) block as otherwise handlerAdded(...) may be called while
        // holding the lock and so produce a deadlock if handlerAdded(...) will try to add another handler from outside
        // the EventLoop.
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }

    private void callHandlerCallbackLater(AbstractChannelHandlerContext ctx, boolean added) {
        assert !registered;
        assert pipelineLock.isWriteLocked();

        PendingHandlerCallback task = added ? new PendingHandlerAddedTask(ctx) : new PendingHandlerRemovedTask(ctx);
        PendingHandlerCallback pending = pendingHandlerCallbackHead;
        if (pending == null) {
            pendingHandlerCallbackHead = task;
        } else {
            // Find the tail of the linked-list.
            while (pending.next != null) {
                pending = pending.next;
            }
            pending.next = task;
        }
    }

    private void callHandlerAddedInEventLoop(final AbstractChannelHandlerContext newCtx, EventExecutor executor) {
        newCtx.setAddPending();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callHandlerAdded0(newCtx);
            }
        });
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
     * Called once a message hit the end of the {@link ChannelPipeline} without been handled by the user
     * in {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}. This method is responsible
     * to call {@link ReferenceCountUtil#release(Object)} on the given msg at some point.
     */
    protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
        onUnhandledInboundMessage(msg);
        if (logger.isDebugEnabled()) {
            logger.debug("Discarded message pipeline : {}. Channel : {}.",
                         ctx.pipeline().names(), ctx.channel());
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

    protected void incrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.incrementPendingOutboundBytes(size);
        }
    }

    protected void decrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.decrementPendingOutboundBytes(size);
        }
    }

    // A special catch-all handler that handles both bytes and messages.
    final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {

        TailContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, TAIL_NAME, TailContext.class);
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) { }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) { }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelInactive();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            onUnhandledChannelWritabilityChanged();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) { }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) { }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            onUnhandledInboundUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            onUnhandledInboundException(cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            onUnhandledInboundMessage(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelReadComplete();
        }
    }

    final class HeadContext extends AbstractChannelHandlerContext
            implements ChannelOutboundHandler, ChannelInboundHandler {

        private final Unsafe unsafe;

        HeadContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, HEAD_NAME, HeadContext.class);
            unsafe = pipeline.channel().unsafe();
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            // NOOP
        }

        @Override
        public void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
            unsafe.bind(localAddress, promise);
        }

        @Override
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.close(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.deregister(promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            unsafe.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            unsafe.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            invokeHandlerAddedIfNeeded();
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();

            // Remove all handlers sequentially if channel is closed and unregistered.
            if (!channel.isOpen()) {
                destroy();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            readIfIsAutoRead();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.fireChannelReadComplete();

            readIfIsAutoRead();
        }

        private void readIfIsAutoRead() {
            if (channel.config().isAutoRead()) {
                channel.read();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            ctx.fireChannelWritabilityChanged();
        }
    }

    private abstract static class PendingHandlerCallback implements Runnable {
        final AbstractChannelHandlerContext ctx;
        PendingHandlerCallback next;

        PendingHandlerCallback(AbstractChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        abstract void execute();
    }

    private final class PendingHandlerAddedTask extends PendingHandlerCallback {

        PendingHandlerAddedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerAdded0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                callHandlerAdded0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerAdded() as the EventExecutor {} rejected it, removing handler {}.",
                                executor, ctx.name(), e);
                    }
                    final long stamp = pipelineLock.writeLock();
                    try {
                        removeIndex(ctx.pipelineIndex);
                    } finally {
                        pipelineLock.unlockWrite(stamp);
                    }
                    ctx.setRemoved();
                }
            }
        }
    }

    private final class PendingHandlerRemovedTask extends PendingHandlerCallback {

        PendingHandlerRemovedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerRemoved0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                callHandlerRemoved0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerRemoved() as the EventExecutor {} rejected it," +
                                        " removing handler {}.", executor, ctx.name(), e);
                    }
                    // remove0(...) was call before so just call AbstractChannelHandlerContext.setRemoved().
                    ctx.setRemoved();
                }
            }
        }
    }
}

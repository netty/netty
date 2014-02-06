/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.Signal;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.concurrent.TimeUnit.*;


public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultPromise.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<DefaultPromise> PROGRESSIVE_SIZE_UPDATER;

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> ENTRY_UPDATER;

    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> LISTENER_STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };
    private static final Signal SUCCESS = Signal.valueOf(DefaultPromise.class.getName() + ".SUCCESS");
    private static final Signal UNCANCELLABLE = Signal.valueOf(DefaultPromise.class.getName() + ".UNCANCELLABLE");
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(new CancellationException());
    static {
        CANCELLATION_CAUSE_HOLDER.cause.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);

        @SuppressWarnings("rawtypes")
        AtomicReferenceFieldUpdater<DefaultPromise, Object> entryUpdater =
        PlatformDependent.newAtomicReferenceFieldUpdater(DefaultPromise.class, "entry");
        if (entryUpdater == null) {
            entryUpdater = AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "entry");
        }
        ENTRY_UPDATER = entryUpdater;

        @SuppressWarnings("rawtypes")
        AtomicIntegerFieldUpdater<DefaultPromise> sizeUpdater =
                PlatformDependent.newAtomicIntegerFieldUpdater(DefaultPromise.class, "progressiveSize");
        if (sizeUpdater == null) {
            sizeUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultPromise.class, "progressiveSize");
        }
        PROGRESSIVE_SIZE_UPDATER = sizeUpdater;
    }

    private final EventExecutor executor;

    @SuppressWarnings("unused")
    private volatile Object entry;
    @SuppressWarnings("unused")
    private volatile int progressiveSize;

    // This can either be GenericListener or Entry
    private volatile Object result;
    private Runnable notifierTask;
    private short waiters;

    /**
     * Creates a new instance.
     *
     * It is preferable to use {@link EventExecutor#newPromise()} to create a new promise
     *
     * @param executor
     *        the {@link EventExecutor} which is used to notify the promise once it is complete
     */
    public DefaultPromise(EventExecutor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        this.executor = executor;
    }

    protected DefaultPromise() {
        // only for subclasses
        executor = null;
    }

    protected EventExecutor executor() {
        return executor;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    @Override
    public boolean isCancellable() {
        return result == null;
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    private static boolean isDone0(Object result) {
        return result != null && result != UNCANCELLABLE;
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        if (result == null || result == UNCANCELLABLE) {
            return false;
        }
        return !(result instanceof CauseHolder);
    }

    @Override
    public Throwable cause() {
        Object result = this.result;
        if (result instanceof CauseHolder) {
            return ((CauseHolder) result).cause;
        }
        return null;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (listener instanceof GenericProgressiveFutureListener) {
            PROGRESSIVE_SIZE_UPDATER.incrementAndGet(this);
        }

        Entry newEntry = new Entry(listener);
        if (!ENTRY_UPDATER.compareAndSet(this, null, newEntry)) {
            // was updated in the meantime so try again via CAS
            Object first = entry;
            Entry entry;
            if (!(first instanceof Entry)) {
                Entry replacement = new Entry((GenericFutureListener<?>) first);
                if (ENTRY_UPDATER.compareAndSet(this, first, replacement)) {
                    entry = replacement;
                } else {
                    // was updated in the meantime
                    entry = (Entry) this.entry;
                }
            } else {
                entry = (Entry) first;
            }
            for (;;) {
                if (entry.next == null) {
                    entry = entry.next(newEntry);
                    if (entry == null) {
                        break;
                    }
                }
                entry = entry.next;
            }
        }

        if (!isDone()) {
            return this;
        }
        notifyListeners();
        return this;
    }

    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future<? super V>> l: listeners) {
            if (l == null) {
                break;
            }
            addListener(l);
        }
        return this;
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        Object first = entry;
        if (first == listener) {
            // check if we can remove it directly if this fails it means there was something else added in the meantime
            if (ENTRY_UPDATER.compareAndSet(this, first, null)) {
                if (listener instanceof GenericProgressiveFutureListener) {
                    PROGRESSIVE_SIZE_UPDATER.decrementAndGet(this);
                }
                return this;
            }
        }

        if (first instanceof Entry)  {
            Entry entry = (Entry) first;
            for (;;) {
                if (entry == null || (entry.curr == listener && !entry.isRemoved() && entry.remove())) {
                    break;
                }
                if (entry.curr == listener && !entry.isRemoved()) {
                    if (entry.remove()) {
                        if (listener instanceof GenericProgressiveFutureListener) {
                            PROGRESSIVE_SIZE_UPDATER.decrementAndGet(this);
                        }
                        break;
                    }
                }
                entry = entry.next;
            }
        }
        return this;
    }

    @Override
    public Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future<? super V>> l: listeners) {
            if (l == null) {
                break;
            }
            removeListener(l);
        }
        return this;
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }

        PlatformDependent.throwException(cause);
    }

    @Override
    public Promise<V> await() throws InterruptedException {
        if (isDone()) {
            return this;
        }

        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                incWaiters();
                try {
                    wait();
                } finally {
                    decWaiters();
                }
            }
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public Promise<V> awaitUninterruptibly() {
        if (isDone()) {
            return this;
        }

        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Interrupted while waiting.
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (isDone()) {
            return true;
        }

        if (timeoutNanos <= 0) {
            return isDone();
        }

        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        long startTime = System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (isDone()) {
                    return true;
                }

                if (waitTime <= 0) {
                    return isDone();
                }

                checkDeadLock();
                incWaiters();
                try {
                    for (;;) {
                        try {
                            wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (isDone()) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return isDone();
                            }
                        }
                    }
                } finally {
                    decWaiters();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Do deadlock checks
     */
    protected void checkDeadLock() {
        EventExecutor e = executor();
        if (e != null && e.inEventLoop()) {
            throw new BlockingOperationException(toString());
        }
    }

    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        if (setSuccess0(result)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        if (setFailure0(cause)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Object result = this.result;
        if (isDone0(result) || result == UNCANCELLABLE) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            result = this.result;
            if (isDone0(result) || result == UNCANCELLABLE) {
                return false;
            }

            this.result = CANCELLATION_CAUSE_HOLDER;
            if (hasWaiters()) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean setUncancellable() {
        Object result = this.result;
        if (isDone0(result)) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            result = this.result;
            if (isDone0(result)) {
                return false;
            }

            this.result = UNCANCELLABLE;
        }
        return true;
    }

    private boolean setFailure0(Throwable cause) {
        if (isDone()) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (isDone()) {
                return false;
            }

            result = new CauseHolder(cause);
            if (hasWaiters()) {
                notifyAll();
            }
        }
        return true;
    }

    private boolean setSuccess0(V result) {
        if (isDone()) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (isDone()) {
                return false;
            }
            if (result == null) {
                this.result = SUCCESS;
            } else {
                this.result = result;
            }
            if (hasWaiters()) {
                notifyAll();
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getNow() {
        Object result = this.result;
        if (result instanceof CauseHolder || result == SUCCESS) {
            return null;
        }
        return (V) result;
    }

    private boolean hasWaiters() {
        return waiters > 0;
    }

    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        waiters ++;
    }

    private void decWaiters() {
        waiters --;
    }

    private void notifyListeners() {
        if (entry == null) {
            // nothing to notify so return here
            return;
        }
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            final Integer stackDepth = LISTENER_STACK_DEPTH.get();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyListeners0(this);
                } finally {
                    LISTENER_STACK_DEPTH.set(stackDepth);
                }
                return;
            }
        }

        try {
            // cache the task just in case for double notifications
            Runnable task = notifierTask;
            if (task == null) {
                notifierTask = task = new Runnable() {
                    @Override
                    public void run() {
                        notifyListeners0(DefaultPromise.this);
                    }
                };
            }
            executor.execute(task);
        } catch (Throwable t) {
            logger.error("Failed to notify listener(s). Event loop shut down?", t);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void notifyListeners0(DefaultPromise<?> promise) {
        Object first = promise.entry;
        if (first == null) {
            // nothing to notify so return here
            return;
        }
        if (first instanceof Entry) {
            Entry entry = (Entry) first;
            for (;;) {
                if (entry == null) {
                    break;
                }
                entry.notifyListenener(promise);
                entry = entry.next;
            }
        } else {
            GenericFutureListener listener = (GenericFutureListener) first;
            notifyListener0(promise, listener);

            // check if we can remove it directly as it was the only listener. If this fails something else
            // was added in the meantime. In this case we mark the listener as removed if it is still present
            if (!ENTRY_UPDATER.compareAndSet(promise, listener, null)) {
                Object f = promise.entry;
                if (f instanceof Entry) {
                    Entry entry = (Entry) f;
                    if (entry.curr == listener) {
                        // mark ourself as removed as it was run before
                        entry.remove();
                    }
                }
            }
        }
    }

    protected static void notifyListener(
            final EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> l) {
        if (eventExecutor.inEventLoop()) {
            final Integer stackDepth = LISTENER_STACK_DEPTH.get();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyListener0(future, l);
                } finally {
                    LISTENER_STACK_DEPTH.set(stackDepth);
                }
                return;
            }
        }

        try {
            eventExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    notifyListener0(future, l);
                }
            });
        } catch (Throwable t) {
            logger.error("Failed to notify a listener. Event loop shut down?", t);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    void notifyProgressiveListeners(final long progress, final long total) {
        if (progressiveSize == 0 || entry == null) {
            // nothing to notify so return
            return;
        }
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            notifyProgressiveListeners0(this, progress, total);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListeners0(DefaultPromise.this, progress, total);
                    }
                });
            } catch (Throwable t) {
                logger.error("Failed to notify listener(s). Event loop shut down?", t);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void notifyProgressiveListeners0(
            DefaultPromise<?> promise, long progress, long total) {
        if (promise.progressiveSize == 0 || promise.entry == null) {
            // nothing to notify so return
            return;
        }
        Object first = promise.entry;
        if (first instanceof Entry) {
            Entry entry = (Entry) first;
            for (;;) {
                if (entry == null) {
                    break;
                }
                entry.notifyProgressiveListener(promise, progress, total);
                entry = entry.next;
            }
        } else {
            GenericProgressiveFutureListener listener = (GenericProgressiveFutureListener) first;
            notifyProgressiveListener0((ProgressiveFuture) promise, listener, progress, total);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void notifyProgressiveListener0(
            ProgressiveFuture future, GenericProgressiveFutureListener listener, long progress, long total) {
        try {
            listener.operationProgressed(future, progress, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by "
                        + listener.getClass().getName() + ".operationProgressed()", t);
            }
        }
    }

    private static final class CauseHolder {
        final Throwable cause;
        private CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64);
        buf.append(StringUtil.simpleClassName(this));
        buf.append('@');
        buf.append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure(");
            buf.append(((CauseHolder) result).cause);
            buf.append(')');
        } else {
            buf.append("(incomplete)");
        }
        return buf;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final class Entry {
        // Dummy handler which is used as replacement for the previous used listener to allow GC to kick in
        @SuppressWarnings("rawtypes")
        private static final GenericFutureListener REMOVED_LISTENER = new GenericFutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
                // NOOP
            }
        };

        private volatile GenericFutureListener curr;
        @SuppressWarnings("unused")
        private volatile Entry next;
        private static final AtomicReferenceFieldUpdater<Entry, GenericFutureListener> CURR_UPDATER;
        private static final AtomicReferenceFieldUpdater<Entry, Entry> NEXT_UPDATER;

        static {
            AtomicReferenceFieldUpdater<Entry, GenericFutureListener> currUpdater =
                    PlatformDependent.newAtomicReferenceFieldUpdater(Entry.class, "curr");
            if (currUpdater == null) {
                currUpdater = AtomicReferenceFieldUpdater.newUpdater(Entry.class, GenericFutureListener.class, "curr");
            }
            CURR_UPDATER = currUpdater;
            AtomicReferenceFieldUpdater<Entry, Entry> nextUpdater =
                    PlatformDependent.newAtomicReferenceFieldUpdater(Entry.class, "next");
            if (nextUpdater == null) {
                nextUpdater = AtomicReferenceFieldUpdater.newUpdater(Entry.class, Entry.class, "next");
            }
            NEXT_UPDATER = nextUpdater;
        }

        // done is always only modified and read from one thread so no need for volatile
        private boolean done;

        Entry(GenericFutureListener curr) {
            this.curr = curr;
        }

        boolean remove() {
            GenericFutureListener listener = curr;
            return CURR_UPDATER.compareAndSet(this, listener, REMOVED_LISTENER);
        }

        boolean isRemoved() {
            return curr == REMOVED_LISTENER;
        }

        Entry next(Entry node) {
            if (NEXT_UPDATER.compareAndSet(this, null, node)) {
               return null;
            }
            return next;
        }

        void notifyListenener(Future<?> future) {
            if (!done && !isRemoved()) {
                done = true;
                GenericFutureListener listener = curr;
                curr = REMOVED_LISTENER;
                notifyListener0(future, listener);
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void notifyProgressiveListener(Future future, long progress, long total) {
            if (!done && curr instanceof GenericProgressiveFutureListener && !isRemoved()) {
                notifyProgressiveListener0(
                        (ProgressiveFuture) future, (GenericProgressiveFutureListener) curr, progress, total);
            }
        }
    }
}

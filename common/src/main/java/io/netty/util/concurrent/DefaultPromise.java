/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DefaultPromise<V> implements Promise<V>, Future<V> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromise.class);
    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise.class.getName() + ".rejectedExecution");
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");
    private static final Object SUCCESS = new Object();
    private static final Object UNCANCELLABLE = new Object();
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(
            StacklessCancellationException.newInstance(DefaultPromise.class, "cancel(...)"));
    private static final StackTraceElement[] CANCELLATION_STACK = CANCELLATION_CAUSE_HOLDER.cause.getStackTrace();
    static final Object NULL_CONTEXT = new Object();

    /**
     * There are several possibilities for what 'result' can point to:
     *
     * <ul>
     *     <li>{@code null} — no result computed, no waiters, not cancelled and not un-cancellable.</li>
     *     <li>{@link #UNCANCELLABLE} — operation has not yet completed, but can now no longer be cancelled.</li>
     *     <li>{@link Waiter} — operation has not completed, but threads are waiting for completion.
     *     <ul>
     *         <li>The {@link Waiter} nodes form a linked list of waiting threads.</li>
     *         <li>The list is terminated by either a {@code null} reference, or {@link #UNCANCELLABLE}.</li>
     *         <li>Each waiter node has an uncancellable field, which is true if the next node is either an
     *         uncancellable waiter, or {@link #UNCANCELLABLE}.</li>
     *     </ul>
     *     </li>
     *     <li>{@link #SUCCESS} — operation completed successfully with a {@code null} result. Terminal.</li>
     *     <li>{@link CauseHolder} — operation has completed in either failure or from being cancelled. Terminal.</li>
     * </ul>
     */
    private volatile Object result;
    private final EventExecutor executor;

    // It is fine to not make this volatile as even if we override the value in there it does not matter as
    // DefaultFutureCompletionStage has no state itself and is just a wrapper around this DefaultPromise instance.
    private DefaultFutureCompletionStage<V> stage;

    /**
     * One or more listeners. Can be a {@link FutureListener} or a {@link DefaultFutureListeners}.
     * If {@code null}, it means either 1) no listeners were added yet or 2) all listeners were notified.
     * <p>
     * Note that if a {@link FutureContextListener} is added, we immediately upgrade to a {@link DefaultFutureListeners}
     * as we otherwise wouldn't have room to store the associated context object.
     * <p>
     * Threading - synchronized(this). We must support adding listeners when there is no EventExecutor.
     */
    private Object listeners;

    /**
     * Creates a new unfulfilled promise.
     *
     * This constructor is only meant to be used by sub-classes.
     *
     * @param executor
     *        The {@link EventExecutor} which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     *        The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     *        depth exceeds a threshold.
     */
    protected DefaultPromise(EventExecutor executor) {
        this.executor = requireNonNull(executor, "executor");
        stage = new DefaultFutureCompletionStage<>(this);
    }

    /**
     * Creates a new promise that has already been completed successfully.
     *
     * @param executor
     *        The {@link EventExecutor} which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     *        The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     *        depth exceeds a threshold.
     * @param result The result of the successful promise.
     */
    static <V> Promise<V> newSuccessfulPromise(EventExecutor executor, V result) {
        return new DefaultPromise<>(executor, result);
    }

    /**
     * Creates a new promise that has already failed.
     *
     * @param executor
     *        The {@link EventExecutor} which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     *        The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     *        depth exceeds a threshold.
     * @param cause The {@link Throwable} that caused the failure of the returned promise.
     */
    static <V> Promise<V> newFailedPromise(EventExecutor executor, Throwable cause) {
        return new DefaultPromise<>(cause, executor);
    }

    private DefaultPromise(EventExecutor executor, Object result) {
        this.executor = requireNonNull(executor, "executor");
        this.result = result == null? SUCCESS : result;
        stage = new DefaultFutureCompletionStage<>(this);
    }

    private DefaultPromise(Throwable cause, EventExecutor executor) {
        this.executor = requireNonNull(executor, "executor");
        result = new CauseHolder(requireNonNull(cause, "cause"));
        stage = new DefaultFutureCompletionStage<>(this);
    }

    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        return setSuccess0(result);
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    @Override
    public Future<V> asFuture() {
        return this;
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        return result != null && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isFailed() {
        return result instanceof CauseHolder;
    }

    @Override
    public boolean isCancellable() {
        return result == null;
    }

    @Override
    public Throwable cause() {
        return cause0(result);
    }

    private Throwable cause0(Object result) {
        if (!isDone0(result)) {
            throw new IllegalStateException("Cannot call cause() on a future that has not completed.");
        }
        if (!(result instanceof CauseHolder)) {
            return null;
        }
        if (result == CANCELLATION_CAUSE_HOLDER) {
            CancellationException ce = new LeanCancellationException();
            if (RESULT_UPDATER.compareAndSet(this, CANCELLATION_CAUSE_HOLDER, new CauseHolder(ce))) {
                return ce;
            }
            result = this.result;
        }
        return ((CauseHolder) result).cause;
    }

    @Override
    public Future<V> addListener(FutureListener<? super V> listener) {
        requireNonNull(listener, "listener");

        addListener0(listener, null);
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public <C> Future<V> addListener(C context, FutureContextListener<? super C, ? super V> listener) {
        requireNonNull(listener, "listener");

        addListener0(listener, context == null ? NULL_CONTEXT : context);
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Future<V> await() throws InterruptedException {
        try {
            await0(true, true, 0);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    @Override
    public Future<V> awaitUninterruptibly() {
        try {
            await0(true, false, 0);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(false, true, unit.toNanos(timeout));
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(false, true, MILLISECONDS.toNanos(timeoutMillis));
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(false, false, unit.toNanos(timeout));
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(false, false, MILLISECONDS.toNanos(timeoutMillis));
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getNow() {
        Object result = this.result;
        if (!isDone0(result)) {
            throw new IllegalStateException("Cannot call getNow() on a future that has not completed.");
        }
        if (result instanceof CauseHolder || result == SUCCESS) {
            return null;
        }
        return (V) result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() throws InterruptedException, ExecutionException {
        Object result = this.result;
        if (!isDone0(result)) {
            await();
            result = this.result;
        }
        if (result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        Throwable cause = cause0(result);
        if (cause == null) {
            return (V) result;
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Object result = this.result;
        if (!isDone0(result)) {
            if (!await(timeout, unit)) {
                throw new TimeoutException();
            }
            result = this.result;
        }
        if (result == SUCCESS) {
            return null;
        }
        Throwable cause = cause0(result);
        if (cause == null) {
            return (V) result;
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    @Override
    public Future<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Future<V> syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ")
                    .append(((CauseHolder) result).cause)
                    .append(')');
        } else if (result != null && !Waiter.isWaiter(result)) {
            buf.append("(success: ")
                    .append(result)
                    .append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
    }

    /**
     * Get the executor used to notify listeners when this promise is complete.
     * <p>
     * It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     * The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     * depth exceeds a threshold.
     * @return The executor used to notify listeners when this promise is complete.
     */
    @Override
    public final EventExecutor executor() {
        return executor;
    }

    protected void checkDeadLock() {
        checkDeadLock(executor);
    }

    protected final void checkDeadLock(EventExecutor executor) {
        if (executor.inEventLoop()) {
            throw new BlockingOperationException(toString());
        }
    }

    private void notifyListeners() {
        safeExecute(executor(), this::notifyListenersNow);
    }

    @SuppressWarnings("unchecked")
    private void notifyListenersNow() {
        Object listeners;
        synchronized (this) {
            // Only proceed if there are listeners to notify.
            if (this.listeners == null) {
                return;
            }
            listeners = this.listeners;
            this.listeners = null;
        }
        for (;;) {
            if (listeners instanceof DefaultFutureListeners) {
                notifyListeners0((DefaultFutureListeners) listeners);
            } else {
                notifyListener0(this, (FutureListener<V>) listeners);
            }
            synchronized (this) {
                if (this.listeners == null) {
                    return;
                }
                listeners = this.listeners;
                this.listeners = null;
            }
        }
    }

    private void notifyListeners0(DefaultFutureListeners listeners) {
        listeners.notifyListeners(this, logger);
    }

    static <V> void notifyListener0(Future<V> future, FutureListener<? super V> l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    private synchronized void addListener0(Object listener, Object context) {
        if (listeners == null && context == null) {
            listeners = listener;
        } else if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).add(listener, context);
        } else {
            DefaultFutureListeners listeners = new DefaultFutureListeners();
            if (this.listeners != null) {
                listeners.add(this.listeners, null);
            }
            listeners.add(listener, context);
            this.listeners = listeners;
        }
    }

    private boolean setSuccess0(V result) {
        return setValue0(result == null ? SUCCESS : result);
    }

    private boolean setFailure0(Throwable cause) {
        return setValue0(new CauseHolder(requireNonNull(cause, "cause")));
    }

    @Override
    public boolean cancel() {
        Object existing;
        do {
            existing = RESULT_UPDATER.get(this);
            if (isDone0(existing)) {
                return false;
            }
            if (existing == UNCANCELLABLE || Waiter.isWaiter(existing) && ((Waiter) existing).uncancellable) {
                return false;
            }
        } while (!RESULT_UPDATER.compareAndSet(this, existing, CANCELLATION_CAUSE_HOLDER));
        unblockWaitersAndNotifyListeners(existing);
        return true;
    }

    @Override
    public boolean setUncancellable() {
        Waiter uncancellableWaiter = null;
        Object next;
        Object result;
        do {
            result = this.result;
            if (Waiter.isWaiter(result)) {
                if (uncancellableWaiter == null) {
                    uncancellableWaiter = new Waiter();
                    uncancellableWaiter.uncancellable = true;
                }
                uncancellableWaiter.setNext(result);
                next = uncancellableWaiter;
            } else {
                next = UNCANCELLABLE;
            }
        } while (!isDone0(result) && !RESULT_UPDATER.compareAndSet(this, result, next));
        result = this.result;
        return !isDone0(result) || !isCancelled0(result);
    }

    private boolean setValue0(Object objResult) {
        Object existing;
        do {
            existing = RESULT_UPDATER.get(this);
            if (existing != null && existing != UNCANCELLABLE && existing.getClass() != Waiter.class) {
                return false;
            }
        } while (!RESULT_UPDATER.compareAndSet(this, existing, objResult));
        unblockWaitersAndNotifyListeners(existing);
        return true;
    }

    private void unblockWaitersAndNotifyListeners(Object potentialWaiters) {
        if (Waiter.isWaiter(potentialWaiters)) {
            Waiter waiter = (Waiter) potentialWaiters;
            do {
                waiter = waiter.unblockAndGetNext();
            } while (waiter != null);
        }
        synchronized (this) {
            if (listeners != null) {
                notifyListeners();
            }
        }
    }

    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new CompletionException(cause);
    }

    private boolean await0(boolean blocking, boolean interruptable, long timeoutNanos) throws InterruptedException {
        if (isDone()) {
            return true;
        }

        if (timeoutNanos <= 0) {
            if (blocking) {
                timeoutNanos = 1;
            } else {
                return isDone();
            }
        }

        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        checkDeadLock();

        // Start counting time from here instead of the first line of this method,
        // to avoid/postpone performance cost of System.nanoTime().
        final long startTime = blocking? 0 : System.nanoTime();
        boolean interrupted = false;
        try {
            long waitTime = timeoutNanos;
            Waiter waiter = getOrInstallWaiter();
            Object result = this.result;
            while (!isDone0(result) && waitTime > 0) {
                assert waiter != null;
                if (blocking) {
                    waiter.await(this);
                } else {
                    waiter.awaitNanos(this, waitTime);
                }
                interrupted |= Thread.interrupted();
                if (interrupted && interruptable) {
                    waiter.abandon();
                    throw new InterruptedException();
                }
                // Check isDone() in advance, try to avoid calculating the elapsed time later.
                result = this.result;
                if (isDone0(result)) {
                    return true;
                }
                if (!blocking) {
                    // Calculate the elapsed time here instead of in the while condition,
                    // try to avoid performance cost of System.nanoTime() in the first loop of while.
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                }
            }

            boolean done = isDone0(result);
            if (!done && waiter != null) {
                waiter.abandon();
            }
            return done;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Waiter getOrInstallWaiter() {
        Object currentResult = result;
        Thread currentThread = Thread.currentThread();
        Waiter waiter = null;
        boolean currentIsWaiter = Waiter.isWaiter(currentResult);
        if (currentIsWaiter) {
            // Try to find an abandoned waiter to claim.
            waiter = (Waiter) currentResult;
            do {
                if (waiter.tryClaim(currentThread)) {
                    return waiter;
                }
                waiter = waiter.getNext();
            } while (waiter != null);
        }
        if (currentResult == null || currentResult == UNCANCELLABLE || currentIsWaiter) {
            // Found no abandoned waiter to claim. Install a new one.
            waiter = new Waiter();
            waiter.setOwner(currentThread);
            do {
                waiter.setNext(currentResult);
                if (RESULT_UPDATER.compareAndSet(this, currentResult, waiter)) {
                    break;
                }
                currentResult = result;
            } while (currentResult == null || currentResult == UNCANCELLABLE || Waiter.isWaiter(currentResult));
        }
        return waiter;
    }

    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    private static boolean isDone0(Object result) {
        return result != null && result != UNCANCELLABLE && result.getClass() != Waiter.class;
    }

    static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }

    @Override
    public FutureCompletionStage<V> asStage() {
        return getFutureStageAdaptor();
    }

    @Override
    public java.util.concurrent.Future<V> asJdkFuture() {
        return getFutureStageAdaptor();
    }

    private DefaultFutureCompletionStage<V> getFutureStageAdaptor() {
        DefaultFutureCompletionStage<V> stageAdapter = stage;
        if (stageAdapter == null) {
            stage = stageAdapter = new DefaultFutureCompletionStage<>(this);
        }
        return stageAdapter;
    }

    private static final class CauseHolder {
        final Throwable cause;
        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    private static final class LeanCancellationException extends CancellationException {
        private static final long serialVersionUID = 2794674970981187807L;

        // Suppress a warning since the method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {   // lgtm[java/non-sync-override]
            setStackTrace(CANCELLATION_STACK);
            return this;
        }

        @Override
        public String toString() {
            return CancellationException.class.getName();
        }
    }

    private static final class StacklessCancellationException extends CancellationException {

        private static final long serialVersionUID = -2974906711413716191L;

        // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
        // Classloader.
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        static StacklessCancellationException newInstance(Class<?> clazz, String method) {
            return ThrowableUtil.unknownStackTrace(new StacklessCancellationException(), clazz, method);
        }
    }

    private static final class Waiter {
        private static final AtomicReferenceFieldUpdater<Waiter, Thread> OWNER =
                AtomicReferenceFieldUpdater.newUpdater(Waiter.class, Thread.class, "owner");
        private volatile Thread owner;
        private Object next;
        private boolean uncancellable;

        void setOwner(Thread thread) {
            owner = thread;
        }

        boolean tryClaim(Thread candicate) {
            if (owner == null) {
                return OWNER.compareAndSet(this, null, candicate);
            }
            return false;
        }

        void abandon() {
            owner = null;
        }

        void setNext(Object nextObj) {
            uncancellable = nextObj == UNCANCELLABLE || isWaiter(nextObj) && ((Waiter) nextObj).uncancellable;
            next = nextObj;
        }

        Waiter unblockAndGetNext() {
            Thread th = owner;
            if (th != null) {
                LockSupport.unpark(th);
            }
            Object n = next;
            next = null; // Help GC by limiting old-gen nepotism.
            if (isWaiter(n)) {
                return (Waiter) n;
            }
            return null;
        }

        Waiter getNext() {
            Object n = next;
            if (isWaiter(n)) {
                return (Waiter) n;
            }
            return null;
        }

        void await(Object blocker) {
            LockSupport.park(blocker);
        }

        void awaitNanos(Object blocker, long timeoutNanoes) {
            LockSupport.parkNanos(blocker, timeoutNanoes);
        }

        static boolean isWaiter(Object obj) {
            return obj != null && obj.getClass() == Waiter.class;
        }
    }
}

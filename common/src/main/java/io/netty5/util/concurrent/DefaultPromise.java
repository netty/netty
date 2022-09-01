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
package io.netty5.util.concurrent;

import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.ThrowableUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class DefaultPromise<V> implements Promise<V>, Future<V>,
                                          FutureCompletionStage<V>, java.util.concurrent.Future<V> {
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

    private volatile Object result;
    private final EventExecutor executor;

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
     * Threading - synchronized(this). We are required to hold the monitor to use Java's underlying wait()/notifyAll().
     */
    private short waiters;

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
    }

    private DefaultPromise(Throwable cause, EventExecutor executor) {
        this.executor = requireNonNull(executor, "executor");
        result = new CauseHolder(requireNonNull(cause, "cause"));
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
    public boolean setUncancellable() {
        return RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE);
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
    public FutureCompletionStage<V> await() throws InterruptedException {
        if (isDone()) {
            return this;
        }

        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        checkDeadLock();

        synchronized (this) {
            while (!isDone()) {
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
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
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

    @Override
    public boolean cancel() {
        if (RESULT_UPDATER.compareAndSet(this, null, CANCELLATION_CAUSE_HOLDER)) {
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
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
    public FutureCompletionStage<V> sync() throws InterruptedException {
        await();
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
        } else if (result != null) {
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
        safeExecute(executor(), new NotifyListeners(this));
    }

    private static final class NotifyListeners implements Runnable {
        private final DefaultPromise<?> promise;

        private NotifyListeners(DefaultPromise<?> promise) {
            this.promise = promise;
        }

        @Override
        public void run() {
            promise.notifyListenersNow();
        }
    }

    @SuppressWarnings({ "unchecked", "MethodOnlyUsedFromInnerClass" })
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

    private boolean setValue0(Object objResult) {
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    /**
     * Check if there are any waiters and if so notify these.
     * @return {@code true} if there are any listeners attached to the promise, {@code false} otherwise.
     */
    private synchronized boolean checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
        return listeners != null;
    }

    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        ++waiters;
    }

    private void decWaiters() {
        --waiters;
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

        checkDeadLock();

        // Start counting time from here instead of the first line of this method,
        // to avoid/postpone performance cost of System.nanoTime().
        final long startTime = System.nanoTime();
        synchronized (this) {
            boolean interrupted = false;
            try {
                long waitTime = timeoutNanos;
                while (!isDone() && waitTime > 0) {
                    incWaiters();
                    try {
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            throw e;
                        } else {
                            interrupted = true;
                        }
                    } finally {
                        decWaiters();
                    }
                    // Check isDone() in advance, try to avoid calculating the elapsed time later.
                    if (isDone()) {
                        return true;
                    }
                    // Calculate the elapsed time here instead of in the while condition,
                    // try to avoid performance cost of System.nanoTime() in the first loop of while.
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                }
                return isDone();
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    private static boolean isDone0(Object result) {
        return result != null && result != UNCANCELLABLE;
    }

    private static final class CauseHolder {
        final Throwable cause;
        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
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
        return this;
    }

    // <editor-fold defaultstate="collapsed" desc="CompletionStage and JDK Future implementation.">
    private enum Marker {
        EMPTY,
        ERROR
    }

    // Just a marker
    private static final Executor SAME_AS_FUTURE = task -> {
        throw new UnsupportedOperationException("Marker executor. Should never be called!");
    };

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    @Override
    public Future<V> future() {
        return this;
    }

    @Override
    public <U> FutureCompletionStage<U> thenApply(Function<? super V, ? extends U> fn) {
        return thenApplyAsync(fn, SAME_AS_FUTURE);
    }

    @Override
    public <U> FutureCompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn) {
        return thenApplyAsync(fn, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<Void> thenAccept(Consumer<? super V> action) {
        return thenAcceptAsync(action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<Void> thenAcceptAsync(Consumer<? super V> action) {
        return thenAcceptAsync(action, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<Void> thenRun(Runnable action) {
        return thenRunAsync(action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, ForkJoinPool.commonPool());
    }

    @Override
    public  <U, V1> FutureCompletionStage<V1> thenCombine(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn) {
        return thenCombineAsync(other, fn, SAME_AS_FUTURE);
    }

    @Override
    public <U, V1> FutureCompletionStage<V1> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn) {
        return thenCombineAsync(other, fn, ForkJoinPool.commonPool());
    }

    @Override
    public  <U> FutureCompletionStage<Void> thenAcceptBoth(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action) {
        return thenAcceptBothAsync(other, action, SAME_AS_FUTURE);
    }

    @Override
    public <U> FutureCompletionStage<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action) {
        return thenAcceptBothAsync(other, action, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, ForkJoinPool.commonPool());
    }

    @Override
    public  <U> FutureCompletionStage<U> applyToEither(
            CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return applyToEitherAsync(other, fn, SAME_AS_FUTURE);
    }

    @Override
    public <U> FutureCompletionStage<U> applyToEitherAsync(
            CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return applyToEitherAsync(other, fn, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<Void> acceptEither(CompletionStage<? extends V> other, Consumer<? super V> action) {
        return acceptEitherAsync(other, action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<Void> acceptEitherAsync(
            CompletionStage<? extends V> other, Consumer<? super V> action) {
        return acceptEitherAsync(other, action, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, ForkJoinPool.commonPool());
    }

    @Override
    public <U> FutureCompletionStage<U> thenCompose(Function<? super V, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, SAME_AS_FUTURE);
    }

    @Override
    public <U> FutureCompletionStage<U> thenComposeAsync(Function<? super V, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, ForkJoinPool.commonPool());
    }

    @Override
    public FutureCompletionStage<V> whenComplete(BiConsumer<? super V, ? super Throwable> action) {
        return whenCompleteAsync(action, SAME_AS_FUTURE);
    }

    @Override
    public FutureCompletionStage<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action) {
        return whenCompleteAsync(action, ForkJoinPool.commonPool());
    }

    @Override
    public <U> FutureCompletionStage<U> handle(BiFunction<? super V, Throwable, ? extends U> fn) {
        return handleAsync(fn, SAME_AS_FUTURE);
    }

    @Override
    public <U> FutureCompletionStage<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn) {
        return handleAsync(fn, ForkJoinPool.commonPool());
    }

    @Override
    public <U> FutureCompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn, Executor executor) {
        requireNonNull(fn, "fn");
        requireNonNull(executor, "executor");

        Promise<U> promise = executor().newPromise();
        addListener(future -> {
            Throwable cause = future.cause();
            if (cause == null) {
                V value = future.getNow();
                if (executeDirectly(executor)) {
                    thenApplyAsync0(promise, value, fn);
                } else {
                    safeExecute(executor, () -> thenApplyAsync0(promise, value, fn), promise);
                }
            } else {
                promise.setFailure(cause);
            }
        });
        return promise.asFuture().asStage();
    }

    private static <U, V> void thenApplyAsync0(Promise<U> promise, V value, Function<? super V, ? extends U> fn) {
        final U result;
        try {
            result = fn.apply(value);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(result);
    }

    @Override
    public FutureCompletionStage<Void> thenAcceptAsync(Consumer<? super V> action, Executor executor) {
        requireNonNull(action, "action");
        requireNonNull(executor, "executor");

        Promise<Void> promise = executor().newPromise();
        addListener(future -> {
            Throwable cause = future.cause();
            if (cause == null) {
                V value = future.getNow();
                if (executeDirectly(executor)) {
                    thenAcceptAsync0(promise, value, action);
                } else {
                    safeExecute(executor, () -> thenAcceptAsync0(promise, value, action), promise);
                }
            } else {
                promise.setFailure(cause);
            }
        });
        return promise.asFuture().asStage();
    }

    private static <U, V> void thenAcceptAsync0(Promise<U> promise, V value, Consumer<? super V> action) {
        try {
            action.accept(value);
            promise.setSuccess(null);
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    @Override
    public FutureCompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return thenAcceptAsync(ignore -> action.run(), executor);
    }

    @Override
    public <U, V1> FutureCompletionStage<V1> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn, Executor executor) {
        requireNonNull(other, "other");
        requireNonNull(fn, "fn");
        requireNonNull(executor, "executor");

        Promise<V1> promise = executor().newPromise();
        AtomicReference<Object> reference = new AtomicReference<>(Marker.EMPTY);

        abstract class CombineBiConsumer<T1, T2, T> implements BiConsumer<T, Throwable> {
            @SuppressWarnings("unchecked")
            @Override
            public void accept(T v, Throwable error) {
                if (error == null) {
                    if (!reference.compareAndSet(Marker.EMPTY, v)) {
                        Object rawValue = reference.get();
                        if (rawValue == Marker.ERROR) {
                            return;
                        }
                        applyAndNotify0(promise, (T1) v, (T2) rawValue, fn);
                    }
                } else {
                    if (reference.getAndSet(Marker.ERROR) != Marker.ERROR) {
                        // Did not fail the promise before, do it now.
                        promise.setFailure(error);
                    }
                }
            }

            abstract void applyAndNotify0(
                    Promise<V1> promise, T1 value1, T2 value2, BiFunction<? super V, ? super U, ? extends V1> fn);
        }

        whenCompleteAsync(new CombineBiConsumer<V, U, V>() {
            @Override
            void applyAndNotify0(
                    Promise<V1> promise, V value1, U value2, BiFunction<? super V, ? super U, ? extends V1> fn) {
                applyAndNotify(promise, value1, value2, fn);
            }
        }, executor);
        other.whenCompleteAsync(new CombineBiConsumer<U, V, U>() {
            @Override
            void applyAndNotify0(
                    Promise<V1> promise, U value1, V value2, BiFunction<? super V, ? super U, ? extends V1> fn) {
                applyAndNotify(promise, value2, value1, fn);
            }
        }, otherExecutor(executor));
        return promise.asFuture().asStage();
    }

    private Executor otherExecutor(Executor executor) {
        return executor == SAME_AS_FUTURE ? executor() : executor;
    }

    @Override
    public <U> FutureCompletionStage<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action, Executor executor) {
        requireNonNull(action, "action");
        return thenCombineAsync(other, (value, value2) -> {
            action.accept(value, value2);
            return null;
        }, executor);
    }

    @Override
    public FutureCompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        requireNonNull(action, "action");
        return thenCombineAsync(other, (ignoreThisValue, ignoreOtherValue) -> {
            action.run();
            return null;
        }, executor);
    }

    @Override
    public <U> FutureCompletionStage<U> applyToEitherAsync(
            CompletionStage<? extends V> other, Function<? super V, U> fn, Executor executor) {
        requireNonNull(other, "other");
        requireNonNull(fn, "fn");

        Promise<U> promise = executor().newPromise();
        BiConsumer<V, Throwable> consumer = new AtomicBiConsumer<>(promise) {
            private static final long serialVersionUID = -8454630185124276599L;

            @Override
            protected U apply(V value) {
                return fn.apply(value);
            }
        };
        whenCompleteAsync(consumer, executor);
        other.whenCompleteAsync(consumer, otherExecutor(executor));
        return promise.asFuture().asStage();
    }

    @Override
    public FutureCompletionStage<Void> acceptEitherAsync(
            CompletionStage<? extends V> other, Consumer<? super V> action, Executor executor) {
        requireNonNull(other, "other");
        requireNonNull(action, "action");

        Promise<Void> promise = executor().newPromise();
        BiConsumer<V, Throwable> consumer = new AtomicBiConsumer<>(promise) {
            private static final long serialVersionUID = -8429618092318150682L;

            @Override
            protected Void apply(V value) {
                action.accept(value);
                return null;
            }
        };
        whenCompleteAsync(consumer, executor);
        other.whenCompleteAsync(consumer, otherExecutor(executor));
        return promise.asFuture().asStage();
    }

    @Override
    public FutureCompletionStage<Void> runAfterEitherAsync(
            CompletionStage<?> other, Runnable action, Executor executor) {
        requireNonNull(other, "other");
        requireNonNull(action, "action");

        Promise<Void> promise = executor().newPromise();
        BiConsumer<Object, Throwable> consumer = new AtomicBiConsumer<>(promise) {
            private static final long serialVersionUID = 5994110691767731494L;

            @Override
            protected Void apply(Object value) {
                action.run();
                return null;
            }
        };
        whenCompleteAsync(consumer, executor);
        other.whenCompleteAsync(consumer, otherExecutor(executor));
        return promise.asFuture().asStage();
    }

    @Override
    public <U> FutureCompletionStage<U> thenComposeAsync(
            Function<? super V, ? extends CompletionStage<U>> fn, Executor executor) {
        requireNonNull(fn, "fn");
        requireNonNull(executor, "executor");

        Promise<U> promise = executor().newPromise();
        addListener(f -> {
            Throwable cause = f.cause();
            if (cause == null) {
                V value = f.getNow();
                if (executeDirectly(executor)) {
                    thenComposeAsync0(promise, fn, value);
                } else {
                    safeExecute(executor, () -> thenComposeAsync0(promise, fn, value), promise);
                }
            } else {
                promise.setFailure(cause);
            }
        });
        return promise.asFuture().asStage();
    }

    private static <V, U> void thenComposeAsync0(
            Promise<U> promise, Function<? super V, ? extends CompletionStage<U>> fn, V value) {
        final CompletionStage<U> result;
        try {
            result = fn.apply(value);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        result.whenComplete((v, error) -> {
            if (error == null) {
                promise.setSuccess(v);
            } else {
                promise.setFailure(error);
            }
        });
    }

    @Override
    public FutureCompletionStage<V> exceptionally(Function<Throwable, ? extends V> fn) {
        requireNonNull(fn, "fn");

        Promise<V> promise = executor().newPromise();
        addListener(f -> {
            Throwable error = f.cause();
            if (error == null) {
                V value = f.getNow();
                promise.setSuccess(value);
            } else {
                final V result;
                try {
                    result = fn.apply(error);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                    return;
                }
                promise.setSuccess(result);
            }
        });
        return promise.asFuture().asStage();
    }

    @Override
    public FutureCompletionStage<V> whenCompleteAsync(
            BiConsumer<? super V, ? super Throwable> action, Executor executor) {
        requireNonNull(action, "action");
        requireNonNull(executor, "executor");

        Promise<V> promise = executor().newPromise();
        addListener(f -> {
            if (executeDirectly(executor)) {
                whenCompleteAsync0(promise, f, action);
            } else {
                safeExecute(executor, () -> whenCompleteAsync0(promise, f, action), promise);
            }
        });
        return promise.asFuture().asStage();
    }

    private static <V> void whenCompleteAsync0(
            Promise<V> promise, Future<? extends V> f, BiConsumer<? super V, ? super Throwable> action) {
        Throwable cause = f.cause();
        V value = cause == null ? f.getNow() : null;
        try {
            action.accept(value, cause);
        } catch (Throwable error) {
            promise.setFailure(error);
            return;
        }

        if (cause == null) {
            promise.setSuccess(value);
        } else {
            promise.setFailure(cause);
        }
    }

    @Override
    public <U> FutureCompletionStage<U> handleAsync(
            BiFunction<? super V, Throwable, ? extends U> fn, Executor executor) {
        requireNonNull(fn, "fn");
        requireNonNull(executor, "executor");

        Promise<U> promise = executor().newPromise();
        addListener(f -> {
            if (executeDirectly(executor)) {
                handleAsync0(promise, f, fn);
            } else {
                safeExecute(executor, () -> handleAsync0(promise, f, fn), promise);
            }
        });
        return promise.asFuture().asStage();
    }

    @SuppressWarnings("unchecked")
    private static <U, V> void handleAsync0(
            Promise<U> promise, Future<? super V> f, BiFunction<? super V, Throwable, ? extends U> fn) {
        Throwable cause = f.cause();
        applyAndNotify(promise, cause == null ? (V) f.getNow() : null, cause, fn);
    }

    private static <U, V, T> void applyAndNotify(
            Promise<U> promise, V value, T value2, BiFunction<? super V, ? super T, ? extends U> fn) {
        final U result;
        try {
            result = fn.apply(value, value2);
        } catch (Throwable error) {
            promise.setFailure(error);
            return;
        }
        promise.setSuccess(result);
    }

    private static boolean executeDirectly(Executor executor) {
        return executor == SAME_AS_FUTURE;
    }

    private static void safeExecute(Executor executor, Runnable task, Promise<?> promise) {
        try {
            executor.execute(task);
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    private abstract static class AtomicBiConsumer<V, U> extends AtomicReference<Object>
            implements BiConsumer<V, Throwable> {
        private static final long serialVersionUID = 880039612531973027L;

        private final Promise<U> promise;

        AtomicBiConsumer(Promise<U> promise) {
            super(Marker.EMPTY);
            this.promise = promise;
        }

        @Override
        public void accept(V v, Throwable error) {
            if (error == null) {
                if (compareAndSet(Marker.EMPTY, v)) {
                    final U value;
                    try {
                        value = apply(v);
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                        return;
                    }
                    promise.setSuccess(value);
                }
            } else if (compareAndSet(Marker.EMPTY, Marker.ERROR)) {
                promise.setFailure(error);
            }
        }

        protected abstract U apply(V value);
    }
    // </editor-fold>

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
}

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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * The result of an asynchronous operation.
 * <p>
 * An asynchronous operation is one that might be completed outside a given
 * thread of execution. The operation can either be performing computation,
 * or I/O, or both.
 * <p>
 * All I/O operations in Netty are asynchronous. It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call. Instead, you will be returned with
 * a {@link Future} instance which gives you the information about the
 * result or status of the I/O operation.
 * <p>
 * A {@link Future} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created. The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet. If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure. Please note that even failure and cancellation belong to the
 * completed state.
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = throws   |    |    +===========================+
 * |      getNow() = throws   |    |    | Completed by cancellation |
 * +--------------------------+    |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link FutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * <h3>Prefer {@link #addListener(FutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(FutureListener)}, or
 * {@link #addListener(Object, FutureContextListener)}, to {@link #await()}
 * wherever possible to get notified when an I/O operation is done and to
 * do any follow-up tasks.
 * <p>
 * The {@link #addListener(FutureListener)} method is non-blocking. It simply adds
 * the specified {@link FutureListener} to the {@link Future}, and the I/O thread
 * will notify the listeners when the I/O operation associated with the future is
 * done. The {@link FutureListener} and {@link FutureContextListener} callbacks
 * yield the best performance and resource utilization because it does not block at
 * all, but it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * <p>
 * By contrast, {@link #await()} is a blocking operation. Once called, the
 * caller thread blocks until the operation is done. It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification. Moreover, there's a chance of
 * dead-lock in a particular circumstance, which is described below.
 *
 * <h3>Do not call {@link #await()} inside a {@link io.netty.channel.ChannelHandler}</h3>
 * <p>
 * The event handler methods in {@link io.netty.channel.ChannelHandler} are usually
 * called by an I/O thread. If {@link #await()} is called by an event handler method,
 * which is called by the I/O thread, the I/O operation it is waiting for might never
 * complete because {@link #await()} can block the I/O operation it is waiting for,
 * which is a dead-lock.
 * <pre>
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void channelRead({@link io.netty.channel.ChannelHandlerContext} ctx, Object msg) {
 *     {@link Future} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link io.netty.channel.ChannelHandlerContext} ctx, Object msg) {
 *     {@link Future} future = ctx.channel().close();
 *     future.addListener(new {@link FutureListener}() {
 *         public void operationComplete({@link Future} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread. Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead-lock.
 *
 * <h3>Do not confuse I/O timeout and await timeout</h3>
 *
 * The timeout value you specify with {@link #await(long)},
 * {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 * {@link #awaitUninterruptibly(long, TimeUnit)} are not related with I/O
 * timeout at all.  If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * <pre>
 * // BAD - NEVER DO THIS
 * {@link io.netty.bootstrap.Bootstrap} b = ...;
 * {@link Future} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * {@link io.netty.bootstrap.Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link io.netty.channel.ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link Future} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {
    /**
     * Returns {@code true} if and only if the operation was completed successfully.
     */
    boolean isSuccess();

    /**
     * Returns {@code true} if and only if the operation was completed and failed.
     */
    boolean isFailed();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     */
    boolean isCancellable();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded.
     * @throws IllegalStateException if this {@code Future} has not completed yet.
     */
    Throwable cause();

    /**
     * Adds the specified listener to this future.
     * The specified listener is notified when this future is {@linkplain #isDone() done}.
     * If this future is already completed, the specified listener is notified immediately.
     *
     * @param listener The listener to be called when this future completes.
     *                 The listener will be passed this future as an argument.
     * @return this future object.
     */
    Future<V> addListener(FutureListener<? super V> listener);

    /**
     * Adds the specified listener to this future.
     * The specified listener is notified when this future is {@linkplain #isDone() done}.
     * If this future is already completed, the specified listener is notified immediately.
     *
     * @param context The context object that will be passed to the listener when this future completes.
     * @param listener The listener to be called when this future completes.
     *                 The listener will be passed the given context, and this future.
     * @return this future object.
     */
    <C> Future<V> addListener(C context, FutureContextListener<? super C, ? super V> listener);

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if the computation threw an exception.
     * @throws InterruptedException if the current thread was interrupted while waiting
     *
     */
    Future<V> sync() throws InterruptedException;

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if the computation threw an exception.
     */
    Future<V> syncUninterruptibly();

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    Future<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    Future<V> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * Return the result without blocking. If the future is not done yet this will throw {@link IllegalStateException}.
     *
     * @throws IllegalStateException if this {@code Future} has not completed yet.
     */
    V getNow();

    /**
     * {@inheritDoc}
     *
     * If the cancellation was successful it will fail the future with a {@link CancellationException}.
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Create a new future that will complete with the result of this future mapped through the given mapper function.
     * <p>
     * If this future fails, then the returned future will fail as well, with the same exception.
     * Cancellation of either future will cancel the other.
     * If the mapper function throws, the returned future will fail, but this future will be unaffected.
     *
     * @param mapper The function that will convert the result of this future into the result of the returned future.
     * @param <R> The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of this future.
     */
    default <R> Future<R> map(Function<V, R> mapper) {
        Promise<R> promise = executor().newPromise();
        addListener(promise, PromiseOperator.map(mapper));
        promise.addListener(this, PromiseNotifier::propagateCancel);
        return promise;
    }

    /**
     * Create a new future that will complete with the result of this future flat-mapped through the given mapper
     * function.
     * <p>
     * The "flat" in "flat-map" means the given mapper function produces a result that itself is a future-of-R,
     * yet this method also returns a future-of-R, rather than a future-of-future-of-R.
     * In other words, if the same mapper function was used with the {@link #map(Function)} method, you would get back
     * a {@code Future<Future<R>>}.
     * These nested futures are "flattened" into a {@code Future<R>} by this method.
     * Note that the future returned by this method is not the same instance as the one the mapper function returns.
     * The reason is that this method needs to return immediately, but the mapper function cannot be applied before this
     * future has completed.
     * <p>
     * If this future fails, then the returned future will fail as well, with the same exception.
     * Cancellation of either future will cancel the other.
     * If the mapper function throws, the returned future will fail, but this future will be unaffected.
     *
     * @param mapper The function that will convert the result of this future into the result of the returned future.
     * @param <R> The result type of the mapper function, and of the returned future.
     * @return A new future instance that will complete with the mapped result of this future.
     */
    default <R> Future<R> flatMap(Function<V, Future<R>> mapper) {
        Promise<R> promise = executor().newPromise();
        addListener(promise, PromiseOperator.flatMap(mapper));
        promise.addListener(this, PromiseNotifier::propagateCancel);
        return promise;
    }

    /**
     * Returns the {@link EventExecutor} that is tied to this {@link Future}.
     */
    EventExecutor executor();

    @Override
    default V get() throws InterruptedException, ExecutionException {
        await();

        Throwable cause = cause();
        if (cause == null) {
            return getNow();
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }

    @Override
    default V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (await(timeout, unit)) {
            Throwable cause = cause();
            if (cause == null) {
                return getNow();
            }
            if (cause instanceof CancellationException) {
                throw (CancellationException) cause;
            }
            throw new ExecutionException(cause);
        }
        throw new TimeoutException();
    }

    /**
     * Returns a {@link FutureCompletionStage} that reflects the state of this {@link Future} and so will receive
     * all updates as well.
     */
    default FutureCompletionStage<V> asStage() {
        return new DefaultFutureCompletionStage<>(this);
    }
}

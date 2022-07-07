/*
 * Copyright 2019 The Netty Project
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

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link CompletionStage} that provides the same threading semantics and guarantees as the underlying
 * {@link Future}, which means that all the callbacks will be executed by {@link #executor()}
 * if not specified otherwise (by calling the corresponding *Async methods).
 * <p>
 * This interface also extends {@link java.util.concurrent.Future}, to provide blocking methods for awaiting the result
 * of the future.
 * This is in contrast to the Netty {@link Future}, which is entirely non-blocking.
 * <p>
 * Please be aware that {@link FutureCompletionStage#toCompletableFuture()} is not supported and so will throw
 * an {@link UnsupportedOperationException} when invoked.
 *
 * @param <V> the value type.
 */
public interface FutureCompletionStage<V>
        extends CompletionStage<V>, java.util.concurrent.Future<V>, AsynchronousResult<V> {

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future failed.
     *
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if the computation threw an exception.
     * @throws InterruptedException  if the current thread was interrupted while waiting
     */
    FutureCompletionStage<V> sync() throws InterruptedException;

    /**
     * Waits for the future to complete, then calls the given result handler with the outcome.
     * <p>
     * If the future completes successfully, then the result handler is called with the result of the future -
     * which may be {@code null} - and a {@code null} exception.
     * <p>
     * If the future fails, then the result handler is called with a {@code null} result, and a non-{@code null}
     * exception.
     * <p>
     * Success or failure of the future can be determined on whether the exception is {@code null} or not.
     * <p>
     * The result handler may compute a new result, which will be the return value of the {@code join} call.
     *
     * @param resultHandler The function that will process the result of the completed future.
     * @return The result of the {@code resultHandler} computation.
     * @param <T> The return type of the {@code resultHandler}.
     * @throws InterruptedException if the thread is interrupted while waiting for the future to complete.
     */
    default <T> T join(BiFunction<V, Throwable, T> resultHandler) throws InterruptedException {
        Objects.requireNonNull(resultHandler, "resultHandler");
        await();
        var fut = future();
        if (fut.isSuccess()) {
            return resultHandler.apply(fut.getNow(), null);
        } else {
            return resultHandler.apply(null, fut.cause());
        }
    }

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    FutureCompletionStage<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed within the specified time limit.
     *
     * @return {@code true} if and only if the future was completed within the specified time limit
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Wait for the future to complete, and return the cause if it failed, or {@code null} if it succeeded.
     *
     * @return The exception that caused the future to fail, if any, or {@code null}.
     * @throws InterruptedException if the current thread was interrupted while waiting.
     */
    default Throwable getCause() throws InterruptedException {
        await();
        return cause();
    }

    /**
     * Returns the underlying {@link Future} of this {@link FutureCompletionStage}.
     */
    Future<V> future();

    @Override
    default boolean cancel() {
        return future().cancel();
    }

    @Override
    default boolean isSuccess() {
        return future().isSuccess();
    }

    @Override
    default boolean isFailed() {
        return future().isFailed();
    }

    @Override
    default boolean isCancellable() {
        return future().isCancellable();
    }

    @Override
    default V getNow() {
        return future().getNow();
    }

    @Override
    default Throwable cause() {
        return future().cause();
    }

    @Override
    default EventExecutor executor() {
        return future().executor();
    }

    /**
     * Not supported and so throws an {@link UnsupportedOperationException}.
     */
    @Override
    default CompletableFuture<V> toCompletableFuture() {
        throw new UnsupportedOperationException("Not supported by "
                + StringUtil.simpleClassName(FutureCompletionStage.class));
    }

    @Override
    <U> FutureCompletionStage<U> thenApply(Function<? super V, ? extends U> fn);

    @Override
    <U> FutureCompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn);

    @Override
    FutureCompletionStage<Void> thenAccept(Consumer<? super V> action);

    @Override
    FutureCompletionStage<Void> thenAcceptAsync(Consumer<? super V> action);

    @Override
    FutureCompletionStage<Void> thenRun(Runnable action);

    @Override
    FutureCompletionStage<Void> thenRunAsync(Runnable action);

    @Override
    <U, V1> FutureCompletionStage<V1> thenCombine(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn);

    @Override
    <U, V1> FutureCompletionStage<V1> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn);

    @Override
    <U> FutureCompletionStage<Void> thenAcceptBoth(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action);

    @Override
    <U> FutureCompletionStage<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action);

    @Override
    FutureCompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    @Override
    FutureCompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    @Override
    <U> FutureCompletionStage<U> applyToEither(CompletionStage<? extends V> other, Function<? super V, U> fn);

    @Override
    <U> FutureCompletionStage<U> applyToEitherAsync(CompletionStage<? extends V> other, Function<? super V, U> fn);

    @Override
    FutureCompletionStage<Void> acceptEither(CompletionStage<? extends V> other, Consumer<? super V> action);

    @Override
    FutureCompletionStage<Void> acceptEitherAsync(CompletionStage<? extends V> other, Consumer<? super V> action);

    @Override
    FutureCompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    @Override
    FutureCompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    @Override
    <U> FutureCompletionStage<U> thenCompose(Function<? super V, ? extends CompletionStage<U>> fn);

    @Override
    <U> FutureCompletionStage<U> thenComposeAsync(Function<? super V, ? extends CompletionStage<U>> fn);

    @Override
    FutureCompletionStage<V> whenComplete(BiConsumer<? super V, ? super Throwable> action);

    @Override
    FutureCompletionStage<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action);

    @Override
    <U> FutureCompletionStage<U> handle(BiFunction<? super V, Throwable, ? extends U> fn);

    @Override
    <U> FutureCompletionStage<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn);

    @Override
    <U> FutureCompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn, Executor executor);

    @Override
    FutureCompletionStage<Void> thenAcceptAsync(Consumer<? super V> action, Executor executor);

    @Override
    FutureCompletionStage<Void> thenRunAsync(Runnable action, Executor executor);

    @Override
    <U, V1> FutureCompletionStage<V1> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn, Executor executor);

    @Override
    <U> FutureCompletionStage<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action, Executor executor);

    @Override
    FutureCompletionStage<Void> runAfterBothAsync(
            CompletionStage<?> other, Runnable action, Executor executor);

    @Override
    <U> FutureCompletionStage<U> applyToEitherAsync(
            CompletionStage<? extends V> other, Function<? super V, U> fn, Executor executor);

    @Override
    FutureCompletionStage<Void> acceptEitherAsync(
            CompletionStage<? extends V> other, Consumer<? super V> action, Executor executor);

    @Override
    FutureCompletionStage<Void> runAfterEitherAsync(
            CompletionStage<?> other, Runnable action, Executor executor);

    @Override
    <U> FutureCompletionStage<U> thenComposeAsync(
            Function<? super V, ? extends CompletionStage<U>> fn, Executor executor);

    @Override
    FutureCompletionStage<V> exceptionally(Function<Throwable, ? extends V> fn);

    @Override
    FutureCompletionStage<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action, Executor executor);

    @Override
    <U> FutureCompletionStage<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn, Executor executor);

    /**
     * Returns a {@link FutureCompletionStage} for the given {@link CompletionStage}
     * that is pinned to the given {@link EventExecutor}.
     */
    static <U> FutureCompletionStage<U> toFutureCompletionStage(CompletionStage<U> stage, EventExecutor executor) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(executor, "executor");
        if (stage instanceof FutureCompletionStage && ((FutureCompletionStage<?>) stage).executor() == executor) {
            return (FutureCompletionStage<U>) stage;
        }

        // Try fast-path for CompletableFuture instances that are already complete to reduce object creation.
        if (stage instanceof CompletableFuture) {
            CompletableFuture<U> future = (CompletableFuture<U>) stage;
            if (future.isDone() && !future.isCompletedExceptionally()) {
                return executor.newSucceededFuture(future.getNow(null)).asStage();
            }
        }

        Promise<U> promise = executor.newPromise();
        stage.whenComplete((v, cause) -> {
            if (cause != null) {
                promise.setFailure(cause);
            } else {
                promise.setSuccess(v);
            }
        });
        return promise.asFuture().asStage();
    }
}

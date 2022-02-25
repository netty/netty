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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link CompletionStage} that provides the same threading semantics and guarantees as the underlying
 * {@link Future}, which means that all the callbacks will be executed by {@link #executor()}
 * if not specified otherwise (by calling the corresponding *Async methods).
 *
 * Please be aware that {@link FutureCompletionStage#toCompletableFuture()} is not supported and so will throw
 * a {@link UnsupportedOperationException} when invoked.
 *
 * @param <V> the value type.
 */
public interface FutureCompletionStage<V> extends CompletionStage<V> {

    /**
     * Returns the underlying {@link Future} of this {@link FutureCompletionStage}.
     */
    Future<V> future();

    /**
     * See {@link Future#executor()}.
     */
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

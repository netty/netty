/*
 * Copyright 2026 The Netty Project
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

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Handler that will be notified once an operation completes.
 * <p>
 * Generally speaking, it should only be expected to be able to notify a {@link CompletionHandler} once per instance.
 * What exactly happens if an already completed {@link CompletionHandler} will receive a completion again is
 * undefined and depends on the implementation itself.
 *
 * @param <V>   the type of the result.
 */
public interface CompletionHandler<V> {
    /**
     * Will be called once an operation completes successfully.
     *
     * @param result    the value of the result.
     */
    void success(@Nullable V result);

    /**
     * Will be called once an operation completes with an error.
     *
     * @param cause     the error.
     */
    void failure(Throwable cause);

    /**
     * Returns a composed {@code CompletionHandler} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation. If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     * <p>
     * Notifications should only be triggered on the returned {@link CompletionHandler} and not on
     * this {@link CompletionHandler} anymore once this method was used to create the {@link CompletionHandler}.
     *
     * @param after the operation to perform after this operation
     * @param executor the {@link EventExecutor} on which {@code after} is executed.
     * @return a composed {@code CompletionHandler} that performs in sequence this
     * operation followed by the {@code after} operation
     */
    default CompletionHandler<V> andThen(CompletionHandler<? super V> after, EventExecutor executor) {
        Objects.requireNonNull(after);
        Objects.requireNonNull(executor);
        return toPromise(executor).addHandler(after);
    }

    /**
     * Returns a {@link Promise} which will notify this {@link CompletionHandler} once completed.
     * <p>
     * Notifications should only be triggered on the returned {@link Promise} and not on this {@link CompletionHandler}
     * anymore once this method was used to create the {@link Promise}.
     *
     * @param executor  the {@link EventExecutor} that will be used to create the {@link Promise}.
     * @return          a promise.
     */
    default Promise<V> toPromise(EventExecutor executor) {
        return executor.<V>newPromise().addHandler(this);
    }

    /**
     * Returns a composed {@code CompletionHandler} that ensures that the execution of the execution is done in the
     * {@link EventExecutor} thread.
     * <p>
     * Notifications should only be triggered on the returned {@link CompletionHandler} and not on
     * this {@link CompletionHandler} anymore once this method was used to create the {@link CompletionHandler}.
     *
     * @param executor  the executor.
     * @return          handler.
     */
    default CompletionHandler<V> onExecutor(EventExecutor executor) {
        return CompletionHandlers.onExecutor(this, executor);
    }

    /**
     * Returns a {@link CompletionHandler} that just ignores the completion.
     *
     * @return      handler.
     * @param <V>   the type of the result.
     */
    @SuppressWarnings("unchecked")
    static <V> CompletionHandler<V> ignore() {
        return (CompletionHandler<V>) CompletionHandlers.IGNORE;
    }
}

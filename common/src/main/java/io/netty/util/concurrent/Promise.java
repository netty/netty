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

/**
 * Special {@link Future} which is writable.
 */
public interface Promise<V> {
    /**
     * Marks this promise as a success and notifies all listeners attached to the {@linkplain #asFuture() future}.
     * <p>
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * Marks this future as a success and notifies all listeners.
     *
     * @return {@code true} if and only if successfully marked this promise as a success.
     * Otherwise {@code false} because this promise is already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * Marks this promise as a failure and notifies all listeners attached to the {@linkplain #asFuture() future}.
     * <p>
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * Marks this promise as a failure and notifies all listeners.
     *
     * @return {@code true} if and only if successfully marked this promise as a failure.
     * Otherwise {@code false} because this promise is already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * Make this promise impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this promise as uncancellable, or it is already done
     * without being cancelled. Otherwise {@code false} if this promise has been cancelled already.
     */
    boolean setUncancellable();

    /**
     * Cancel this promise, unless it has already been completed.
     * <p>
     * A cancelled promise is considered to be {@linkplain #isFailed() failed}.
     * <p>
     * If the cancellation was successful it will fail associated futures with a {@link CancellationException}.
     * <p>
     * Cancellation will not cause any threads working on the promise to be {@linkplain Thread#interrupt()
     * interrupted}.
     *
     * @return {@code true} if the promise was cancelled by this call, otherwise {@code false}.
     */
    boolean cancel();

    /**
     * Returns {@code true} if and only if the operation was completed successfully.
     */
    boolean isSuccess();

    /**
     * Returns {@code true} if and only if the operation was completed and failed.
     */
    boolean isFailed();

    /**
     * Return {@code true} if this promise has been {@linkplain #cancel() cancelled}.
     *
     * @return {@code true} if this promise has been cancelled, otherwise {@code false}.
     */
    boolean isCancelled();

    /**
     * Return {@code true} if this promise has been completed either {@linkplain #setSuccess(Object)
     * successfully}, {@linkplain #setFailure(Throwable) unsuccessfully}, or through
     * {@linkplain #cancel() cancellation}.
     *
     * @return {@code true} if this promise has completed, otherwise {@code false}.
     */
    boolean isDone();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel()}.
     */
    boolean isCancellable();

    /**
     * Return the successful result of this promise, if any.
     * If the promise has not yet been completed, then this will throw {@link IllegalStateException}.
     * If the promise has been cancelled or failed with an exception, then this returns {@code null}.
     * Note that a promise can also be completed successfully with a {@code null} result.
     *
     * @return the result of this promise, if completed successfully.
     * @throws IllegalStateException if this {@code Future} has not completed yet.
     */
    V getNow();

    /**
     * Returns the cause of the failed operation if the operation has failed.
     *
     * @return The cause of the failure, if any. Otherwise {@code null} if succeeded.
     * @throws IllegalStateException if this {@code Promise} has not completed yet.
     */
    Throwable cause();

    /**
     * Return the {@link Future} instance is associated with this promsie.
     * This future will be completed upon completion of this promise.
     *
     * @return A future instance associated with this promise.
     */
    Future<V> asFuture();

    /**
     * Returns the {@link EventExecutor} that is tied to this {@link Promise}.
     */
    EventExecutor executor();
}

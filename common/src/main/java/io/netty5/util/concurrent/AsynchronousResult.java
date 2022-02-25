/*
 * Copyright 2021 The Netty Project
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

import java.util.concurrent.CancellationException;

/**
 * A result of an asynchronous operation.
 * <p>
 * This interface is used as the super-interface of {@link Promise} and {@link Future}, and these should generally be
 * used instead.
 */
interface AsynchronousResult<V> {
    /**
     * Cancel this asynchronous operation, unless it has already been completed
     * or is not {@linkplain #isCancellable() cancellable}.
     * <p>
     * A cancelled operation is considered to be {@linkplain #isDone() done} and {@linkplain #isFailed() failed}.
     * <p>
     * If the cancellation was successful, the result of this operation will be that it has failed with a
     * {@link CancellationException}.
     * <p>
     * Cancellation will not cause any threads working on the operation to be {@linkplain Thread#interrupt()
     * interrupted}.
     *
     * @return {@code true} if the operation was cancelled by this call, otherwise {@code false}.
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
     * Return {@code true} if this operation has been {@linkplain #cancel() cancelled}.
     *
     * @return {@code true} if this operation has been cancelled, otherwise {@code false}.
     */
    boolean isCancelled();

    /**
     * Return {@code true} if this operation has been completed either {@linkplain Promise#setSuccess(Object)
     * successfully}, {@linkplain Promise#setFailure(Throwable) unsuccessfully}, or through
     * {@linkplain #cancel() cancellation}.
     *
     * @return {@code true} if this operation has completed, otherwise {@code false}.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if the operation can be cancelled via {@link #cancel()}.
     * Note that this is inherently racy, as the operation could be made
     * {@linkplain Promise#setUncancellable() uncancellable} at any time.
     *
     * @return {@code true} if this operation can be cancelled.
     */
    boolean isCancellable();

    /**
     * Return the successful result of this asynchronous operation, if any.
     * If the operation has not yet been completed, then this will throw {@link IllegalStateException}.
     * If the operation has been cancelled or failed with an exception, then this returns {@code null}.
     * Note that asynchronous operations can also be completed successfully with a {@code null} result.
     *
     * @return the result of this operation, if completed successfully.
     * @throws IllegalStateException if this {@code Future} or {@link Promise} has not completed yet.
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
     * Returns the {@link EventExecutor} that is tied to this {@link Promise} or {@link Future}.
     */
    EventExecutor executor();
}

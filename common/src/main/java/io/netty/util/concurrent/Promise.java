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
     * Marks this future as a success and notifies all listeners.
     * <p>
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * Marks this future as a success and notifies all listeners.
     *
     * @return {@code true} if and only if successfully marked this future as a success. Otherwise {@code false} because
     * this future is already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * Marks this future as a failure and notifies all listeners.
     * <p>
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * Marks this future as a failure and notifies all listeners.
     *
     * @return {@code true} if and only if successfully marked this future as a failure. Otherwise {@code false} because
     * this future is already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * Make this future impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this future as uncancellable, or it is already done
     * without being cancelled. Otherwise {@code false} if this future has been cancelled already.
     */
    boolean setUncancellable();

    /**
     * Cancel the promise and mark the associated future as cancelled.
     * <p>
     * If the cancellation was successful it will fail the future with a {@link CancellationException}.
     *
     * @param mayInterruptIfRunning set to {@code true} if any thread that might be working on fulfilling the promise
     *                             is allowed to be interrupted. Otherwise, set to {@code false}.
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Get a {@link Future} representation of this promise.
     *
     * When this promise succeeds or fails, the future will be completed in a corresponding way.
     *
     * The returned future is bound to this specific promise instance, and repeated calls to this method will return
     * the same future instance.
     *
     * @return A {@link Future} representation of this promise.
     */
    Future<V> asFuture();

    /**
     * Returns {@code true} if this promise has already either failed, or been fulfilled successfully.
     *
     * @return {@code true} if this promise has been completed, otherwise {@code false}.
     */
    boolean isDone();

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
}

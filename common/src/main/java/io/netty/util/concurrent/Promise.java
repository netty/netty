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

/**
 * Special {@link Future} which is writable.
 */
public interface Promise<V> extends Future<V> {
    /**
     * Create a new unfulfilled promise.
     *
     * @param executor The {@link EventExecutor} the promise should be bound to. Completion callbacks will run on this
     *                 event executor. It is assumed this executor will protect against {@link StackOverflowError}
     *                 exceptions. The executor may be used to avoid {@link StackOverflowError} by executing a {@link
     *                 Runnable} if the stack depth exceeds a threshold.
     * @param <T>      The result type of the promise.
     * @return The new promise.
     */
    static <T> Promise<T> newPromise(EventExecutor executor) {
        return new DefaultPromise<>(executor);
    }

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

    @Override
    Promise<V> addListener(FutureListener<? super V> listener);

    @Override
    <C> Promise<V> addListener(C context, FutureContextListener<? super C, ? super V> listener);
}

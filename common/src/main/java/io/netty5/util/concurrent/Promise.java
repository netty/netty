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

/**
 * Special {@link Future} which is writable.
 */
public interface Promise<V> extends AsynchronousResult<V> {
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
     * Return the {@link Future} instance is associated with this promise.
     * This future will be completed upon completion of this promise.
     *
     * @return A future instance associated with this promise.
     */
    Future<V> asFuture();
}

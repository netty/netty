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

import org.jetbrains.annotations.Nullable;

/**
 * Special {@link Future} which is writable and so allows to set the result.
 */
public interface Promise<V> extends Future<V>, CompletionHandler<V> {

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    @Override
    Promise<V> addListener(FutureListener<? super V> listener);

    @Override
    Promise<V> addHandler(CompletionHandler<? super V> handler);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();

    @Override
    default void success(@Nullable V result) {
        setSuccess(result);
    }

    @Override
    default void failure(Throwable cause) {
        setFailure(cause);
    }

    @Override
    default CompletionHandler<V> andThen(CompletionHandler<? super V> after, EventExecutor executor) {
        if (executor() == executor) {
            // Just add the handler and return the same instance to reduce object allocations.
            addHandler(after);
            return this;
        }
        return CompletionHandler.super.andThen(after, executor);
    }

    @Override
    default Promise<V> toPromise(EventExecutor executor) {
        if (executor() == executor) {
            // Just return itself.
            return this;
        }
        return CompletionHandler.super.toPromise(executor);
    }

    @Override
    default CompletionHandler<V> onExecutor(EventExecutor executor) {
        if (executor() == executor) {
            return this;
        }
        return toPromise(executor);
    }
}

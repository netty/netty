/*
 * Copyright 2012 The Netty Project
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

import java.util.Collections;
import java.util.Iterator;

/**
 * The {@link EventExecutor} is a special {@link EventExecutorGroup} which comes
 * with some handy methods to see if a {@link Thread} is executed in a event loop.
 * Besides this, it also extends the {@link EventExecutorGroup} to allow for a generic
 * way to access methods.
 *
 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * Returns a reference to itself.
     */
    @Override
    default EventExecutor next() {
        return this;
    }

    @Override
    default Iterator<EventExecutor> iterator() {
        return Collections.singleton(this).iterator();
    }

    /**
     * Calls {@link #inEventLoop(Thread)} with {@link Thread#currentThread()} as argument
     */
    default boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    /**
     * Return {@code true} if the given {@link Thread} is executed in the event loop,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * Return a new {@link Promise}.
     */
    default <V> Promise<V> newPromise() {
        return new DefaultPromise<>(this);
    }

    /**
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    default <V> Future<V> newSucceededFuture(V result) {
        return DefaultPromise.newSuccessfulPromise(this, result).asFuture();
    }

    /**
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    default <V> Future<V> newFailedFuture(Throwable cause) {
        return DefaultPromise.<V>newFailedPromise(this, cause).asFuture();
    }
}

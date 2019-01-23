/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

/**
 * A combination of {@link java.util.concurrent.RunnableScheduledFuture}, {@link RunnableFuture} and
 * {@link ScheduledFuture}.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface RunnableScheduledFuture<V> extends
        java.util.concurrent.RunnableScheduledFuture<V>, RunnableFuture<V>, ScheduledFuture<V> {

    /**
     * Returns the deadline in nanos when the {@link #run()} method should be called again.
     */
    long deadlineNanos();

    /**
     * Returns the delay in nanos when the {@link #run()} method should be called again.
     */
    long delayNanos();

    /**
     * Returns the delay in nanos (taking the given {@code currentTimeNanos} into account) when the
     * {@link #run()} method should be called again.
     */
    long delayNanos(long currentTimeNanos);

    @Override
    RunnableScheduledFuture<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    RunnableScheduledFuture<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    RunnableScheduledFuture<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    RunnableScheduledFuture<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);
}


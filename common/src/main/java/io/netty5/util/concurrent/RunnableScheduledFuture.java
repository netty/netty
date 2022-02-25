/*
 * Copyright 2018 The Netty Project
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
 * A combination of {@link RunnableFuture} and {@link Comparable} (sorting by their next deadline),
 * with additional methods for scheduling, periodicity, and delay.
 */
public interface RunnableScheduledFuture<V> extends RunnableFuture<V>, Comparable<RunnableScheduledFuture<?>> {
    /**
     * Return {@code true} if the task is periodic, which means it may be executed multiple times, as opposed to a
     * delayed task or a normal task, that only execute once.
     *
     * @return {@code true} if this task is periodic, otherwise {@code false}.
     */
    boolean isPeriodic();

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
    RunnableScheduledFuture<V> addListener(FutureListener<? super V> listener);

    @Override
    <C> RunnableScheduledFuture<V> addListener(C context, FutureContextListener<? super C, ? super V> listener);
}


/*
 * Copyright 2022 The Netty Project
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

import java.util.concurrent.TimeUnit;

import io.netty.util.internal.UnstableApi;

/**
 * Limits the number of tasks that can be executed concurrently.
 */
@UnstableApi
public interface ConcurrencyLimit {
    /**
     * Requests to acquire a permit with the specified {@link EventExecutor} and {@code timeout}.
     * Once the requested permit is acquired, the specified {@code handler}'s
     * {@link ConcurrencyLimitHandler#permitAcquired(Runnable)} will be invoked.
     * The handler is responsible for calling the given {@link Runnable} to release the permit.
     * If failed to acquire a permit within the given timeout,
     * {@link ConcurrencyLimitHandler#permitAcquisitionTimedOut()} will be invoked.
     *
     * @param executor the {@link EventExecutor} that will be used for invoking {@link ConcurrencyLimitHandler}
     * @param handler the {@link ConcurrencyLimitHandler} that will be notified when the requested permit
     *                is acquired (or not)
     * @param timeout the maximum amount of time to wait until the permit is acquired
     * @param unit    the {@link TimeUnit} of {@code timeout}.
     */
    void acquire(EventExecutor executor, ConcurrencyLimitHandler handler, long timeout, TimeUnit unit);
}

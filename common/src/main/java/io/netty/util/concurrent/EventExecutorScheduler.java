/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.metrics.EventExecutorMetrics;
import io.netty.util.metrics.EventExecutorMetricsFactory;

import java.util.Set;

/**
 * Derive from this interface to create your own {@link EventExecutorScheduler} implementation.
 */
public interface EventExecutorScheduler<T1 extends EventExecutor, T2 extends EventExecutorMetrics>
        extends EventExecutorMetricsFactory<T2> {

    /**
     * Returns one of the {@linkplain EventExecutor}s managed by this {@link EventExecutorScheduler}.
     * This method must be implemented thread-safe. This method must <strong>NOT</strong> return {@code null}.
     */
    T1 next();

    /**
     * Add an {@link EventExecutor} and its corresponding {@link EventExecutorMetrics} object.
     */
    void addChild(T1 executor, T2 metrics);

    /**
     * Returns an unmodifiable set of all {@linkplain EventExecutor}s managed by this {@link EventExecutorScheduler}.
     * The {@link Set} will not contain any {@linkplain EventExecutor}s that are added via
     * {@link #addChild(EventExecutor, EventExecutorMetrics)} after it was retrieved.
     */
    Set<T1> children();
}

/*
 * Copyright 2023 The Netty Project
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
package io.netty5.util.internal;

import io.netty5.util.concurrent.EventExecutor;

import java.util.Queue;

/**
 * A task scheduler for {@link EventExecutor}s that want to support task scheduling.
 * @param <T> The object that is scheduled in this {@link ScheduledTaskQueue}.
 */
public interface ScheduledTaskQueue<T> extends Queue<T> {

    /**
     * Same as {@link #remove(Object)} but typed using generics.
     */
    boolean removeTyped(T task);

    /**
     * Removes all of the elements from this {@link ScheduledTaskQueue} without explicitly removing references
     * to them to allow them to be garbage collected. This should only be used when it is certain that
     * the nodes will not be re-inserted into this or any other {@link ScheduledTaskQueue} and it is known that
     * the {@link ScheduledTaskQueue} itself will be garbage collected after this call.
     */
    void clearIgnoringIndexes();
}

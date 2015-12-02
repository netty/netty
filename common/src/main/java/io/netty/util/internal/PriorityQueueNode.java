/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.internal;

/**
 * Provides methods for {@link PriorityQueue} to maintain internal state. These methods should generally not be used
 * outside the scope of {@link PriorityQueue}.
 * @param <T> The type which will be queued in {@link PriorityQueue}.
 */
public interface PriorityQueueNode<T> extends Comparable<T> {
    /**
     * This should be used to initialize the storage returned by {@link #priorityQueueIndex()}.
     */
    int INDEX_NOT_IN_QUEUE = -1;

    /**
     * Get the last value set by {@link #priorityQueueIndex(int)}.
     * <p>
     * Throwing exceptions from this method will result in undefined behavior.
     */
    int priorityQueueIndex();

    /**
     * Used by {@link PriorityQueue} to maintain state for an element in the queue.
     * <p>
     * Throwing exceptions from this method will result in undefined behavior.
     * @param i The index as used by {@link PriorityQueue}.
     */
    void priorityQueueIndex(int i);
}

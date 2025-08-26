/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.IntSupplier;
import io.netty.util.IntConsumer;

/**
 * A multi-producer (concurrent and thread-safe {@code offer} and {@code fill}),
 * single-consumer (single-threaded {@code poll} and {@code drain}) queue of primitive integers.
 */
public interface MpscIntQueue {

    /**
     * Offer the given value to the queue. This will throw an exception if the given value is the "empty" value.
     * @param value The value to add to the queue.
     * @return {@code true} if the value was added to the queue,
     * or {@code false} if the value could not be added because the queue is full.
     */
    boolean offer(int value);

    /**
     * Remove and return the next value from the queue, or return the "empty" value if the queue is empty.
     * @return The next value or the "empty" value.
     */
    int poll();

    /**
     * Remove up to the given limit of elements from the queue, and pass them to the consumer in order.
     * @param limit The maximum number of elements to dequeue.
     * @param consumer The consumer to pass the removed elements to.
     * @return The actual number of elements removed.
     */
    int drain(int limit, IntConsumer consumer);

    /**
     * Add up to the given limit of elements to this queue, from the given supplier.
     * @param limit The maximum number of elements to enqueue.
     * @param supplier The supplier to obtain the elements from.
     * @return The actual number of elements added.
     */
    int fill(int limit, IntSupplier supplier);

    /**
     * Query if the queue is empty or not.
     * <p>
     * This method is inherently racy and the result may be out of date by the time the method returns.
     * @return {@code true} if the queue was observed to be empty, otherwise {@code false.
     */
    boolean isEmpty();

    /**
     * Query the number of elements currently in the queue.
     * <p>
     * This method is inherently racy and the result may be out of date by the time the method returns.
     * @return An estimate of the number of elements observed in the queue.
     */
    int size();
}

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpscIntQueueTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 7, 8, 15, 16, 17})
    void mustFillWithSpecifiedEmptyEntry(int size) throws Exception {
        MpscIntQueue queue = MpscIntQueue.create(size, -1);
        int filled = queue.fill(size, () -> 42);
        assertEquals(size, filled);
        for (int i = 0; i < size; i++) {
            assertEquals(42, queue.poll());
        }
        assertEquals(-1, queue.poll());
        assertTrue(queue.isEmpty());
    }

    /**
     * {@code offer} publishes the producer index before the element, so a producer that has won
     * the race for its slot is already counted by {@code size()} while its store is still
     * pending -- a caller cannot tell it apart from a completed one. {@code resetAndFill} must
     * therefore wait for those stores, otherwise a stalled producer's value lands in the queue
     * after it has been reset for a new owner.
     */
    @Test
    void resetAndFillMustNotRaceProducersThatAlreadyClaimedASlot() throws Exception {
        final int capacity = 32;
        final int stride = 256;
        final int sentinel = 999_999;
        final int producers = 4;

        for (int round = 0; round < 5_000; round++) {
            final MpscIntQueue queue = MpscIntQueue.create(capacity, -1);
            final CountDownLatch go = new CountDownLatch(1);
            Thread[] threads = new Thread[producers];
            for (int i = 0; i < producers; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    queue.offer(sentinel);
                });
                threads[i].setDaemon(true);
                threads[i].start();
            }
            go.countDown();

            // Every producer has claimed a slot; none may start after this point.
            while (queue.size() != producers) {
                // spin
            }
            queue.resetAndFill(capacity, stride);

            for (Thread t : threads) {
                t.join();
            }

            for (int i = 0; i < capacity; i++) {
                assertEquals(i * stride, queue.poll(),
                        "round " + round + ": reset queue was corrupted by an in-flight producer");
            }
        }
    }
}

/*
 * Copyright 2021 The Netty Project
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
package io.netty.util.internal.telemetry;

import io.netty.util.internal.UnstableApi;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@UnstableApi
public class CycleBufferQueueEliminationStrategy implements QueueEliminationStrategy {
    private static final long EMPTY_VALUE = -1L;

    private final long[] latencies;
    private final AtomicInteger pointer;

    public CycleBufferQueueEliminationStrategy(int bufferSize) {
        latencies = new long[bufferSize];
        Arrays.fill(latencies, EMPTY_VALUE);
        pointer = new AtomicInteger();
    }

    public long[] getLatencies() {
        if (latencies[latencies.length - 1] == EMPTY_VALUE) {
            // First iteration of array insertion is not finished, return only actual latencies
            return Arrays.copyOf(latencies, pointer.get());
        } else {
            // We have second or later iteration, all array values contains actial latencies
            long[] latenciesCopy = new long[latencies.length];
            int ptr = pointer.get();
            // Return elements according their order. Old ones in the array beginning, new in the end
            System.arraycopy(latencies, ptr, latenciesCopy, 0, latencies.length - ptr);
            System.arraycopy(latencies, 0, latenciesCopy, latencies.length - ptr, ptr);
            return latenciesCopy;
        }
    }

    @Override
    public void handleElemination(EleminationHookQueueEntry<?> queueEntry) {
        final long dequeueTimeNanos = System.nanoTime();
        while (true) {
            int ptr = pointer.get();
            int nextVal = ptr >= latencies.length - 1 ? 0 : ptr + 1;
            if (pointer.compareAndSet(ptr, nextVal)) {
                latencies[ptr] = dequeueTimeNanos - queueEntry.getEnqueueTimeNanos();
                return;
            }
        }
    }
}

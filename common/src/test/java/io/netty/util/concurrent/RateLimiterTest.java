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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(RateLimiterTest.class);
    private static final EventExecutor executor = new DefaultEventExecutor();

    @AfterAll
    static void shutdownExecutor() {
        executor.shutdownGracefully();
    }

    private final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

    private final LimiterCallback handler = new LimiterCallback() {
        @Override
        public void permitAcquired(Runnable releaser) {
            events.add("acquired");
        }

        @Override
        public void permitAcquisitionTimedOut() {
            events.add("timed_out");
        }
    };

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @Test
    void acquisitionWithoutRefill() throws InterruptedException {
        final int bucketSize = 2;
        final RateLimiter limit = new RateLimiter(bucketSize, 1, 1, TimeUnit.DAYS);
        exhaustBucket(bucketSize, limit);
        assertNoEvents();

        // When the bucket is empty and a token is not added to it,
        // the acquisition request should time out.
        limit.acquire(executor, handler, 1, TimeUnit.SECONDS);
        assertEquals("timed_out", events.take());
        assertNoEvents();
    }

    @Test
    void acquisitionWithRefill() throws InterruptedException {
        final int bucketSize = 2;
        final long startTimeNanos = System.nanoTime();
        final RateLimiter limit = new RateLimiter(bucketSize, 1, 1, TimeUnit.SECONDS);
        exhaustBucket(bucketSize, limit);

        final int numAcquisitions = 5;
        final BlockingQueue<Long> timestamps = new LinkedBlockingQueue<Long>();
        final LimiterCallback handler = new LimiterCallback() {
            @Override
            public void permitAcquired(Runnable releaser) {
                final long currentTimeNanos = System.nanoTime();
                timestamps.add(currentTimeNanos != -1 ? currentTimeNanos : 0);
            }

            @Override
            public void permitAcquisitionTimedOut() {
                timestamps.add(-1L);
            }
        };

        for (int i = 0; i < numAcquisitions; i++) {
            limit.acquire(executor, handler, 1000, TimeUnit.SECONDS);
        }

        // Wait until all acquisitions are complete.
        while (timestamps.size() < numAcquisitions) {
            Thread.sleep(100);
        }

        // Ensure each acquisition occurred at even pace, without introducing extra delay.
        for (int i = 0; i < numAcquisitions; i++) {
            final long timestamp = timestamps.poll();
            final long expectedTimestamp = startTimeNanos + (i + 1) * TimeUnit.SECONDS.toNanos(1);

            logger.debug("timestamp: {}, expectedTimestamp: {}", timestamp, expectedTimestamp);
            assertTrue(timestamp >= expectedTimestamp);
            assertTrue(timestamp < expectedTimestamp + TimeUnit.MILLISECONDS.toNanos(300));
        }
    }

    @Test
    void refillOverMultipleIntervals() throws InterruptedException {
        final int bucketSize = 3;
        final RateLimiter limit = new RateLimiter(bucketSize, 1, 1, TimeUnit.SECONDS);
        exhaustBucket(bucketSize, limit);

        // Ensure the refill task refills the bucket for the next tick.
        while (limit.bucketLevel() == 0) {
            Thread.sleep(100);
        }
        assertEquals(1, limit.bucketLevel());

        // However, the refill task will not be rescheduled for the next next tick,
        // because the refill task is scheduled only once for the tick that received
        // an acquisition request.
        // Therefore, the bucket level should not change.
        Thread.sleep(2000);
        assertEquals(1, limit.bucketLevel());

        // Now attempt to acquire something.
        // The refill task should now be scheduled and thus bucket level should be updated.
        limit.acquire(executor, handler, 10, TimeUnit.SECONDS);
        while (limit.bucketLevel() < 3) {
            Thread.sleep(100);
        }
        assertEquals(3, limit.bucketLevel());
    }

    private void exhaustBucket(int bucketSize, RateLimiter limit) throws InterruptedException {
        // Should acquire permits immediately until the bucket is empty.
        for (int i = 0; i < bucketSize; i++) {
            limit.acquire(executor, handler, 0, TimeUnit.MILLISECONDS);
            final long startTimeNanos = System.nanoTime();
            assertEquals("acquired", events.take());
            final long endTimeNanos = System.nanoTime();
            assertTrue(endTimeNanos - startTimeNanos < TimeUnit.MILLISECONDS.toNanos(500));
        }
    }

    private void assertNoEvents() throws InterruptedException {
        assertNull(events.poll(1, TimeUnit.SECONDS));
    }
}

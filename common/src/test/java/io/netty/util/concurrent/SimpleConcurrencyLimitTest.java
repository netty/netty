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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleConcurrencyLimitTest {

    private static final int NOT_INTERESTED = -1;

    private static final EventExecutor executor = new DefaultEventExecutor();

    @AfterAll
    static void shutdownExecutor() {
        executor.shutdownGracefully();
    }

    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @Test
    void uncontendedSuccessfulAcquisition() throws Exception {
        final SimpleConcurrencyLimit limit = new SimpleConcurrencyLimit(1);
        acquireAndRelease(limit, 1, NOT_INTERESTED);
        assertEquals("acquired", takeEvent());
        assertNoEvents();
    }

    @Test
    void contendedSuccessfulAcquisition() throws Exception {
        final SimpleConcurrencyLimit limit = new SimpleConcurrencyLimit(1);
        final Runnable releaser = acquireButNotRelease(limit);

        acquireAndRelease(limit, 1, NOT_INTERESTED);

        // The handler should not be notified until the previous acquisition is released.
        assertNoEvents();

        // The handler should be notified as soon as the previous acquisition is released.
        releaser.run();
        assertEquals("acquired", takeEvent());
        assertNoEvents();
    }

    @Test
    void acquisitionTimeout() throws InterruptedException {
        final SimpleConcurrencyLimit limit = new SimpleConcurrencyLimit(1);
        acquireButNotRelease(limit);
        acquireAndRelease(limit, NOT_INTERESTED, 1);

        // The handler should be notified of timeout because we didn't release any previous acquisitions.
        assertEquals("timed_out", takeEvent());
        assertNoEvents();
    }

    @Test
    void testToString() {
        assertEquals("SimpleConcurrencyLimit{maxLimits=3, permitsInUse=0}",
                     new SimpleConcurrencyLimit(3).toString());
    }

    @Test
    void invalidMaxPermits() {
        try {
            new SimpleConcurrencyLimit(0);
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected
        }
    }

    @Nullable
    private String takeEvent() throws InterruptedException {
        return events.poll(10, TimeUnit.SECONDS);
    }

    private void acquireAndRelease(final SimpleConcurrencyLimit limit,
                                   final int expectedPermitsInUseOnAcquisition,
                                   final int expectedPermitsInUseOnTimeout) {
        limit.acquire(executor, new ConcurrencyLimitHandler() {
            @Override
            public void permitAcquired(Runnable releaser) {
                try {
                    assertTrue(executor.inEventLoop());
                    assertEquals(expectedPermitsInUseOnAcquisition, limit.permitsInUse());
                    releaser.run();
                    assertEquals(expectedPermitsInUseOnAcquisition - 1, limit.permitsInUse());
                    events.add("acquired");
                } catch (Throwable t) {
                    events.add(t.toString());
                }
            }

            @Override
            public void permitAcquisitionTimedOut() {
                try {
                    assertTrue(executor.inEventLoop());
                    assertEquals(expectedPermitsInUseOnTimeout, limit.permitsInUse());
                    events.add("timed_out");
                } catch (Throwable t) {
                    events.add(t.toString());
                }
            }
        }, 3, TimeUnit.SECONDS);
    }

    @Nullable
    private static Runnable acquireButNotRelease(SimpleConcurrencyLimit limit) throws InterruptedException {
        final BlockingQueue<Runnable> releaserQueue = new LinkedBlockingQueue<>();
        limit.acquire(executor, new ConcurrencyLimitHandler() {
            @Override
            public void permitAcquired(Runnable releaser) {
                releaserQueue.add(releaser);
            }

            @Override
            public void permitAcquisitionTimedOut() {
                // Not invoked
            }
        }, 0, TimeUnit.SECONDS);

        return releaserQueue.poll(3, TimeUnit.SECONDS);
    }

    private void assertNoEvents() throws InterruptedException {
        assertNull(events.poll(1, TimeUnit.SECONDS));
    }
}

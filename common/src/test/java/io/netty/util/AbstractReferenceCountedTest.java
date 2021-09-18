/*
 * Copyright 2016 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AbstractReferenceCountedTest {

    @Test
    public void testRetainOverflow() {
        final AbstractReferenceCounted referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain();
            }
        });
    }

    @Test
    public void testRetainOverflow2() {
        final AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertEquals(1, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain(Integer.MAX_VALUE);
            }
        });
    }

    @Test
    public void testReleaseOverflow() {
        final AbstractReferenceCounted referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(0);
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.release(Integer.MAX_VALUE);
            }
        });
    }

    @Test
    public void testReleaseErrorMessage() {
        AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        try {
            referenceCounted.release(1);
            fail("IllegalReferenceCountException didn't occur");
        } catch (IllegalReferenceCountException e) {
            assertEquals("refCnt: 0, decrement: 1", e.getMessage());
        }
    }

    @Test
    public void testRetainResurrect() {
        final AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain();
            }
        });
    }

    @Test
    public void testRetainResurrect2() {
        final AbstractReferenceCounted referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain(2);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testRetainFromMultipleThreadsThrowsReferenceCountException() throws Exception {
        int threads = 4;
        Queue<Future<?>> futures = new ArrayDeque<Future<?>>(threads);
        ExecutorService service = Executors.newFixedThreadPool(threads);
        final AtomicInteger refCountExceptions = new AtomicInteger();

        try {
            for (int i = 0; i < 10000; i++) {
                final AbstractReferenceCounted referenceCounted = newReferenceCounted();
                final CountDownLatch retainLatch = new CountDownLatch(1);
                assertTrue(referenceCounted.release());

                for (int a = 0; a < threads; a++) {
                    final int retainCnt = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
                    futures.add(service.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                retainLatch.await();
                                try {
                                    referenceCounted.retain(retainCnt);
                                } catch (IllegalReferenceCountException e) {
                                    refCountExceptions.incrementAndGet();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }));
                }
                retainLatch.countDown();

                for (;;) {
                    Future<?> f = futures.poll();
                    if (f == null) {
                        break;
                    }
                    f.get();
                }
                assertEquals(4, refCountExceptions.get());
                refCountExceptions.set(0);
            }
        } finally {
            service.shutdown();
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testReleaseFromMultipleThreadsThrowsReferenceCountException() throws Exception {
        int threads = 4;
        Queue<Future<?>> futures = new ArrayDeque<Future<?>>(threads);
        ExecutorService service = Executors.newFixedThreadPool(threads);
        final AtomicInteger refCountExceptions = new AtomicInteger();

        try {
            for (int i = 0; i < 10000; i++) {
                final AbstractReferenceCounted referenceCounted = newReferenceCounted();
                final CountDownLatch releaseLatch = new CountDownLatch(1);
                final AtomicInteger releasedCount = new AtomicInteger();

                for (int a = 0; a < threads; a++) {
                    final AtomicInteger releaseCnt = new AtomicInteger(0);

                    futures.add(service.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                releaseLatch.await();
                                try {
                                    if (referenceCounted.release(releaseCnt.incrementAndGet())) {
                                        releasedCount.incrementAndGet();
                                    }
                                } catch (IllegalReferenceCountException e) {
                                    refCountExceptions.incrementAndGet();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }));
                }
                releaseLatch.countDown();

                for (;;) {
                    Future<?> f = futures.poll();
                    if (f == null) {
                        break;
                    }
                    f.get();
                }
                assertEquals(3, refCountExceptions.get());
                assertEquals(1, releasedCount.get());

                refCountExceptions.set(0);
            }
        } finally {
            service.shutdown();
        }
    }

    private static AbstractReferenceCounted newReferenceCounted() {
        return new AbstractReferenceCounted() {
            @Override
            protected void deallocate() {
                // NOOP
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        };
    }
}

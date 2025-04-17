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
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMockTickerTest {

    @Test
    void newMockTickerShouldReturnDefaultMockTicker() {
        assertTrue(Ticker.newMockTicker() instanceof DefaultMockTicker);
    }

    @Test
    void defaultValues() {
        final MockTicker ticker = Ticker.newMockTicker();
        assertEquals(0, ticker.initialNanoTime());
        assertEquals(0, ticker.nanoTime());
    }

    @Test
    void advanceWithoutWaiters() {
        final MockTicker ticker = Ticker.newMockTicker();
        ticker.advance(42, TimeUnit.NANOSECONDS);
        assertEquals(0, ticker.initialNanoTime());
        assertEquals(42, ticker.nanoTime());

        ticker.advanceMillis(42);
        assertEquals(42_000_042, ticker.nanoTime());
    }

    @Test
    void advanceWithNegativeAmount() {
        final MockTicker ticker = Ticker.newMockTicker();
        assertThrows(IllegalArgumentException.class, () -> {
            ticker.advance(-1, TimeUnit.SECONDS);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ticker.advanceMillis(-1);
        });
    }

    @Timeout(60)
    @Test
    void advanceWithWaiters() throws Exception {
        final List<Thread> threads = new ArrayList<>();
        final DefaultMockTicker ticker = (DefaultMockTicker) Ticker.newMockTicker();
        final int numWaiters = 4;
        final List<FutureTask<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numWaiters; i++) {
            FutureTask<Void> task = new FutureTask<>(() -> {
                try {
                    ticker.sleep(1, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new CompletionException(e);
                }
                return null;
            });
            Thread thread = new Thread(task);
            threads.add(thread);
            futures.add(task);
            thread.start();
        }

        try {
            // Wait for all threads to be sleeping.
            for (Thread thread : threads) {
                ticker.awaitSleepingThread(thread);
            }

            // Time did not advance at all, and thus future will not complete.
            for (int i = 0; i < numWaiters; i++) {
                final int finalCnt = i;
                assertThrows(TimeoutException.class, () -> {
                    futures.get(finalCnt).get(1, TimeUnit.MILLISECONDS);
                });
            }

            // Advance just one nanosecond before completion.
            ticker.advance(999_999, TimeUnit.NANOSECONDS);

            // All threads should still be sleeping.
            for (Thread thread : threads) {
                ticker.awaitSleepingThread(thread);
            }

            // Still needs one more nanosecond for our futures.
            for (int i = 0; i < numWaiters; i++) {
                final int finalCnt = i;
                assertThrows(TimeoutException.class, () -> {
                    futures.get(finalCnt).get(1, TimeUnit.MILLISECONDS);
                });
            }

            // Reach at the 1 millisecond mark and ensure the future is complete.
            ticker.advance(1, TimeUnit.NANOSECONDS);
            for (int i = 0; i < numWaiters; i++) {
                futures.get(i).get();
            }
        } catch (InterruptedException ie) {
            for (Thread thread : threads) {
                String name = thread.getName();
                Thread.State state = thread.getState();
                StackTraceElement[] stackTrace = thread.getStackTrace();
                thread.interrupt();
                InterruptedException threadStackTrace = new InterruptedException(name + ": " + state);
                threadStackTrace.setStackTrace(stackTrace);
                ie.addSuppressed(threadStackTrace);
            }
            throw ie;
        }
    }

    @Test
    void sleepZero() throws InterruptedException {
        final MockTicker ticker = Ticker.newMockTicker();
        // All sleep calls with 0 delay should return immediately.
        ticker.sleep(0, TimeUnit.SECONDS);
        ticker.sleepMillis(0);
        assertEquals(0, ticker.nanoTime());
    }
}

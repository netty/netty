/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectCleanerTest {

    private Thread temporaryThread;
    private Object temporaryObject;

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testCleanup() throws Exception {
        final AtomicBoolean freeCalled = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);
        temporaryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException ignore) {
                    // just ignore
                }
            }
        });
        temporaryThread.start();
        ObjectCleaner.register(temporaryThread, new Runnable() {
            @Override
            public void run() {
                freeCalled.set(true);
            }
        });

        latch.countDown();
        temporaryThread.join();
        assertFalse(freeCalled.get());

        // Null out the temporary object to ensure it is enqueued for GC.
        temporaryThread = null;

        while (!freeCalled.get()) {
            System.gc();
            System.runFinalization();
            Thread.sleep(100);
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testCleanupContinuesDespiteThrowing() throws InterruptedException {
        final AtomicInteger freeCalledCount = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        temporaryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException ignore) {
                    // just ignore
                }
            }
        });
        temporaryThread.start();
        temporaryObject = new Object();
        ObjectCleaner.register(temporaryThread, new Runnable() {
            @Override
            public void run() {
                freeCalledCount.incrementAndGet();
                throw new RuntimeException("expected");
            }
        });
        ObjectCleaner.register(temporaryObject, new Runnable() {
            @Override
            public void run() {
                freeCalledCount.incrementAndGet();
                throw new RuntimeException("expected");
            }
        });

        latch.countDown();
        temporaryThread.join();
        assertEquals(0, freeCalledCount.get());

        // Null out the temporary object to ensure it is enqueued for GC.
        temporaryThread = null;
        temporaryObject = null;

        while (freeCalledCount.get() != 2) {
            System.gc();
            System.runFinalization();
            Thread.sleep(100);
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testCleanerThreadIsDaemon() throws Exception {
        temporaryObject = new Object();
        ObjectCleaner.register(temporaryObject, new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        });

        Thread cleanerThread = null;

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(ObjectCleaner.CLEANER_THREAD_NAME)) {
                cleanerThread = thread;
                break;
            }
        }
        assertNotNull(cleanerThread);
        assertTrue(cleanerThread.isDaemon());
    }
}

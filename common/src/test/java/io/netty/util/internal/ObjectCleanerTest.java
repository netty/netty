/*
 * Copyright 2017 The Netty Project
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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ObjectCleanerTest {

    private Thread temporaryThread;
    private Object temporaryObject;

    @Test(timeout = 5000)
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
        Assert.assertFalse(freeCalled.get());

        // Null out the temporary object to ensure it is enqueued for GC.
        temporaryThread = null;

        while (!freeCalled.get()) {
            System.gc();
            System.runFinalization();
            Thread.sleep(100);
        }
    }

    @Test(timeout = 5000)
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

    @Test(timeout = 5000)
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
